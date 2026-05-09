# Changelog

All notable changes to **OculiX** are documented in this file.
Format inspired by [Keep a Changelog](https://keepachangelog.com/).
Versions follow [SemVer](https://semver.org/) with `-rcN` / `-betaN` / `-alphaN` suffixes for pre-releases.

> Each `## [vX.Y.Z]` section is consumed verbatim by `.github/workflows/release-rc.yml`
> and published as the GitHub Release body for the matching tag. Keep entries
> reader-friendly — they ship as the public release notes.

---

## [v3.0.3-rc5] - 2026-05-09

![status](https://img.shields.io/badge/status-pre--release-orange)
![track](https://img.shields.io/badge/track-RC-8250df)
![focus](https://img.shields.io/badge/focus-visual%20rebrand%20%2B%20i18n%20%2B%20bundling-blue)

> **Highlights** — visual rebrand foundation (theme system, gecko mascot, sidebar polish),
> Chinese/Japanese typing via clipboard pivot, CLI `-l`/`-e` auto-run finally green on
> Linux + Windows + WSL, native fat-jar slimmed by ~50 MB on Linux and **~114 MB on Windows**,
> ScreenUnion removed (Track4 cleanup), 73 new automated test cases.

### 🎨 Visual rebrand foundation
- New theme primitives: `OculixColors`, `OculixFonts`, `OculixDarkLaf`, `OculixLightLaf`
- Sidebar: wordmark, hero card, glowing status dots, pill-style theme switch
- Welcome tab: gecko mascot, RaiMan citation, theme-aware copy + center alignment
- Editor: tab strip surface, workspace/message bg, visible split divider
- Light mode: hero readability, auto-hide explorer, muted slate-blue debug colors
- IDE splash: gecko cyclope hero replaces the old banner
- `JFrame` icon set for Windows taskbar / Alt+Tab / title bar

### 🦎 Maven build banner (Easter egg, see #237)
- ASCII gecko + rotating "fun-but-pro" taglines + success/failure footer
- SLF4J `Logger` pipeline + per-project gecko markers
- Full UTF-8 (`✓ ✗ ▪`) with auto-fallback to ASCII glyphs on legacy Windows cmd
- `chcp 65001` forced at startup for Windows console UTF-8
- `.mvn/` extension jar bundled — works from the first clone, no manual install

### 🌗 Console & theme toggle
- Console theme decision via `PreferencesUser`, not LaF name (more robust)
- Scrollback re-htmlize on theme toggle — full session history preserved
- Drop auto-clear on script run (both inline + full-script paths)
- `ScriptExplorer` (workspace pane) follows the theme toggle
- Persist theme pref **before** LaF swap (no flash on next launch)
- Selection foreground/background pairing for dark-mode contrast
- Run separator routed through `System.out.println` (matches `[DEBUG STARTUP]` channel)
- Light mode: darker high-contrast log colors

### 📂 File dialogs & workspace
- `LAST_OPEN_DIR` write centralized across every chooser
- `SikulixFileChooser`: defaults to JAR working directory on first run
- `Save As` (flat `.py` + workspace): copies referenced PNGs into target folder
- Workspace: permissive script discovery — supports 3 folder layouts
- Workspace: explorer pane visibility forced via `invokeLater + revalidate`
- Image-flow: `Save As` copies bundle images, `ImageButton` rename works (#228)
- Recorder + file-open: skip duplicate import + reparse on load
- New `SikulixFileChooserDefaultDirTest` — 10 cases lock the dir-memory contract

### 🖼️ Pattern button & image flow (#209)
- `As Pattern` routes to legacy `PatternWindow` (preview matching, target offset, similar)
- In-place promotion with visible badge + code rewrite (no full-line replacement)
- `ImageButton` replaces the whole `Pattern(...)` chain in the editor view
- Image popup menu hides `As Pattern` once the button is already a Pattern
- Glyph indicators on the Pattern button + hover code variant
- Hover shows filename only — custom `Pattern()` preview popup dropped
- `doShowThumbs`: bounds + content check before swap (silences *"Invalid remove"*)
- `Optimize` wires `setParameters` / `setTargetOffset` on `EditorImageButton`
- `rename image` looks up button under `imgBtn` key (was `parm1`)
- `PatternWindow`: preview matching + null-safe target paint
- `SXDialogPaneImage`: progressive screen-resolution fallback

### 🚀 CLI flags `-l` / `-e` (#224)
- `-l <file>` preloads a script in the IDE on launch
- `-e` auto-runs the loaded script after startup completes
- Auto-run mirrors the `-r` flow verbatim — same `Runner.runScripts` path
- Validation reads CLI args directly instead of the loop counter (Windows-specific bug fixed)
- Trigger deferred to a clean EDT pulse (no race with Jython init)
- `-l` parsing tightened to one value per occurrence
- New `test-cli.sikuli/test-cli.py` smoke test for Linux / WSL / Windows parity

### 🌏 Unicode typing (#232)
- `Region.type()` automatically routes non-ASCII text through clipboard `paste()`
- Chinese / Japanese / Cyrillic / accented Latin now type correctly
- Previously failed with `Key: Not supported character: 你` and similar
- ASCII fast-path unchanged — zero overhead for Latin scripts

### 🔥 ScreenUnion removal (PR #235, Track4)
- `ScreenUnion.java` deleted — no longer needed in V3 architecture
- `Sikuli.py` `Screen.all()` now falls back to primary screen (drop-in)
- `PatternWindow` rewired to use `Screen` + `FileManager` directly
- `PatternPaneTargetOffset` updated post-removal
- 9 dead code paths cleaned up

### 📦 Native lib bundling slim (#236, Phase 1)
- Maven assembly excludes fixed across `API/makeapi-{lux,win,mac}.xml` and `IDE/makeide-{lux,win,mac}.xml`
- Apertix + Legerix native libs were targeting phantom paths (`/nu/pattern/opencv/<os>/`)
- Now match the **actual** top-level jar layout (`darwin/`, `linux-x86-64/`, `win32-x86-64/`)
- Result: **~50 MB shaved on Linux fat-jar**, **~114 MB on Windows fat-jar**
- Phase 2 (classifier-based resolution via `os-maven-plugin`) tracked in #236

### ☕ App API (#230) + Java 8 cleanup (#231)
- `App.open(String[])` overload bypasses `handleQuoting` for arguments containing spaces
- `DesktopSupport` / `TaskbarSupport` Java 8 cleanup (Track5)
- `tigervnc-java-oculix` bumped 2.0.0 → 2.0.1

### 🛠️ Other fixes
- `SX.input` rename: `isCancelled()` instead of broken `cancel()` return value
- `SX.input`: no NPE when lexer is unavailable for text reparse
- Image-button rename: replace broken `SX.input` with direct `JOptionPane`
- `LightLaf`: default-button foreground dark on cyan accent
- New 63-case IDE test suite: recorder, theme, workspace, lifecycle smoke

### 📝 Docs / housekeeping
- `@author Julien Mer` + `@author Claude` added to OculiX-original Java files (50+)
- `@author` tags removed from inherited SikuliX1 files (provenance via git history)
- CI: bug report template + chooser config, project-status sync, `status:*` label strip on close
- Local-only `CLAUDE.md` working notes removed from the tree

### 🐛 Closed in this RC
- #232 — Chinese / Japanese typing support
- #230 — `App.CommandLine.addArguments()` handleQuoting on spaces
- #228 — `ImageButton` inline rename
- #227 — `Save As` bundle images
- #224 — IDE `-l` / `-e` CLI flags
- #209 — Pattern thumbnail rendering parity with SikuliX

### 🔗 Living issues referenced
- #237 — 🦎 Maven gecko Easter egg (living doc)
- #233 — rc5 umbrella epic
- #236 — Native bundling roadmap (Phase 2)

---

```
                    .--.
                   |o_o |
                   |:_/ |   "From two reporters
                  //   \ \   to one matrix —
                 (|     | )  Jammy fleet saved.
                /'\_   _/`\   Now: rc5 ships."
                \___)=(___/

                 Tux + Gecko approve ✓
```

— Julien & le gecko 🦎

---

## [v3.0.3-rc4] - 2026-05-04

Earlier RCs (rc1 → rc4) are not retroactively detailed here. See the
[release page](https://github.com/oculix-org/Oculix/releases) and the
commit history for the included changes.

---

## [v3.0.2] - 2026-04-16

- MCP server module — stdio + HTTP, Ed25519-signed audit journal, 12 tools exposed
- Modern Recorder — Swipe, DragDrop, Wheel, Key Combo, image library, Browse-file
- Workspace management — Eclipse-style ScriptExplorer, project cards, live sidebar panels
- Apple Silicon native support (Apertix `4.10.0-2`)
- Jython Unicode crash fixed in PaddleOCR probe
- `OculixKeywords` Robot Framework library with pluggable OCR engine

## [v3.0.1] - 2026-03-29

- Apertix `4.10.0-1` — consistent OpenCV 4.10.0 natives across all 7 platforms
- Linux + macOS fat-jars rebuilt against fixed Apertix
- Native version verification step added to Apertix CI
- Closes #15 (OpenCV `UnsatisfiedLinkError` on Ubuntu 24.04)

## [v3.0.0] - 2026-03

Initial OculiX release after the SikuliX1 fork.
