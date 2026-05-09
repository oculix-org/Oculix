#!/usr/bin/env python3
"""
translate-bundles.py — Auto-translate IDE_en_US.properties → IDE_<locale>.properties
via a self-hosted LibreTranslate instance.

Why this exists
---------------
OculiX ships a Java IDE with ~22 locales (IDE_en_US, IDE_fr, IDE_de, IDE_zh_CN, ...).
Historically only a subset of keys was translated by RaiMan + community over a
decade (menuFile, menuEdit, ...). The 3.0.x rebrand introduced ~30 new UI
strings (Welcome tab, Sidebar, status bar) that were initially hardcoded in
English. Doing 30 new keys × 21 target locales = 630 entries by hand is the
kind of friction that kills i18n maintenance.

This script bridges the gap:
  - reads the source bundle IDE_en_US.properties
  - for each missing key in each target IDE_<locale>.properties, POSTs the
    English value to a LibreTranslate /translate endpoint
  - writes the translated value back into the locale bundle
  - preserves existing translations (won't overwrite a key that already has a
    non-empty value in the target locale — RaiMan's original FR/DE/etc. work
    stays intact)
  - escapes Java .properties Unicode (\\uXXXX) so the resulting files are
    portable across JDK locales without BOM headaches

The IDE itself shows a one-time disclaimer popup on auto-translated locales
(see SikuliIDEI18N + i18nDisclaimer* keys) so users know to report
inaccuracies. Tamil (ta) is excluded because LibreTranslate's Argos backend
does not currently ship a model for it; RaiMan's hand translation in
IDE_ta_IN.properties is preserved as-is.

Privacy
-------
LibreTranslate runs on localhost (default http://localhost:5000). No string
ever leaves the developer's machine. Aligns with the OculiX philosophy of
local-first, sovereign tooling.

Usage
-----
1. Spin up LibreTranslate locally:
     docker run -d --name libretranslate -p 5000:5000 \\
       -e LT_LOAD_ONLY=ar,bg,ca,da,de,en,es,fr,he,it,ja,ko,nl,pl,pt,ru,sv,tr,uk,zh,zt \\
       libretranslate/libretranslate:latest

2. Wait until ready:
     curl -s http://localhost:5000/languages

3. Run this script from the repo root:
     python scripts/translate-bundles.py
   or with overrides:
     python scripts/translate-bundles.py \\
       --bundle-dir IDE/src/main/resources/i18n \\
       --endpoint http://localhost:5000 \\
       --only de,es,zh_CN

4. Review the diff and commit. Re-run any time keys are added to
   IDE_en_US.properties — only missing keys are filled in target bundles,
   so existing translations are never trampled.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path

# ── Locale → LibreTranslate language code mapping ──────────────────────────
# Java/SikuliX bundle suffix → ISO-639 code expected by LibreTranslate.
# Notes:
#   - en_US is the source, never a target.
#   - pt_BR → 'pt' (LibreTranslate's Portuguese model covers Brazilian usage
#     well enough for UI strings; native pt-BR speakers can refine).
#   - zh_CN → 'zh' (simplified), zh_TW → 'zt' (traditional).
#   - ta_IN → skipped (no Argos model available at time of writing). The
#     RaiMan-original IDE_ta_IN.properties is kept untouched.
LOCALE_MAP: dict[str, str] = {
    "ar":    "ar",
    "bg":    "bg",
    "ca":    "ca",
    "da":    "da",
    "de":    "de",
    "es":    "es",
    "fr":    "fr",     # already hand-translated; opt-in via --include-fr if desired
    "he":    "he",
    "it":    "it",
    "ja":    "ja",
    "ko":    "ko",
    "nl":    "nl",
    "pl":    "pl",
    "pt_BR": "pt",
    "ru":    "ru",
    "sv":    "sv",
    "tr":    "tr",
    "uk":    "uk",
    "zh_CN": "zh",
    "zh_TW": "zt",
    # "ta_IN": <not supported by LibreTranslate yet>
}

DEFAULT_ENDPOINT = "http://localhost:5000"
SOURCE_LOCALE = "en_US"
SOURCE_LANG_CODE = "en"


# ── .properties parser/writer (preserves comments + key order) ─────────────
# We don't use java.util.Properties or python-jproperties because we want to
# (a) keep header comments, (b) keep blank-line groupings, (c) write values
# with java-style \\uXXXX escapes so the file is locale-independent on disk.

@dataclass
class Entry:
    """One line in a .properties file. Either a key=value pair or pure prose
    (comment, blank line) we want to preserve verbatim on rewrite."""
    raw: str            # original line (without trailing newline)
    key: str | None     # set when this line is a `key=value` pair
    value: str | None   # the unescaped value (Java-decoded)


_KV_RE = re.compile(r"^(?P<key>[^#!=:\s]+)\s*=\s*(?P<val>.*)$")


def _java_decode(s: str) -> str:
    """Decode the \\uXXXX sequences and the standard \\n/\\t/\\\\ escapes used
    in Java .properties files."""
    # Decode \\uXXXX
    s = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), s)
    # Decode common escapes (order matters — \\\\ first)
    s = s.replace("\\\\", "\x00")  # placeholder for literal backslash
    s = s.replace("\\n", "\n").replace("\\t", "\t").replace("\\r", "\r")
    s = s.replace("\\:", ":").replace("\\=", "=").replace("\\ ", " ")
    s = s.replace("\x00", "\\")
    return s


def _java_encode(s: str) -> str:
    """Encode a string back to .properties form: ASCII-only + \\uXXXX for
    non-ASCII. Preserves MessageFormat placeholders ({0}, {1,number,integer})
    and HTML in values (used by the Welcome hero <br>)."""
    out = []
    for ch in s:
        cp = ord(ch)
        if ch == "\n":
            out.append("\\n")
        elif ch == "\r":
            out.append("\\r")
        elif ch == "\t":
            out.append("\\t")
        elif ch == "\\":
            out.append("\\\\")
        elif cp < 0x20 or cp > 0x7E:
            out.append(f"\\u{cp:04X}")
        else:
            out.append(ch)
    return "".join(out)


def parse_properties(path: Path) -> list[Entry]:
    """Read a .properties file and return a list of Entry objects, preserving
    comments / blank lines as raw entries with key=None."""
    entries: list[Entry] = []
    if not path.exists():
        return entries
    with path.open("r", encoding="utf-8") as f:
        for raw_line in f:
            line = raw_line.rstrip("\n").rstrip("\r")
            stripped = line.lstrip()
            # Comments, blank lines, or section banners → preserve as-is.
            if not stripped or stripped.startswith("#") or stripped.startswith("!"):
                entries.append(Entry(raw=line, key=None, value=None))
                continue
            m = _KV_RE.match(line)
            if not m:
                entries.append(Entry(raw=line, key=None, value=None))
                continue
            key = m.group("key").strip()
            val = _java_decode(m.group("val"))
            entries.append(Entry(raw=line, key=key, value=val))
    return entries


def index(entries: list[Entry]) -> dict[str, str]:
    """Flatten parsed entries to a dict[key] → value (last wins on dup, like
    java.util.Properties). Skips comment/blank entries."""
    return {e.key: e.value for e in entries if e.key is not None and e.value is not None}


def write_properties(path: Path, entries: list[Entry]) -> None:
    """Write entries to disk. Pairs are re-encoded; comment/blank lines are
    written verbatim (no transformation)."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        for e in entries:
            if e.key is None:
                f.write(e.raw + "\n")
            else:
                f.write(f"{e.key}={_java_encode(e.value)}\n")


