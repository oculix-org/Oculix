#!/usr/bin/env python3
"""
translate-bundles.py — Auto-translate IDE_en_US.properties into all OculiX
locale bundles via Google Translate's public web endpoint.

Why this exists
---------------
OculiX ships a Java IDE with ~22 locales (IDE_en_US, IDE_fr, IDE_de, IDE_zh_CN, ...).
Historically only a subset of keys was translated by RaiMan + community.
The 3.0.x rebrand introduced ~30 new UI strings (Welcome tab, Sidebar,
status bar) that were initially hardcoded in English. Doing 30 new keys ×
21 target locales = ~630 entries by hand is the kind of friction that
kills i18n maintenance.

This script bridges the gap:
  - reads the source bundle IDE_en_US.properties
  - for each missing key in each target IDE_<locale>.properties, calls
    Google Translate's free public web endpoint to translate the value
  - writes the translated value back into the locale bundle
  - preserves existing translations (RaiMan's hand work stays intact)
  - escapes Java .properties Unicode (\\uXXXX) so output is portable
    across JDK locales without BOM headaches

Why Google Translate (and not LibreTranslate / Argos)?
------------------------------------------------------
We tried LibreTranslate self-hosted first (aligned with the OculiX
local-first philosophy). Quality was unusable on short UI strings:
  - "Docs"        → "Doctors" (Bulgarian)
  - "fork of X"   → "fourchette de X" (utensil)
  - "Dark theme"  → "Brand" (Bulgarian)
  - "VISUAL AUTOMATION · v{0}"
                  → "Eliminating discrimination against women" (Arabic)
  - placeholders {0} fragmented into "§§ 0 § § §"
Google Translate handles these correctly because its model has seen
orders of magnitude more parallel software-localized text. UI strings
are public anyway (visible in any unpacked jar), so the privacy concern
is moot. The IDE itself shows a one-time disclaimer popup on
auto-translated locales (see SikuliIDEI18N + i18nDisclaimer* keys).

Tamil (ta_IN) is excluded because Google Translate handles it but
the existing IDE_ta_IN.properties is a hand-curated RaiMan translation;
we leave it untouched for respect.

Privacy / dependency
--------------------
  - No pip install — pure stdlib (urllib, json, re, argparse).
  - No API key — uses the same free public endpoint browsers hit.
  - Network: ~700 small HTTP calls to translate.googleapis.com over
    ~5-10 minutes. If Google rate-limits, the script retries with
    exponential backoff.

Output layout
-------------
By design the script NEVER touches the live bundles in
IDE/src/main/resources/i18n/. It writes one staging file per locale into
translation/ at the repo root:

    translation/
      IDE_ar.properties
      IDE_de.properties
      IDE_zh_CN.properties
      ...

Each staging file holds ONLY the keys that were missing in the live bundle
(or all keys if --overwrite is set), with a header banner describing the
source, engine, and review process. The live bundle is the source of truth
for "what's already shipped"; the staging file is the "diff to merge after
native-speaker review".

Once a native speaker validates a locale (typically via a GitHub issue
tagged 'i18n-Languages'), the maintainer merges the staging file into
the matching live bundle by hand and deletes the staging file.

Usage
-----
    python scripts/translate-bundles.py                  # all locales
    python scripts/translate-bundles.py --only de,zh_CN  # subset
    python scripts/translate-bundles.py --dry-run        # preview, no write
    python scripts/translate-bundles.py --overwrite      # also re-translate
                                                         # keys present in
                                                         # the live bundle
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path

# ── Locale → Google Translate language code mapping ───────────────────────
# Java/SikuliX bundle suffix → ISO-639 code expected by Google.
# Notes:
#   - en_US is the source, never a target.
#   - pt_BR → 'pt' (Google's "pt" = Portuguese, no separate pt-BR for free
#     endpoint; for UI strings the diff is cosmetic).
#   - zh_CN → 'zh-CN' (simplified), zh_TW → 'zh-TW' (traditional).
#   - ta_IN → skipped on purpose (RaiMan hand translation preserved).
LOCALE_MAP: dict[str, str] = {
    "ar":    "ar",
    "bg":    "bg",
    "ca":    "ca",
    "da":    "da",
    "de":    "de",
    "es":    "es",
    "fr":    "fr",       # opt-in via --include-fr (FR is hand-translated)
    "he":    "he",       # Google uses "iw" historically but accepts "he"
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
    "zh_CN": "zh-CN",
    "zh_TW": "zh-TW",
}

SOURCE_LOCALE = "en_US"
SOURCE_LANG_CODE = "en"

# Strings we never want translated — brand names, tech acronyms, keyboard
# tokens. They get stitched out via sentinels before each call and stitched
# back verbatim afterwards.
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
    # Keyboard shortcut tokens
    "Ctrl", "Shift", "Alt", "Cmd",
}


# ── .properties parser/writer (preserves comments + key order) ────────────

@dataclass
class Entry:
    raw: str
    key: str | None
    value: str | None


_KV_RE = re.compile(r"^(?P<key>[^#!=:\s]+)\s*=\s*(?P<val>.*)$")


def _java_decode(s: str) -> str:
    s = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), s)
    s = s.replace("\\\\", "\x00")
    s = s.replace("\\n", "\n").replace("\\t", "\t").replace("\\r", "\r")
    s = s.replace("\\:", ":").replace("\\=", "=").replace("\\ ", " ")
    s = s.replace("\x00", "\\")
    return s


def _java_encode(s: str, ascii_escape: bool = True) -> str:
    """Encode a value for a .properties file.

    Two modes:
      - ascii_escape=True (default): historic Java format. ASCII printables
        kept verbatim, every other character emitted as \\uXXXX. Compatible
        with Java 1.0 .properties loader, BOM-safe, but unreadable for
        non-Latin reviewers (\\u8B66\\u544A means nothing to a human).
      - ascii_escape=False: keep characters in raw UTF-8. Modern Java 9+
        loaders accept this when invoked via PropertyResourceBundle(Reader)
        with an explicit UTF-8 reader, but the legacy Java loader (default
        ResourceBundle.getBundle classpath route) does NOT — it falls back
        to ISO-8859-1 and produces mojibake.

    The staging files in translation/ are NEVER loaded by the IDE — they
    are for native-speaker review. We write them in UTF-8 so reviewers
    can actually read what got translated. The merge step that copies
    validated keys into the live IDE bundle re-encodes in ascii_escape
    mode for runtime safety.
    """
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
        elif cp < 0x20:
            # Control chars below space — always escape, even in UTF-8 mode
            out.append(f"\\u{cp:04X}")
        elif ascii_escape and cp > 0x7E:
            out.append(f"\\u{cp:04X}")
        else:
            out.append(ch)
    return "".join(out)


def parse_properties(path: Path) -> list[Entry]:
    entries: list[Entry] = []
    if not path.exists():
        return entries
    with path.open("r", encoding="utf-8") as f:
        for raw_line in f:
            line = raw_line.rstrip("\n").rstrip("\r")
            stripped = line.lstrip()
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
    return {e.key: e.value for e in entries
            if e.key is not None and e.value is not None}


def write_properties(path: Path, entries: list[Entry],
                     ascii_escape: bool = True) -> None:
    """Serialize entries to disk. ascii_escape controls whether non-ASCII
    values are emitted as \\uXXXX (Java legacy, runtime-safe) or kept in
    raw UTF-8 (review-friendly, suitable for staging files only)."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        for e in entries:
            if e.key is None:
                f.write(e.raw + "\n")
            else:
                f.write(f"{e.key}={_java_encode(e.value, ascii_escape)}\n")


