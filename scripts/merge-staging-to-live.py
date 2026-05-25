#!/usr/bin/env python3
"""
merge-staging-to-live.py — Promote translation/IDE_<locale>.properties
staging files into IDE/src/main/resources/i18n/IDE_<locale>.properties
live bundles.

Why this exists
---------------
scripts/translate-bundles.py writes auto-translated bundles to translation/
(staging) on purpose, so a native-speaker review can land via the
i18n-Languages GitHub issues before the bundle is exposed to end users.
That review loop is the right long-term workflow.

But for the Phase 1 ship of OculiX 3.0.x, the user wants the Welcome /
Sidebar / Status surfaces to render in the user's locale today, even at
imperfect quality, rather than fall back to English. This script is the
"merge it now, refine via PRs later" lever.

What it does
------------
For each staging file (translation/IDE_<locale>.properties):
  - skip the auto-generated header comment block
  - for each key=value pair, append to the live bundle
    IDE/src/main/resources/i18n/IDE_<locale>.properties
  - preserve the existing live bundle content untouched (so RaiMan's
    historical hand translations stay as-is — we only add new keys,
    never overwrite)
  - re-encode the value as Java legacy ASCII (\\uXXXX for non-ASCII)
    so the .properties file works on every JDK locale

Usage
-----
    python scripts/merge-staging-to-live.py            # all locales
    python scripts/merge-staging-to-live.py --only de  # one locale
    python scripts/merge-staging-to-live.py --dry-run  # preview
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

LIVE_DIR = Path("IDE/src/main/resources/i18n")
STAGING_DIR = Path("translation")


_KV_RE = re.compile(r"^(?P<key>[^#!=:\s]+)\s*=\s*(?P<val>.*)$")


def java_encode(s: str) -> str:
    """Java legacy ASCII escape for runtime safety on every JDK."""
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


def parse_properties_keys(path: Path) -> dict[str, str]:
    """Read a .properties file (UTF-8 encoded) and return key→value dict.
    Decodes Java \\uXXXX escapes back to characters."""
    if not path.exists():
        return {}
    out: dict[str, str] = {}
    text = path.read_text(encoding="utf-8")
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or stripped.startswith("!"):
            continue
        m = _KV_RE.match(line)
        if not m:
            continue
        key = m.group("key").strip()
        val = m.group("val")
        # Decode \\uXXXX back to characters so we can re-encode consistently
        val = re.sub(r"\\u([0-9a-fA-F]{4})", lambda mm: chr(int(mm.group(1), 16)), val)
        out[key] = val
    return out


def merge_locale(locale_suffix: str, dry_run: bool,
                 overwrite: bool = False) -> tuple[int, int, int]:
    """Merge staging into live for one locale. Returns (added, updated, skipped).

    Default (additive — the peixuana-safe rule): only keys ABSENT from live
    are appended; existing live keys are never touched (protects native review
    work from being clobbered by re-run auto-translation).

    overwrite=True (--overwrite): an UPSERT / diff for promoting a *validated
    native review*. For each staging key:
      - present in live AND value differs → UPDATED in place
      - absent from live                  → ADDED (appended)
    Crucially this is a diff, NOT a replace: live keys that are NOT in the
    staging are kept verbatim (comments + ordering too). The live bundle never
    shrinks to the staging's contents — it only gains keys or fixes values.
    """
    staging = STAGING_DIR / f"IDE_{locale_suffix}.properties"
    live = LIVE_DIR / f"IDE_{locale_suffix}.properties"

    if not staging.exists():
        print(f"  [{locale_suffix:>6}] no staging file, skip")
        return 0, 0, 0

    staging_keys = parse_properties_keys(staging)
    live_keys = parse_properties_keys(live) if live.exists() else {}

    missing = [k for k in staging_keys if k not in live_keys]
    common = [k for k in staging_keys if k in live_keys]
    # Additive mode never updates; overwrite updates only where the value
    # actually changed (avoids churn on identical keys).
    to_update = ([k for k in common if staging_keys[k] != live_keys[k]]
                 if overwrite else [])
    skipped = len(common) - len(to_update)

    if not missing and not to_update:
        print(f"  [{locale_suffix:>6}] {len(staging_keys)} staging keys, "
              f"nothing to add/update")
        return 0, 0, skipped

    if dry_run:
        print(f"  [{locale_suffix:>6}] DRY-RUN +{len(missing)} add, "
              f"~{len(to_update)} update, {skipped} skip")
        return 0, 0, skipped

    # 1) Start from the EXISTING live content and update changed keys IN PLACE.
    #    Comments, ordering and live-only keys are preserved verbatim — the
    #    file is rebuilt line-by-line, never replaced by the staging.
    update_set = set(to_update)
    if live.exists():
        body_lines = live.read_text(encoding="utf-8").splitlines()
        for i, line in enumerate(body_lines):
            m = _KV_RE.match(line)
            if m and m.group("key").strip() in update_set:
                k = m.group("key").strip()
                body_lines[i] = f"{k}={java_encode(staging_keys[k])}"
    else:
        # Brand-new locale (e.g. hi/bn/te) — write a header from scratch.
        body_lines = [
            "#",
            "# Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license",
            "#",
            f"# IDE_{locale_suffix}.properties — generated by merge-staging-to-live.py",
            "# Source: IDE_en_US.properties via Google Translate",
            "# Native-speaker corrections welcome via the i18n-Languages issue tracker.",
            "#",
        ]

    # 2) Append the keys that are genuinely missing from live, under a banner.
    if missing:
        body_lines.append("")
        body_lines.append("# ── Auto-translated keys promoted from translation/ (Phase 1 i18n)")
        body_lines.append("# ── Native-speaker corrections welcome via the GitHub")
        body_lines.append("# ── 'i18n-Languages' issue tracker / Translation issue template.")
        for k in missing:
            body_lines.append(f"{k}={java_encode(staging_keys[k])}")

    new_content = "\n".join(body_lines).rstrip("\n") + "\n"
    live.parent.mkdir(parents=True, exist_ok=True)
    live.write_text(new_content, encoding="utf-8")
    print(f"  [{locale_suffix:>6}] +{len(missing)} added, ~{len(to_update)} "
          f"updated, {skipped} unchanged")
    return len(missing), len(to_update), skipped


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--only",
                        default=None,
                        help="Comma-separated locale suffixes (default: all in translation/)")
    parser.add_argument("--dry-run",
                        action="store_true",
                        help="Preview what would change without writing files")
    parser.add_argument("--overwrite",
                        action="store_true",
                        help="UPSERT mode for a VALIDATED native review: also "
                             "UPDATE existing live keys whose value changed in "
                             "staging (in place), on top of adding missing ones. "
                             "Live-only keys are kept verbatim — the file never "
                             "shrinks. Default is additive-only (existing keys "
                             "untouched, the peixuana-safe rule).")
    args = parser.parse_args()

    if not STAGING_DIR.is_dir():
        print(f"ERROR: staging dir not found: {STAGING_DIR}", file=sys.stderr)
        return 2
    if not LIVE_DIR.is_dir():
        print(f"ERROR: live bundle dir not found: {LIVE_DIR}", file=sys.stderr)
        return 2

    available = sorted(p.stem.removeprefix("IDE_")
                       for p in STAGING_DIR.glob("IDE_*.properties"))

    if args.only:
        wanted = {x.strip() for x in args.only.split(",") if x.strip()}
        targets = [loc for loc in available if loc in wanted]
        if not targets:
            print(f"ERROR: --only filter selected nothing. Available: "
                  f"{','.join(available)}", file=sys.stderr)
            return 2
    else:
        targets = available

    print(f"Merging {len(targets)} locale(s) from {STAGING_DIR}/ into {LIVE_DIR}/\n")

    total_added = 0
    total_updated = 0
    for loc in targets:
        added, updated, _ = merge_locale(loc, args.dry_run, args.overwrite)
        total_added += added
        total_updated += updated

    suffix = " (dry-run)" if args.dry_run else ""
    mode = "overwrite/upsert" if args.overwrite else "additive"
    print(f"\nDone [{mode}] — {total_added} added, {total_updated} updated "
          f"across {len(targets)} locales{suffix}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