# ── LibreTranslate client ──────────────────────────────────────────────────

class LibreTranslate:
    """Thin urllib-based client. No external deps so the script runs on a
    bare Python 3.10+ install."""

    def __init__(self, endpoint: str):
        self.endpoint = endpoint.rstrip("/")
        self._supported: set[str] | None = None

    def _post(self, path: str, payload: dict, timeout: float = 30.0) -> dict:
        body = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            f"{self.endpoint}{path}",
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))

    def _get(self, path: str, timeout: float = 10.0) -> list:
        with urllib.request.urlopen(f"{self.endpoint}{path}", timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))

    def supported(self) -> set[str]:
        if self._supported is None:
            try:
                langs = self._get("/languages")
                self._supported = {lang["code"] for lang in langs}
            except (urllib.error.URLError, json.JSONDecodeError) as e:
                print(f"ERROR: cannot reach LibreTranslate at {self.endpoint}: {e}",
                      file=sys.stderr)
                sys.exit(2)
        return self._supported

    def translate(self, text: str, target: str, source: str = SOURCE_LANG_CODE) -> str:
        """Translate text. Returns the translated string. Re-raises on
        non-recoverable errors so the caller can decide whether to skip the
        whole locale."""
        # Empty / placeholder-only strings → return as-is. LibreTranslate
        # tends to return weird artefacts on values like "{0}" alone.
        if not text.strip() or _is_placeholder_only(text):
            return text
        try:
            resp = self._post("/translate", {
                "q": text,
                "source": source,
                "target": target,
                "format": "text",
            })
            return resp.get("translatedText", text)
        except urllib.error.HTTPError as e:
            print(f"  HTTP {e.code} translating to {target}: {text[:60]!r}",
                  file=sys.stderr)
            return text
        except urllib.error.URLError as e:
            print(f"  Network error to {target}: {e}", file=sys.stderr)
            return text