# ── Google Translate web endpoint client (free, no API key) ──────────────

GOOGLE_ENDPOINT = "https://translate.googleapis.com/translate_a/single"


class GoogleTranslate:
    """Thin urllib client for the public Google Translate web endpoint —
    same one browsers hit. Free, no API key, but rate-limited informally
    (~100 req/min). On HTTP 429 we back off and retry."""

    def __init__(self, max_retries: int = 4, throttle_ms: int = 80):
        self.max_retries = max_retries
        # Tiny per-call sleep keeps the request rate well under the
        # informal limit — burning 1 hour for 600 calls instead of getting
        # banned mid-run.
        self.throttle = throttle_ms / 1000.0

    def translate(self, text: str, target: str,
                  source: str = SOURCE_LANG_CODE) -> str:
        if not text.strip():
            return text
        params = {
            "client": "gtx",
            "sl":     source,
            "tl":     target,
            "dt":     "t",
            "q":      text,
        }
        url = f"{GOOGLE_ENDPOINT}?{urllib.parse.urlencode(params)}"
        backoff = 1.0
        for attempt in range(self.max_retries):
            try:
                req = urllib.request.Request(
                    url,
                    headers={
                        "User-Agent": "Mozilla/5.0 (translate-bundles.py)",
                        "Accept":     "*/*",
                    },
                )
                with urllib.request.urlopen(req, timeout=15) as resp:
                    data = json.loads(resp.read().decode("utf-8"))
                # Response shape: [[[<translated>, <source>, ...], ...], ...]
                # Multi-segment input concatenates all [0][i][0] pieces.
                segments = data[0] if data and data[0] else []
                pieces = [seg[0] for seg in segments if seg and seg[0]]
                time.sleep(self.throttle)
                return "".join(pieces) if pieces else text
            except urllib.error.HTTPError as e:
                if e.code in (429, 503) and attempt < self.max_retries - 1:
                    print(f"    HTTP {e.code} → backoff {backoff:.1f}s",
                          file=sys.stderr)
                    time.sleep(backoff)
                    backoff *= 2
                    continue
                print(f"    HTTP {e.code} translating to {target}: "
                      f"{text[:60]!r}", file=sys.stderr)
                return text
            except urllib.error.URLError as e:
                print(f"    Network error to {target}: {e}", file=sys.stderr)
                return text
            except (KeyError, IndexError, json.JSONDecodeError) as e:
                print(f"    Parse error on response for {target}: {e}",
                      file=sys.stderr)
                return text
        return text


