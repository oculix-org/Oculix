# Changelog

> All merged PRs and significant changes since the fork from SikuliX1.

---

## PR Log

| PR | Type | Title | Status | Date |
|----|------|-------|--------|------|
| [#3](https://github.com/oculix-org/Oculix/pull/3) | 🔧 Fix | SikuliX → OculiX build errors and initial fork setup | Merged | 2026-03-17 |
| [#6](https://github.com/oculix-org/Oculix/pull/6) | ♻️ Refactor | Extract IDEMenuManager from SikulixIDE (part 1) | Merged | 2026-03-24 |
| [#7](https://github.com/oculix-org/Oculix/pull/7) | ♻️ Refactor | Extract IDEMenuManager from SikulixIDE (part 2) | Merged | 2026-03-24 |
| [#8](https://github.com/oculix-org/Oculix/pull/8) | 🚀 Feature | Patterns module — parent-child pattern library | Closed (parked) | 2026-03-25 |
| [#11](https://github.com/oculix-org/Oculix/pull/11) | ⬆️ Upgrade | Modernize CI and align with Apertix build infrastructure | Merged | 2026-03-29 |
| [#12](https://github.com/oculix-org/Oculix/pull/12) | ✨ Enhancement | Fix OpenCV native loading + DPI-aware matching pipeline | Draft | 2026-04-01 |

---

## Key Commits (on master, pre-PR era)

| Date | Commit | Description |
|------|--------|-------------|
| 2026-03 | `5e8f286` | Mega-repo: merge sikulix2tigervnc, sikulix2opencv, restore missing files |
| 2026-03 | `19e3274` | Add VNC Raw encoding fix, SSH tunnel, pluggable OCR architecture |
| 2026-03 | `e32dcb5` | Vendor sikulix2tigervnc sources into API module |
| 2026-03 | `977c450` | Vendor sikulix2opencv sources into API module |
| 2026-03 | `e5dfe01` | Migrate OpenCV from openpnp 4.5.4 to Apertix 4.10.0 |
| 2026-03 | `66b1028` | Rename JAR artifacts from sikulix to oculix |
| 2026-03 | `ba735fc` | Android feature enhancements and fixes |
| 2026-03 | `0ba1dd0` | Fix ADB Android 12+: display dimension, screencap buffer |
| 2026-03 | `a22913e` | VNC ZRLE/Tight corruption fix |
| 2026-03 | `c221d43` | Cleanup: remove obsolete modules (Jygments, Libslux, Tesseract) |
| 2026-03 | `072a49b` | Build 6 platform-specific fat JARs in parallel |

---

## Roadmap

### In Progress
- [ ] DPI-aware matching pipeline (PR #12)
- [ ] VNC additional corruption fixes (branch `claude/fix-vnc-corruption-WvspU`)
- [ ] IDE modernization continued (branch `claude/modernize-oculix-ide-sgjuh`)

### Parked
- [ ] Patterns module — SQLite pattern library with parent-child hierarchy (#8)

### Planned
- [ ] IDE/pom.xml alignment with Java 17
- [ ] macOS ARM64 (Apple Silicon) native support
- [ ] Linux ARM64 native support

---

## Version History

| Version | Base | Key Change |
|---------|------|------------|
| **3.0.1** | SikuliX 2.1.0 | Fork creation, Apertix migration, VNC/SSH/ADB/OCR |