_PLACEHOLDER_ONLY = re.compile(r"^\s*(\{[^{}]*\}\s*)+$")


def _is_placeholder_only(text: str) -> bool:
    """True if the string is only MessageFormat placeholders like '{0}' or
    '{0,number,integer} {1,date}'. Translating these is a waste of an API
    call and sometimes corrupts the placeholders."""
    return bool(_PLACEHOLDER_ONLY.match(text))


# Strings we never want LibreTranslate to touch — brand names, tech acronyms,
# keyboard shortcut tokens. Anything in this set gets stitched out via
# sentinels before the API call and stitched back verbatim afterwards.
# Order doesn't matter here, the protect step sorts longest-first to avoid
# partial matches (e.g. "OculiX" must be matched before "Oc").
NO_TRANSLATE_TERMS: set[str] = {
    # OculiX-family brands
    "OculiX", "SikuliX", "SikuliX1", "Apertix", "Legerix",
    # Embedded engines / libraries
    "OpenCV", "PaddleOCR", "Tesseract", "Robot Framework", "Jython",
    # Tech standards / runtimes
    "Java", "Python", "MIT", "VNC", "MCP", "AS/400", "OS",
    # Acronyms users recognize in any language
    "API", "IDE", "OCR", "CLI", "GUI", "JSON", "XML", "HTML", "CSS",
    "ASCII", "URL", "RGB", "PNG", "JPG", "DPI",
    # Keyboard shortcut tokens (translating "Ctrl" is a recipe for chaos)
    "Ctrl", "Shift", "Alt", "Cmd",
}


# ── HTML/MessageFormat-aware translation wrapper ───────────────────────────

# We protect three classes of substrings before sending text to LibreTranslate:
#   1) MessageFormat placeholders ({0}, {1,number,integer}, ...)
#   2) HTML tags (<br>, <i>, <b>, ...)
#   3) Brand names / tech acronyms from NO_TRANSLATE_TERMS
#
# Each protected substring is replaced by a self-closing HTML-like sentinel
# <x0/>, <x1/>, ... — modern translation models (Argos included) treat these
# as opaque tokens and tend to preserve them. We also accept variants like
# <x0> or <x0 /> on the way back, since some models reformat.
#
# An earlier version used "§§N§§" as the sentinel, which Argos happily
# fragmented into "§§ 0 § § §" — destroying the restore step. The HTML-
# style sentinel is more robust because translation models are trained on
# parallel HTML corpora and learn to leave tags alone.

_PROTECT_PLACEHOLDER_RE = re.compile(r"\{[^{}]*\}")
_PROTECT_HTML_RE = re.compile(r"<[^<>]+>")


def _protect(text: str) -> tuple[str, list[str]]:
    sentinels: list[str] = []

    def _stash(value: str) -> str:
        sentinels.append(value)
        return f"<x{len(sentinels) - 1}/>"

    # 1) MessageFormat placeholders first (some contain {} that look like HTML
    #    to the next regex if we did HTML first).
    text = _PROTECT_PLACEHOLDER_RE.sub(lambda m: _stash(m.group(0)), text)
    # 2) HTML tags
    text = _PROTECT_HTML_RE.sub(lambda m: _stash(m.group(0)), text)
    # 3) Brand names — longest first so "Robot Framework" is matched before
    #    "Robot" alone, and "SikuliX1" before "SikuliX".
    for term in sorted(NO_TRANSLATE_TERMS, key=len, reverse=True):
        # \b only works with ASCII word boundaries here — fine for our terms.
        pattern = re.compile(r"\b" + re.escape(term) + r"\b")
        text = pattern.sub(lambda m: _stash(m.group(0)), text)

    return text, sentinels