# ── Sentinel-based protection for placeholders / HTML / brand names ──────

# We protect three classes of substring before sending text to the API:
#   1) MessageFormat placeholders ({0}, {1,number,integer}, ...)
#   2) HTML tags (<br>, <i>, <b>, ...)
#   3) Brand names / tech acronyms from NO_TRANSLATE_TERMS
#
# Each protected substring is replaced by a self-closing HTML-like sentinel
# <x0/>, <x1/>, ... — Google preserves these reliably because it's trained
# on parallel HTML / localized-string corpora and learns to leave tags alone.

_PROTECT_PLACEHOLDER_RE = re.compile(r"\{[^{}]*\}")
_PROTECT_HTML_RE = re.compile(r"<[^<>]+>")


def _protect(text: str) -> tuple[str, list[str]]:
    sentinels: list[str] = []

    def _stash(value: str) -> str:
        sentinels.append(value)
        return f"<x{len(sentinels) - 1}/>"

    text = _PROTECT_PLACEHOLDER_RE.sub(lambda m: _stash(m.group(0)), text)
    text = _PROTECT_HTML_RE.sub(lambda m: _stash(m.group(0)), text)
    for term in sorted(NO_TRANSLATE_TERMS, key=len, reverse=True):
        pattern = re.compile(r"\b" + re.escape(term) + r"\b")
        text = pattern.sub(lambda m: _stash(m.group(0)), text)
    return text, sentinels


_RESTORE_RE = re.compile(r"<x(\d+)\s*/?>")


