# Credits

OculiX is a Java visual automation platform that succeeds where invisible click-and-verify test tools fail — it clicks visibly, records what it sees, and stops when it does not recognize the screen. Building that kind of tool is a marathon, and it takes many hands beyond the maintainers whose names show on the git graph.

This file honors the people whose contribution is hard to see in the default GitHub Contributors view — bug reporters whose analysis shaped a fix, native-language reviewers who caught false-friends in every locale we ship, and voices that made the diff smarter. The GitHub graph tracks commits ; this file tracks intent.

## 🌍 Native language reviewers — v3.0.4 i18n campaign

The v3.0.4 release shipped six locales validated by native speakers. Each one read every string, corrected false-friends, and pushed back on Google-translated placeholders that would have made real users close the IDE at first launch.

| Locale | Reviewer | Contribution | Issue |
|---|---|---|---|
| Deutsch (de) | @RaiMan | Welcome tab review + native pass | #247, #403 |
| Italiano (it) | @daniele-paltrinieri-79 | Three review batches, 210+ keys validated, 18 false-friends caught, 8 IDE hardcoded strings surfaced | #254 |
| Português Brasileiro (pt_BR) | @issaojr | Full locale native pass | #259 |
| Українська (uk) | @Ihor467 | Full locale native pass | #263 |
| 简体中文 (zh_CN) | @peixuana | Full locale native pass | #264 |
| 繁體中文 (zh_TW) | @tcc | Full locale native pass | #265 |

## 🐛 Bug reporters and analysts — March 2026 → v4.0.0

These reporters filed issues whose diagnosis shaped a fix that shipped in a release. Each saved OculiX from a defect that would have hit real users.

| Reporter | Issue | Contribution |
|---|---|---|
| @genequ | #432 | UI text emoji/tofu on macOS Apple Silicon — box-drawing pitfall discovered during systemic sweep, three rounds of verification with codepoint-level precision |
| @andresluuk | #416 | Unicode PUA regressions in `containsNonAscii` — routing broken for `Key.F*` sequences |
| @Zdenda3D | #395 | `exists()` returning false Match 1.0 for solid-color images — motivated the Mode 4 grayscale variance guard, debunked several bad diagnostic narratives along the way |
| @robserm | #286 | NPE on last recorded event in `handleMouseEvent` |
| @blackball | #232 | IME support for Chinese and Japanese — clipboard-based `type()` route |
| @emoQin | #229 | Android multi-device support via ADB serial |
| @roboraptor | #224 | `-e` CLI validation reading loop counter instead of args |
| @micves | #163, #162, #209, #208, #207 | Thumbnail rendering pipeline — five issues covering position offset, missing extension, docstring interaction, NPE in `imageExists`, and rendering fallback |
| @shaworth | #15 | OpenCV `UnsatisfiedLinkError` on Ubuntu 24 — motivated the Legerix native refactor. Reported from land between two sailing trips. |

## 🤝 How to land here

Every non-maintainer whose bug report leads to a shipped fix, whose native review touches a locale that goes live, or whose analysis meaningfully shapes an architectural decision earns a line in this file. The GitHub Contributors graph will start reflecting these contributions the moment this file is committed with `Co-authored-by` trailers.

Thank you.

🦎