# Match the canonical sentinel <xN/> AND the looser <xN> / <xN /> variants
# that Argos sometimes emits after rewriting whitespace inside tags.
_RESTORE_RE = re.compile(r"<x(\d+)\s*/?>")


def _restore(text: str, sentinels: list[str]) -> str:
    def _resolve(m: re.Match) -> str:
        idx = int(m.group(1))
        return sentinels[idx] if 0 <= idx < len(sentinels) else m.group(0)

    return _RESTORE_RE.sub(_resolve, text)


# Strings like "SCRIPT" / "TOOLS" / "DARK" / "STATUS" — single short ALL-CAPS
# words. LibreTranslate often returns them unchanged or, worse, translates
# them inconsistently across calls (DARK stays "DARK" but LIGHT becomes
# "الليل" = "the night"). Treat them specially: translate the lowercase form,
# uppercase the result. That way "STATUS" → "status" → "estado" → "ESTADO".
_ALL_CAPS_RE = re.compile(r"^[A-Z][A-Z\s\-/]{0,30}$")


def _is_all_caps_short(text: str) -> bool:
    return bool(_ALL_CAPS_RE.match(text)) and any(c.isalpha() for c in text)


def safe_translate(client: LibreTranslate, text: str, target: str) -> str:
    # Pure placeholder strings — no semantic content, return as-is.
    if not text.strip() or _is_placeholder_only(text):
        return text

    # Single-token strings that are obvious brand/tech terms — return as-is
    # (the protect step would do it too but this saves an API round-trip).
    stripped = text.strip()
    if stripped in NO_TRANSLATE_TERMS:
        return text

    # ALL-CAPS short strings (section headers, theme labels): translate the
    # lowercase form and uppercase the result for cross-locale consistency.
    if _is_all_caps_short(stripped):
        protected, sentinels = _protect(stripped.lower())
        out = client.translate(protected, target) if not sentinels else client.translate(protected, target)
        out = _restore(out, sentinels)
        return out.upper()

    # Standard path: protect, translate, restore.
    protected, sentinels = _protect(text)
    out = client.translate(protected, target)
    out = _restore(out, sentinels)

    # Sanity check: if the translation is wildly longer/shorter than the source
    # AND no sentinel survived, the model probably hallucinated. Fall back to
    # the source string so the user sees English instead of garbage like
    # "VISUAL AUTOMATION · v{0}" → "Eliminating discrimination against women"
    # observed during early Argos runs on uppercase + placeholders.
    if sentinels and not _RESTORE_RE.search(out):
        # All sentinels lost — restore would yield broken output.
        return text
    src_len = max(1, len(text.strip()))
    out_len = len(out.strip())
    if out_len > src_len * 5 or (src_len > 6 and out_len < src_len // 4):
        return text
    return out


# ── Main pipeline ──────────────────────────────────────────────────────────

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--bundle-dir",
                        default="IDE/src/main/resources/i18n",
                        help="Directory containing IDE_*.properties bundles "
                             "(default: %(default)s)")
    parser.add_argument("--endpoint",
                        default=DEFAULT_ENDPOINT,
                        help="LibreTranslate endpoint URL (default: %(default)s)")
    parser.add_argument("--only",
                        default=None,
                        help="Comma-separated locale suffixes to (re)generate "
                             "(default: all in LOCALE_MAP). Example: de,es,zh_CN")
    parser.add_argument("--include-fr",
                        action="store_true",
                        help="Also auto-translate FR (default: skip, FR is "
                             "hand-translated and considered authoritative)")
    parser.add_argument("--overwrite",
                        action="store_true",
                        help="Overwrite existing values in target bundles "
                             "(default: only fill missing keys, preserve "
                             "RaiMan-era hand translations)")
    parser.add_argument("--dry-run",
                        action="store_true",
                        help="Don't write files, just report what would change")
    args = parser.parse_args()

    bundle_dir = Path(args.bundle_dir)
    if not bundle_dir.is_dir():
        print(f"ERROR: bundle dir not found: {bundle_dir}", file=sys.stderr)
        return 2

    source_path = bundle_dir / f"IDE_{SOURCE_LOCALE}.properties"
    if not source_path.exists():
        print(f"ERROR: source bundle not found: {source_path}", file=sys.stderr)
        return 2

    source_entries = parse_properties(source_path)
    source_idx = index(source_entries)
    print(f"Source: {source_path} — {len(source_idx)} keys")

    client = LibreTranslate(args.endpoint)
    supported = client.supported()
    print(f"LibreTranslate supports {len(supported)} languages")

    targets = LOCALE_MAP.copy()
    if not args.include_fr:
        targets.pop("fr", None)
    if args.only:
        wanted = {x.strip() for x in args.only.split(",") if x.strip()}
        targets = {k: v for k, v in targets.items() if k in wanted}
        if not targets:
            print(f"ERROR: --only filter selected nothing. Available: "
                  f"{','.join(sorted(LOCALE_MAP))}", file=sys.stderr)
            return 2

    total_added = 0
    total_skipped = 0
    for locale_suffix, lang_code in sorted(targets.items()):
        if lang_code not in supported:
            print(f"\n[skip] {locale_suffix:>6} ({lang_code}) — model not loaded "
                  f"in LibreTranslate (re-run container with LT_LOAD_ONLY?)")
            continue

        target_path = bundle_dir / f"IDE_{locale_suffix}.properties"
        target_entries = parse_properties(target_path)
        target_idx = index(target_entries)
        existing_keys = set(target_idx.keys())

        missing_keys = [k for k in source_idx if k not in existing_keys]
        keys_to_overwrite = list(existing_keys & set(source_idx)) if args.overwrite else []
        all_keys = missing_keys + keys_to_overwrite

        action = "fill" if not args.overwrite else "overwrite"
        print(f"\n[{locale_suffix:>6} → {lang_code}] {len(missing_keys)} missing"
              + (f", {len(keys_to_overwrite)} to {action}" if keys_to_overwrite else ""))

        if not all_keys:
            total_skipped += 1
            continue

        # Translate one key at a time. LibreTranslate supports batch but the
        # per-key approach gives better failure granularity (one stuck call
        # doesn't lose the whole locale) and clearer logs.
        new_translations: dict[str, str] = {}
        t0 = time.time()
        for i, key in enumerate(all_keys, 1):
            src_val = source_idx[key]
            new_val = safe_translate(client, src_val, lang_code)
            new_translations[key] = new_val
            if i % 5 == 0 or i == len(all_keys):
                print(f"    {i}/{len(all_keys)} translated"
                      f" — last: {key} = {new_val[:50]!r}")
        elapsed = time.time() - t0
        print(f"  done in {elapsed:.1f}s")

        # Merge into target_entries: append new keys at the end with a header
        # marker; overwrite existing values in place if --overwrite.
        if args.dry_run:
            for k, v in new_translations.items():
                print(f"    DRY-RUN  {k} = {v[:80]}")
            continue

        # In-place overwrite for existing keys
        for entry in target_entries:
            if entry.key in new_translations and entry.key in existing_keys:
                entry.value = new_translations[entry.key]
                # raw will be regenerated on write; keep key for diff readability
                entry.raw = ""

        # Append missing keys at the end of the file with a marker comment.
        if missing_keys:
            target_entries.append(Entry(raw="", key=None, value=None))  # blank
            target_entries.append(Entry(
                raw=f"# Auto-translated by LibreTranslate ({lang_code}) — "
                    f"corrections welcome via github.com/oculix-org/Oculix/issues",
                key=None, value=None))
            for k in missing_keys:
                target_entries.append(Entry(
                    raw="", key=k, value=new_translations[k]))

        write_properties(target_path, target_entries)
        total_added += len(missing_keys)
        print(f"  wrote {target_path}")

    print(f"\nDone — {total_added} keys added across {len(targets) - total_skipped} locales")
    return 0


if __name__ == "__main__":
    sys.exit(main())