def _restore(text: str, sentinels: list[str]) -> str:
    def _resolve(m: re.Match) -> str:
        idx = int(m.group(1))
        return sentinels[idx] if 0 <= idx < len(sentinels) else m.group(0)
    return _RESTORE_RE.sub(_resolve, text)


_PLACEHOLDER_ONLY = re.compile(r"^\s*(\{[^{}]*\}\s*)+$")


def _is_placeholder_only(text: str) -> bool:
    return bool(_PLACEHOLDER_ONLY.match(text))


_ALL_CAPS_RE = re.compile(r"^[A-Z][A-Z\s\-/]{0,30}$")


def _is_all_caps_short(text: str) -> bool:
    return bool(_ALL_CAPS_RE.match(text)) and any(c.isalpha() for c in text)


def safe_translate(client: GoogleTranslate, text: str, target: str) -> str:
    if not text.strip() or _is_placeholder_only(text):
        return text

    stripped = text.strip()
    if stripped in NO_TRANSLATE_TERMS:
        return text

    # ALL-CAPS short strings (section headers): translate the lowercase form
    # and uppercase the result for cross-locale consistency.
    if _is_all_caps_short(stripped):
        protected, sentinels = _protect(stripped.lower())
        out = client.translate(protected, target)
        out = _restore(out, sentinels)
        return out.upper()

    protected, sentinels = _protect(text)
    out = client.translate(protected, target)
    out = _restore(out, sentinels)

    # Sanity: if all sentinels evaporated, restore would yield broken output.
    if sentinels and not _RESTORE_RE.search(out) and not all(
            s in out for s in sentinels):
        return text

    # Sanity: wild length divergence usually means hallucination.
    src_len = max(1, len(text.strip()))
    out_len = len(out.strip())
    if out_len > src_len * 5 or (src_len > 6 and out_len < src_len // 4):
        return text
    return out


# ── Main pipeline ────────────────────────────────────────────────────────

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--bundle-dir",
                        default="IDE/src/main/resources/i18n",
                        help="Directory containing the live IDE_*.properties "
                             "bundles, used as reference for which keys are "
                             "missing per locale (default: %(default)s)")
    parser.add_argument("--output-dir",
                        default="translation",
                        help="Directory where the auto-translated *complement* "
                             "files go. We never write into the live bundle "
                             "dir — output is a review-ready staging area, one "
                             "IDE_<locale>.properties per locale, containing "
                             "only the keys that were missing in the live "
                             "bundle. After native-speaker review, the file "
                             "is moved/merged into bundle-dir manually. "
                             "(default: %(default)s)")
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
                        help="Re-translate keys that already exist in the live "
                             "bundle (default: only translate keys missing "
                             "from the live bundle, preserve RaiMan-era hand "
                             "translations untouched)")
    parser.add_argument("--dry-run",
                        action="store_true",
                        help="Don't write files, just translate and report")
    parser.add_argument("--throttle-ms",
                        type=int, default=80,
                        help="Per-request sleep to avoid rate limit "
                             "(default: %(default)s ms)")
    args = parser.parse_args()

    bundle_dir = Path(args.bundle_dir)
    if not bundle_dir.is_dir():
        print(f"ERROR: bundle dir not found: {bundle_dir}", file=sys.stderr)
        return 2

    output_dir = Path(args.output_dir)

    source_path = bundle_dir / f"IDE_{SOURCE_LOCALE}.properties"
    if not source_path.exists():
        print(f"ERROR: source bundle not found: {source_path}", file=sys.stderr)
        return 2

    source_entries = parse_properties(source_path)
    source_idx = index(source_entries)
    print(f"Source: {source_path} — {len(source_idx)} keys")
    print(f"Output: {output_dir}/ (review-ready complement files)")

    client = GoogleTranslate(throttle_ms=args.throttle_ms)

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

    total_translated = 0
    output_dir.mkdir(parents=True, exist_ok=True)

    for locale_suffix, lang_code in sorted(targets.items()):
        live_target_path = bundle_dir / f"IDE_{locale_suffix}.properties"
        live_target_idx = index(parse_properties(live_target_path))
        existing_keys = set(live_target_idx.keys())

        missing_keys = [k for k in source_idx if k not in existing_keys]
        keys_to_overwrite = (list(existing_keys & set(source_idx))
                             if args.overwrite else [])
        all_keys = missing_keys + keys_to_overwrite

        print(f"\n[{locale_suffix:>6} → {lang_code}] {len(missing_keys)} missing"
              + (f", {len(keys_to_overwrite)} to overwrite"
                 if keys_to_overwrite else ""))

        if not all_keys:
            continue

        new_translations: dict[str, str] = {}
        t0 = time.time()
        for i, key in enumerate(all_keys, 1):
            src_val = source_idx[key]
            new_val = safe_translate(client, src_val, lang_code)
            new_translations[key] = new_val
            if i % 5 == 0 or i == len(all_keys):
                preview = new_val[:60].replace("\n", " ")
                print(f"    {i}/{len(all_keys)} — {key} = {preview!r}")
        print(f"  done in {time.time() - t0:.1f}s")

        if args.dry_run:
            for k, v in new_translations.items():
                print(f"    DRY-RUN  {k} = {v[:80]}")
            continue

        # Build the complement file: header banner + auto-translated keys.
        # No existing-key merge — this is a *staging* file the maintainer
        # reviews and merges into the live bundle by hand once a native
        # speaker has signed off.
        out_path = output_dir / f"IDE_{locale_suffix}.properties"
        complement_entries: list[Entry] = [
            Entry(raw="#", key=None, value=None),
            Entry(raw=f"# Auto-translated complement for IDE_{locale_suffix}.properties",
                  key=None, value=None),
            Entry(raw=f"# Source: IDE_en_US.properties — {len(all_keys)} keys",
                  key=None, value=None),
            Entry(raw=f"# Engine: Google Translate (en → {lang_code})",
                  key=None, value=None),
            Entry(raw=f"# Generated: {time.strftime('%Y-%m-%d %H:%M:%S')}",
                  key=None, value=None),
            Entry(raw="#", key=None, value=None),
            Entry(raw="# This file is NOT loaded by the IDE — it is a staging",
                  key=None, value=None),
            Entry(raw="# area. After native-speaker review (see GitHub issue",
                  key=None, value=None),
            Entry(raw="# tagged i18n-Languages), merge the keys into the live",
                  key=None, value=None),
            Entry(raw=f"# bundle at {bundle_dir}/IDE_{locale_suffix}.properties",
                  key=None, value=None),
            Entry(raw="#", key=None, value=None),
            Entry(raw="# Corrections / refinements welcome via",
                  key=None, value=None),
            Entry(raw="# github.com/oculix-org/Oculix/issues",
                  key=None, value=None),
            Entry(raw="#", key=None, value=None),
            Entry(raw="", key=None, value=None),
        ]
        for k in all_keys:
            complement_entries.append(
                Entry(raw="", key=k, value=new_translations[k]))

        # Staging files are never loaded by the IDE — keep them in raw
        # UTF-8 so native-speaker reviewers can actually read the values
        # (\\u8B66\\u544A escapes in IDE_zh_CN.properties make review
        # impossible). The merge-to-live step re-encodes to ASCII escapes
        # when keys move into IDE/src/main/resources/i18n/.
        write_properties(out_path, complement_entries, ascii_escape=False)
        total_translated += len(all_keys)
        print(f"  wrote {out_path} ({len(all_keys)} keys, UTF-8)")

    print(f"\nDone — {total_translated} keys translated "
          f"across {len(targets)} locales")
    print(f"Review staging files in: {output_dir}/")
    return 0


if __name__ == "__main__":
    sys.exit(main())
