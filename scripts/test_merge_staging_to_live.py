#!/usr/bin/env python3
"""Unit tests for merge-staging-to-live.py critical helpers.

Born from the <xN/> sentinel bug that shipped broken placeholders to
pt_BR/zh_CN/zh_TW (2026-05-23, hand-fixed in 0fb5a710) and then bit
Italian again (2026-06-12) because the script itself was never fixed
nor tested. Rule: a dry-run is not a test — assert on the output.

Run:  python3 scripts/test_merge_staging_to_live.py
"""

import importlib.util
import sys
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "msl", Path(__file__).parent / "merge-staging-to-live.py")
msl = importlib.util.module_from_spec(spec)
spec.loader.exec_module(msl)

EN = {
    "btnRecordStopHint": "Press {0} to stop",
    "sidebarImageCountSingular": "{0,number,integer} image  ·  idle",
    "recorderWheelLblOffsetCustom": "Offset: ({0}, {1})",
    "i18nLanguageChangedBody": "Language set to {0}.\n\nRestart OculiX for the full UI to switch.",
}

failures = []


def check(name, actual, expected):
    if actual != expected:
        failures.append(f"FAIL {name}\n  expected: {expected!r}\n  actual  : {actual!r}")
    else:
        print(f"  ok  {name}")


# ── restore_placeholders ──────────────────────────────────────────────
check("simple sentinel -> {0}",
      msl.restore_placeholders("btnRecordStopHint", "Premi <x0/> per interrompere", EN),
      "Premi {0} per interrompere")

check("rich form -> {0,number,integer} (EN-aware, not naive {0})",
      msl.restore_placeholders("sidebarImageCountSingular", "<x0/> immagine · inattiva", EN),
      "{0,number,integer} immagine · inattiva")

check("two sentinels -> {0} and {1}",
      msl.restore_placeholders("recorderWheelLblOffsetCustom", "Offset: (<x0/>, <x1/>)", EN),
      "Offset: ({0}, {1})")

check("unknown key falls back to plain {N}",
      msl.restore_placeholders("notInBase", "value <x0/>", EN),
      "value {0}")

check("no sentinel -> untouched",
      msl.restore_placeholders("btnRecordStopHint", "Tasto", EN),
      "Tasto")

# ── messageformat_quote ───────────────────────────────────────────────
check("apostrophe doubled when placeholder present",
      msl.messageformat_quote("Lingua impostata su {0}. Riavvia per l'interfaccia."),
      "Lingua impostata su {0}. Riavvia per l''interfaccia.")

check("idempotent: already-doubled '' stays ''",
      msl.messageformat_quote("Riavvia per l''interfaccia {0}"),
      "Riavvia per l''interfaccia {0}")

check("no placeholder -> apostrophe untouched (raw _I() path)",
      msl.messageformat_quote("Informazioni sull'aggiornamento non disponibili!"),
      "Informazioni sull'aggiornamento non disponibili!")

# ── java_decode / java_encode round-trip ──────────────────────────────
check("\\n decodes to newline (no double-escape on re-encode)",
      msl.java_encode(msl.java_decode("Set to {0}.\\n\\nRestart.")),
      "Set to {0}.\\n\\nRestart.")

check("\\uXXXX round-trips",
      msl.java_encode(msl.java_decode("ci\\u00F2 che")),
      "ci\\u00F2 che")

check("literal backslash round-trips",
      msl.java_encode(msl.java_decode("a\\\\b")),
      "a\\\\b")

# ── promote_value: full pipeline ──────────────────────────────────────
check("sentinel + apostrophe: full promotion",
      msl.promote_value(
          "i18nLanguageChangedBody",
          "Lingua impostata su <x0/>.\n\nRiavvia OculiX per cambiare l'interfaccia utente completa.",
          EN),
      "Lingua impostata su {0}.\n\nRiavvia OculiX per cambiare l''interfaccia utente completa.")

# ── verdict ───────────────────────────────────────────────────────────
print()
if failures:
    print("\n".join(failures))
    print(f"\n{len(failures)} test(s) FAILED")
    sys.exit(1)
print("All tests passed.")
sys.exit(0)
