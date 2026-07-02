# Changelog

All notable changes to **OculiX** are documented in this file.
Format inspired by [Keep a Changelog](https://keepachangelog.com/).
Versions follow [SemVer](https://semver.org/) with `-rcN` / `-betaN` / `-alphaN` suffixes for pre-releases.

> Each `## [vX.Y.Z]` section is consumed verbatim by `.github/workflows/release-rc.yml`
> (RC tags) and `.github/workflows/release.yml` (stable tags) and published as the
> GitHub Release body for the matching tag. Keep entries reader-friendly — they
> ship as the public release notes.

---

## [v4.0.0] - 2026-07-01

![status](https://img.shields.io/badge/release-stable-brightgreen)
![maven](https://img.shields.io/badge/maven_central-pending-lightgrey)
![track](https://img.shields.io/badge/upgrade-from_3.0.x_read_notes-orange)
![major](https://img.shields.io/badge/major--version-4.0.0-8250df)

> **Major-version release** — the API becomes a proper library (no bundled scripting runtime, no `RunTime` god-object), the reactor gets a shared in-JVM CVE injector, OCR gains a persistent `lastSeen` cache in the ×25-×150 range on repeated lookups, the SSH/VNC posture becomes a named consent instead of a compromise, and 22 IDE locales land with native reviews from the community.

### Highlights

- **RunTime dismantled, API demoted to a library** — a 46-commit refactor finally kills the ~2200-line `org.sikuli.script.support.RunTime` god-object after 15 years of accretion. The entire scripting runtime (`Runner`, `JythonSupport`, `JRubySupport`, `SikulixForJython`, `Sikulix` launcher) moves from API into IDE, so nothing running against `oculixapi` on the classpath can accidentally boot an IDE any more.

- **×25-×150 speedup on repeated OCR lookups** — the new `TesseractLastSeen` persistent cache mirrors #353's image `lastSeen` PNG-chunk trick on the text side: eight consecutive `findText("Espagne")` on Notepad drop from 48 s to <400 ms after warm-up, full-screen `findText` from >1 s cold to <50 ms warm, with per-resolution safety and atomic writes so a crash never leaves corrupt JSON.

- **Cross-distribution SSH/VNC posture** — `RemoteMode` gives the `AppLauncher` an explicit named consent (`CONFIDENTIAL` default = `sshpass -e` + strict host-key check; `YOLO` = single-operator lab targets), while `DependencyManagementInjector` pins Bouncy Castle 1.84 across all four standalone artifacts (`oculixapi`, `oculixide`, `oculix-mcp`, `oculix-reporter`) in one place, killing four Jython-transitive GHSAs without introducing a parent pom.

- **i18n foundation on 22 locales with native reviews** — Google-Translate-seeded pipeline (`scripts/translate-bundles.py`, sentinels for placeholders and brand tokens) plus a `merge-staging-to-live.py` that never overwrites native work; native passes landed for `zh_TW`, `zh_CN`, `pt_BR`, `it`, `uk`, `de` in this cycle, with `LanguagePicker` in the sidebar and `OculixFonts` falling back on non-Latin locales so glyphs stop rendering as tofu boxes.

### What's new for users

#### New features

- 🤖 **Android/ADB overhaul** — we closed the epic #297 backlog (four bugs: `inputKeyEvent` format-string crash #293, `swipe` ignoring `durationMs` #294, missing `dragAndDrop` overload #295, eager OpenCV load moved to lazy `captureDeviceScreenMat` #296) and rebuilt `ADBClient`'s adb binary resolution holistically — system `PATH`, then `ANDROID_HOME`/`ANDROID_SDK_ROOT/platform-tools`, then per-OS install paths (`~/Library/Android/sdk`, `~/Android/Sdk`, `%LOCALAPPDATA%/Android/Sdk`, `/opt/homebrew/bin`, …), so the headless `-r` Jython mode no longer needs a hardcoded path.

    On top of that:
    - serial-based device selection (`getDeviceBySerial` / `initBySerial` / `startBySerial`) honoring the #229 API
    - multi-session support via lazy `jadb` connect so `new ADBScreen(0)` / `new ADBScreen(1)` can drive two devices from one process
    - `wakeUp()` decoupled from `dumpsys power` (idempotent `KEYCODE_WAKEUP` — no more 4 dumpsys probes on the hot path)
    - `getDisplayDimension()` now reads `adb shell wm size` (the stable `Physical size: WxH` contract) with the old `dumpsys display` parse kept only as a fallback — so LDPlayer / BlueStacks emulators and non-stock ROMs stop returning `null` on `ADBScreen.start()` (#229)

    Validated end-to-end on a real Samsung Galaxy S20 FE (Android 13, One UI 5) over ADB WiFi. (#297 #293 #294 #295 #296 #229)

- 📦 **File > Export sub-menu revived (`.zip` / `.jar` / runnable `.jar`)** — SikuliX's original "pack compiled Jython into a runnable jar" flow (`java -jar oculix.jar -r script.jar`) still worked in OculiX but had been dormant since the menu cleanup — the `FileAction.ASJAR` / `ASRUNJAR` methods lived on with no menu entry to reach them (confirmed in #393).

    We split File > Export into a proper sub-menu (As `.zip` / As `.jar` script library / As runnable `.jar`) and wired the sidebar's Export button through the same handler, so the runnable-jar path is discoverable again. Sidebar was fully invisible for jar export since the Phase 1b migration, which meant the revived feature was reachable by no one.

    Under the hood:
    - `exportAsJar()` was still reading `EditorPane.editorPaneFile`, a field left null-initialised since the PaneContext refactor — rewritten to speak `PaneContext` (temp/non-bundle scripts get a polite popup instead of an NPE, saved bundles get a compiled `_sikuli.jar`).
    - Fixed a stale prolog import (`org.sikuli.script.SikulixForJython` → `org.sikuli.support.SikulixForJython`) that would have made every exported runnable jar die at runtime with `ImportError: No module named org.sikuli.script`.
    - `makeScriptjar` tightened to actually pack compiled `.py` bytecode.

    The `menuFileExport` i18n key is dropped in favour of EN-only inline labels across all 22 active locales (three sibling menu entries in EN keep the JMenu definition in sync without stale RU / BG / AR translations). (#392 #393)

- 🤖 **MCP surface grows from 9 to 13 tools + locale-aware OCR + tolerant `waitForStable`** — we added four new MCP tools so an LLM agent can drive a real UI in one round-trip per intent instead of orchestrating find-then-act loops client-side:
    - `oculix_click_at_point` — raw `x,y` click
    - `oculix_click_text` — OCR find + click, returns the bounding box so the caller can verify which match was hit when the text repeats
    - `oculix_scroll`
    - `oculix_wait_for_stable` — a server-side page-settle observer that polls a region and returns `stable=true` once the byte delta stays under `min_pixel_delta` for `settle_window_ms` continuous (defaults tuned for a Chrome half-pane: 800 ms window, 5000-byte threshold, 150 ms poll, 10 s timeout), replacing the arbitrary Bash sleeps agents used to stitch between steps.

    On the OCR side, `OcrBootstrap.resolveLanguage()` maps the OS locale (or an env override) to the right Tesseract `traineddata` code (`fr`, `en`, `es`, `hi`, `zh`, fallback `eng`) and `cmdRun` / `cmdServe` apply it once at startup, so every subsequent tool call sees an OCR engine already configured for the user's language — 9 unit cases cover the mapping.

    On the API surface, `Region.waitForStable(maxWaitMs, stabilityMs, pixelTolerance)` gains a third argument that treats two captures as stable when `≤ pixelTolerance` fraction of pixels differ; the no-arg form now defaults to 1 % tolerance so pages with permanent micro-animations (blinking cursor, carousel autoplay, sparkline) stop looping forever without callers wrapping every call in a try/except. `VNCScreen` inherits the tolerant path via virtual dispatch and still refreshes the framebuffer before each capture.

- 🦎 **IDE welcome polish** — `mainPane` divider now defaults to 70/30 (was 60/40) so the Welcome footer stays visible, and clicking the restore button after a maximize snaps the window to 70% x 70% centred instead of whatever pre-maximize size Windows had cached. `WelcomeTab` moves hero/body from `JLabel` HTML to `JTextArea` line-wrap so text sizes to the MigLayout cell, the 9-link footer is docked at tab level to spread full width, and the gecko is pinned at 360px height. Ships three runtime ASCII portraits — `gecko-ascii.txt`, `geeko-ascii.txt` (a nod to SUSE's chameleon, mascot since 1994), and `claude-ascii.txt` (half the commits in this repo already say `Co-authored-by: Claude`, now the product can too) — and un-ignores IDE `.txt` resources so they actually ship.

- 🔐 **`RemoteMode` — bounded SSH/VNC security posture** — we introduced a two-value enum (`CONFIDENTIAL`, `YOLO`) resolved from `OCULIX_REMOTE_MODE` to give the `AppLauncher` SSH+VNC path an explicit, named consent instead of a one-size-fits-all compromise.

    - **`CONFIDENTIAL`** (default, byte-for-byte the existing safe behaviour) — hands the password to `sshpass -e` via the `SSHPASS` env var and rejects unknown host keys.
    - **`YOLO`** — accepts password on the command line and auto-trusts host keys for single-operator lab targets (POS farms, kiosks, reprovisioned QA boxes).

    `fromEnv()` throws on typos for CLI paths, `fromEnvOrDefault()` fails closed to `CONFIDENTIAL` for background paths, and the resolved mode is logged once at launcher start so a post-incident audit sees a deliberate decision — not an accident. Deliberately mirrors the `ToolRegistry.Mode` shape from the MCP module so a future `OCULIX_SECURITY_MODE` unification would be a rename, not a refactor. (#421)

- 🎭 **`ActionLogRenderer` — personality knob for `Debug.action` / `Debug.error`** — Verbosity was the only lever on the action log path (`Debug` level 0..5), which meant either fully verbatim lines (`TYPE("password-in-plain")`, `CLICK(1234, 567)`) or nothing at all — fine at dev time, a real leak the moment CI archives or a projected demo console captured the stream.

    We added `org.sikuli.support.ActionLogRenderer` on the emission path, driven by a new `Settings.ActionLogMode` (case-insensitive `String`, defaults to `"CLEAR"` so existing scripts are byte-for-byte unchanged):
    - **`CLEAR`** — keeps today's verbatim output
    - **`MASKED`** — keeps the verb (`CLICK`, `TYPE`, `PASTE`, `HOVER`, …) and replaces every argument with `******`
    - **`SILENT`** — returns `null` and the surrounding `log(...)` call is skipped entirely (no empty-line ghost)

    Templates live in classpath resources under `org/sikuli/support/log-personalities/*.txt` with lazy caching, so extra narrative modes ship without touching `Debug`. Fail-safe by design — any rendering exception falls back to the raw message; the log layer must never break the caller's script. String rather than enum so the field roundtrips cleanly through Jython / JRuby reflection and `settings.properties` without a per-runner adapter. (#424)

#### Performance

- ⚡ **Persistent `lastSeen` via `oPLx` PNG chunk** — we persist `Image.lastSeen` inside the source PNG as a private ancillary chunk (W3C PNG 11.3.5), so a fresh JVM seeds its ROI from disk instead of paying the full-screen scan. On a hit, the find captures only the ×2.5-expanded ROI around the known position — that is the cold-start speedup.

    Measured on i3 7700 / 1920×1080 / 50 cold-start JVMs: **502 ms → 77 ms average find, ×6.46 speedup**, across the whole `click / wait / exists / hover / type` DSL.

    Non-breaking: an absent or stale chunk falls back to the existing full-scan path. Adds `org.sikuli.support.PngChunk` (JDK-only streaming read/write), `Image.setLastSeenAndPersist`, and ROI-only capture in `Region.doCheckLastSeenAndCreateFinder`. (#353)

- ⚡ **Persistent OCR `lastSeen` cache (`TesseractLastSeen`)** — Two weeks after shipping the image `lastSeen` cache (#353), we hit the same problem on the text side: eight consecutive `findText("Espagne")` on Notepad were re-scanning the full 1920×1080 screen every call — 48 s for eight lookups that had already found the answer once.

    The new `org.sikuli.support.TesseractLastSeen` mirrors #353 for OCR: on first hit the `Match` bounding box is persisted to `tesseract-lastseen.json` in `user.dir` (project-scoped, commit-friendly, out of `~/.oculix/` on purpose), and every subsequent `findText` / `existsText` / `click(text)` / `hover(text)` / `waitText` for the same `(text, search-region context)` pair rescans only the cached ROI. Injection is a single funnel in `Region.doFind`, so all text-driven public entry points inherit the speedup with zero duplication.

    Measured:
    - Notepad "Espagne" ×8 goes from 48 s to <400 ms after warm-up (~×120)
    - Full-screen `findText` drops from >1 s cold to <50 ms warm (~×25 low bound)

    Reads/writes are guarded by a single intra-process lock, writes are `ATOMIC_MOVE`d so a crash never leaves corrupt JSON, entries carry a screen-resolution stamp so a cache captured on 1920×1080 is silently ignored on a different display, and any IO/parse failure is swallowed — the cache layer must never break the user's automation. Design walkthrough and PoC numbers in Discussion #400. (#423)

- ⚡ **OCR pipeline 2-3x faster on large regions** — we cut end-to-end `OCR.readWords` time on a full-HD capture from ~6.2s to ~5.0s (~x1.24 hot path, warmup 17.8s → 8.3s = -53% cold path) by two patches and a setter.

    - **`Commons.getBufferedImage(Mat)`** now does a direct byte-buffer memcpy instead of a full PNG encode/decode round-trip (dominated at ~250-500ms per call on 4K images, fixed for grayscale + BGR, exotic layouts keep the legacy path).
    - **`TextRecognizer.optimize()`** now caps the resize factor to 0.8 on large search regions (>1 MP) so a full-screen OCR no longer explodes to ~4K and stalls Tesseract LSTM for ~10s — full-screen OCR drops from ~12-17s to ~6s with no text-recall loss (Tesseract 5.5.0 reads down to ~11px, the legacy x2 upscale was over-margin). Small regions keep the x2 upscale for precision.
    - **`TesseractEngine.setDataPath(String)`** — new setter for a custom tessdata override (fine-tuned model / air-gapped cache) while keeping every other knob on the shared `OCR.globalOptions()` so `Region.findText` and the pluggable engine can never silently drift.

- 📊 **OCR perf benchmark harness in CI** — added a JUnit bench (`API/src/test/java/bench/OcrPerfBench.java`, off the default Surefire scan, invoked with `-Dtest=OcrPerfBench`) that times four surfaces side-by-side — `OCR.readWords` (worst case, `findWord(s)` path), `Finder.findText` (line pass + drill-down), `Finder.findWords`, `Finder.findLines` — against a committed 1920x1080 issue-page screenshot (~250 real words, personal zones blanked). Runs on `windows-latest` (not `ubuntu-latest`) because OculiX is overwhelmingly deployed to automate Windows UIs — a Linux number underestimates what real users see. Empirical finding: the four methods stay within ~5% of each other because Tesseract LSTM dominates ~85-90% of the time. Future changes to `TextRecognizer`, `Commons.makeMat/getBufferedImage`, or Legerix Tesseract bundling now show up as a measurable delta instead of a vibe.

#### Bug fixes

- 🖱️ **macOS Accessibility preflight for keyboard-only scripts** — a long-standing silent-hang on macOS is now closed: when Accessibility permission was denied and a script used `type()` (or any keyboard action) before touching the mouse, the preflight check never fired and the script would block later around `waitForIdle` during typing.

    The fix wires `MouseDevice.start()` into `Screen.initScreens()` so the existing macOS-gated `RunTime.terminate()` runs early on every script, mouse or keyboard. We polished the caller site to gate the whole call on `Commons.runningMac()`, which eliminates the cosmetic ~100 ms cursor "mouse-move dance" that was running at every script startup on Linux and Windows for zero terminate value.

    An addon on `Mouse` + `MouseDevice` propagates the early-fail path, and a cross-OS smoke test (`MousePreflightSmokeTest` + `test-mouse-preflight.yml`) now runs the preflight on macOS/Linux/Windows on every PR so this regression can't come back silently. (#234 #270)

- 🎯 **Finder respects the user similarity threshold again (Modes 3/4/5)** — #273 reported that `Pattern(img).similar(0.7)` was returning matches at score `0.65` on macOS: the cascade's tolerant (Mode 3), smart (Mode 4) and multi-scale (Mode 5) branches were silently softening the requested threshold by ×0.9 / ×0.85 / ×0.9 and calling `setSimilarity()` with the degraded value, violating the API contract.

    We fixed the three modes to compare `maxVal` against the original score and stop overriding the `FindInput` similarity — the cascade still helps match transformed images (blur / grayscale / scale) but only when the real correlation exceeds what the user asked for.

    While validating the fix on Windows we uncovered two knock-on bugs in the scaled modes:
    - **Modes 2 (DPI-aware) and 5 (multi-scale)** were returning `Match` dimensions from the original template instead of the actually-matched scaled template, which crashed the next `hover()` / `click()` with `SikuliXception: image to search (80,80) is larger than image to search in (40,40)` — fixed by threading an `actualTemplate` Mat through `FindResult2` (#273).
    - The same 40×40 cached hit then broke `Region.checkLastSeen`'s fast-path with the same `isValid()` crash on the following find, so we added a size guard that falls back to a full-region search when the cached region can't contain the template.

    A retina regression then surfaced on Apple Silicon from that very guard — on DPI 2x every find logged `checkLastSeen: skipping` and re-ran the full cascade, defeating the optimization SikuliX1 users had relied on for years. Final fix expands the cached rect to the template size centered on the previous match before handing it to the Finder, so Modes 2 / 5 can re-locate the scaled object inside the expanded window; click coordinates still come from the scale-correct `Match`, and an edge-of-screen fallback logs `cannot expand to fit` under `-d 3`. A follow-up on `ScreenImage.getSub()` (deep copy) rounds out the chain so `Commons.makeMat` keeps working on the sub-image. (#273 #274)

- 🎬 **Recorder NPE on last event** — #286 came with a clean stack + minimal repro + suggested patch: on the minimal "record → 1 click → stop" scenario, `RecordedEventsFlow.handleMouseEvent` dereferenced a null `nextEventEntry` when the current event was the last one of the session, deterministically throwing an NPE at line 261 and losing the whole recording.

    We now treat a null next-event as "isolated event, finalize as-is" — an isolated PRESSED is not a drag, an isolated RELEASED finalises the click without waiting for a double-click candidate. Predates OculiX (the method has been untouched since SikuliX 2021). (#286)

- 🤫 **Reporter demo no longer prints spurious `ERROR` in mvn output** — #271 flagged that `mvn install` on the Reporter module logged `ERROR Tests run: 3, Failures: 1` and `ERROR There are test failures` even though `testFailureIgnore=true` kept the build green: `OculixJUnit5IntegrationTest.failedTest` was deliberately throwing an `AssertionError` to exercise the JUnit extension's `testFailed` callback and produce a FAILED row in the HTML demo, and Surefire faithfully reported the throw.

    We renamed the test to `simulatedFailedTest` and inject the FAILED step directly through the model API instead — the `Test.addStep` "worst wins" rule still promotes the outcome to `FAILED` in the HTML report, so the demo output is identical while `mvn -pl Reporter test` now shows `Tests run: 3, Failures: 0, Errors: 0, Skipped: 1` and `testFailureIgnore=true` is gone from the pom. (#271)

- 🎯 **Wheel Configuration dialog — crosshair anchors to the image, code preview wraps** — Two coupled UX bugs in the Modern Recorder Wheel dialog (#289). First, clicking off-center to set a scroll offset made the captured-region image shift inside its frame, opening a grey band on one side and clipping content (`Couleur 1` truncated to `Couleur`) on the other — the crosshair was stored in panel-absolute pixel coordinates while the panel itself was being re-laid by Swing after each click, so the image moved and the crosshair did not. The crosshair position is now derived every paint from the image-relative `offsetX / offsetY` model state, so it stays anchored to the pattern regardless of layout. Second, the live code preview was a `JLabel` in a MigLayout `[grow, fill]` column: as soon as an offset was set, the snippet grew from `wheel("img.png", WHEEL_DOWN, 3)` to `wheel(Pattern("img.png").targetOffset(65, 7), WHEEL_DOWN, 3)`, the column widened, and the OK / Cancel buttons were shoved off-screen — the dialog was effectively unusable. Replaced with a non-editable `JTextArea` with line wrap, so long snippets wrap inside the existing column width instead of forcing the dialog to grow. (#289)

- 🖨️ **Jython `print` reaches the Messages pane on every OS** — #272 reported on macOS that `Debug.on(3)` output landed in the IDE Messages pane while `print "..."` leaked to the underlying terminal.

    Root cause: `JythonSupport.init()` builds the `PythonInterpreter` singleton at startup, capturing `System.out` by reference, so the later `EditorConsolePane.initRedirect` `System.setOut(piped)` never reaches `sys.stdout`. A warm-up race made this look Mac-specific (Linux couldn't repro) but the bug was cross-platform.

    Two-site fix:
    - `JythonRunner.doRedirect` now actually calls `interpreterRedirect` (the body had been commented out with a stale TODO).
    - `doRunScript` / `doEvalScript` / `doRunLines` re-point `sys.stdout` / `sys.stderr` at the current `System.out` / `err` at the start of every run, once both invariants hold (interpreter created AND pane pipe installed).

    Net effect: `print`, `Debug`, and `System.out.println` all land in the same place — Messages pane in the IDE, terminal when run with `-c`. (#272)

- 🧵 **OpenCV loader parallel-safe** — running multiple Oculix instances on the same Linux host used to corrupt each other's OpenCV runtime because every JVM overwrote the same `/tmp/libopencv` shared object, crashing the previously-loaded processes. `Commons.extractAndLoad()` now extracts into a unique per-PID temp directory registered for JVM-exit cleanup, and a startup sweep purges any dir older than 24h so a JVM crash never leaves stale copies behind. Validated with a 10-instance / 5-minute image-matching stress run on Ubuntu 22.04 and 24.04 containers; smoke-tested on Windows; macOS coverage gap acknowledged. (#348 #349)

- 🐧 **Ubuntu 22 `libjpeg.so.62` fixed for real (Legerix `5.5.0-8`)** — the `5.5.0-6` bundle shipped in 3.0.4 turned out to still miss the codec on the legacy tier: libtool was eating the leading `$O` of the `$ORIGIN` RUNPATH, leaving a corrupt `RIGIN` that no loader could resolve. `5.5.0-8` static-links libjpeg / libwebp / libtiff into `libleptonica.so` on the legacy x86-64 tier and drops the RUNPATH dependency entirely, validated end-to-end on a fresh Ubuntu 22.04 container. We also collapsed the `<legerix.version>` property and the hardcoded `API/pom.xml` dependency version onto a single source of truth so future bumps stop desyncing the cache manifest. (#350)

- 🔤 **`findText` multi-word score no longer silently dropped** — a copy-paste typo in `Finder.doFindText` (line 1111, inherited from upstream SikuliX legacy) averaged `startText.getScore()` with itself instead of blending it with `endText.getScore()`, so any multi-word `findText` / `waitText` / `findAllText` match returned only the first word's OCR confidence and the last word's score was discarded. Match is still found, but callers thresholding or ranking on `match.getScore()` were misled — now the score is the intended `(startText + endText) / 2` average. Single-word queries were unaffected. (#380)

- 🧨 **`ButtonGenCommand` NPE guard** — `AutoCaptureForCmdButtons=true` used to crash the IDE on every command-button generation: `btnCapture` was hard-coded to `null` behind a years-old `//TODO` and then dereferenced twice. We wrapped the `insertComponent` + `captureWithAutoDelay` pair in an `if (btnCapture != null)` guard, so toggling the preference is now a no-op instead of a guaranteed `NullPointerException` — the guard collapses back to a no-op once the constructor is wired up. Caught by CodeQL's `Dereferenced variable is always null` rule during the April 2026 triage (#362).

- ⌨️ **Recorder now captures non-ASCII keystrokes correctly** — `RecordedEventsFlow.handleKeyEvent` compared two boxed `Character` values with `==`, which only worked by accident on the JVM's Latin-ASCII [0, 127] cache; anything above U+007F (CJK, accented Latin `é è ñ ç ü ö ä`, Cyrillic, Arabic, Hindi) silently skipped the `TypeTextAction` emission and fell through to slower per-character typing. Replaced with `Objects.equals(...)` — null-safe (the layout can legitimately fail to resolve a modifier combination) and value-based. CodeQL flagged this in the April 2026 static-analysis triage; the defect had been latent for years, absorbed by the surrounding code's tolerance. (#361)

- 👻 **Modern Recorder ghost popup** — the "Choose image source" dialog no longer bakes itself into the capture overlay: we dispose the source-select dialog and defer `userCapture()` past the EDT tick that actually repaints the hide, plus a graphics-state sync to close a Wayland compositor race. Self-inflicted regression from 3.0.3 (`RecorderImagePicker` / `RecorderActions`), now clean on X11, Wayland and Windows. (#387)

- 🔑 **Stop-hotkey modifiers now honored** — `PreferencesUser.getStopHotkeyModifiers()` was reading `GET_HOTKEY_MODIFIERS` while the setter writes `STOP_HOTKEY_MODIFIERS`, so any customized modifier was silently discarded and the default (Shift+Alt on Win/Linux, Shift+Cmd on macOS) always won. Latent SikuliX bug spotted while investigating the recorder stop hotkey (#386/#387 orbit); one-token alignment of the getter key restores custom modifiers. (#388)

- 🎯 **`exists()` no longer returns a phantom Match(1.0, 0, 0) on solid-color templates** — #395 reported that `exists("red.png")` on a plain-red PNG absent from screen returned a perfect match at the origin instead of `None`. Root cause: Mode 4 (smart grayscale) called `matchTemplate(TM_CCOEFF_NORMED)` directly, bypassing the `isPlainColor()` routing that `doFindMatch` uses to send uniform templates through `TM_SQDIFF_NORMED` — and on a zero-variance grayscale patch OpenCV's degenerate escape returns `Scalar::all(1)`. Mode 4 now measures `meanStdDev` on the grayscale template and skips when stddev ≈ 0, letting Mode 5 / null propagation take over cleanly. (#395)

- 🗣️ **Runner says it out loud when an engine is missing** — before, opening a `.rb` script without `jruby-complete` on the classpath was silently reclassified as `[text]` — the runner existed, its engine did not, and nobody said a word. `Runner.getRunner()` now names the runner that could have handled the file and prints the exact `java -cp <ide-jar>:<engine-jar>` incantation to enable it (JRuby is deliberately not bundled: ~34 MB, `<optional>true</optional>`). Deduped per identifier — said once it informs, said twice it nags. (#371)

- ⌨️ **`type(Key.F9)` types F9 again** — #416 reported that on 3.0.4 `screen.type(Key.F9)` printed an invisible box instead of pressing F9, and only the two-arg form `type(Key.F9, 0)` still worked. Root cause: the #232 non-ASCII paste route treated every codepoint above 127 as text, including the Unicode Private Use Area (U+E000..U+F8FF) where SikuliX stores `Key.F1..F12`, `Key.HOME`, arrow keys and other special-key markers — so instead of dispatching `typeKey(VK_F9)` it pasted an unprintable glyph via the clipboard. We excluded the PUA range from `containsNonAscii()`, restoring the SikuliX 1 keystroke path for special keys while keeping Chinese / Japanese / accented Latin paste support intact. Related to the silent-failure family on tn5250j / VNC / IBM ACS / POS (#396). (#416)

- 🐧 **Recorder graceful degrade on slim Linux** — JNativeHook's x86_64 `.so` dynamically links `libxkbcommon-x11.so.0`, which minimal Ubuntu images (slim WSL distros, CI containers) do not ship; the resulting `UnsatisfiedLinkError` slipped through the previous `catch(NativeHookException)` and cascaded as `NoClassDefFoundError` on the EDT, leaving a 60-line stack and a hidden IDE window after clicking Record. We now catch `Throwable` in `Recorder.registerNativeHook`, guard every `GlobalScreen.*` call behind a `nativeHookReady` flag, and surface an `ERROR_MESSAGE` dialog with the exact remediation (`sudo apt install libxkbcommon-x11-0 libxcb-xkb1 libx11-xcb1`) while re-showing the IDE.

#### Security

- 🛡️ **SikulixServer `-x` allow-list actually enforces** — CodeQL flagged `SikulixServer.allowedIPs` as "container contents are never accessed / maintainability" (#368), which understated the impact: the `-x ip,ip,...|file.txt` option had been documented since a 2020 TODO (`commit 1852f36e`) but no filter ever consulted the list — every connection was accepted regardless.

    We wrapped the root Undertow `HttpHandler` in an `IPAddressAccessControlHandler` that rejects non-listed source IPs at the handler layer when `-x` is passed (behavior unchanged when the option is absent, so no regression for existing users).

    Follow-up hardening (#369):
    - dropped the silent `DEFAULT_ALLOWED_IP = "localhost"` seed that gave false comfort on shared hosts
    - added a startup warning when `-x` is configured without any loopback entry (`localhost` / `127.0.0.1` / `0.0.0.0` / `::1`)
    - clarified the strict-no-auto-localhost semantics in the global `-h` output

    The whole server subsystem was subsequently removed in the CLI-args cleanup that landed later in the cycle (refs #226, #366, #399), so this fix hardened the option during its remaining lifetime rather than living on in v4.0. (#368 #360 #369)

- 🛡️ **`DependencyManagementInjector` — reactor-wide dep pins, zero runtime footprint** — OculiX ships `oculixapi`, `oculixide`, `oculix-mcp` and `oculix-reporter` as standalone Maven artifacts (no `<parent>` reference) so a downstream project can depend on just `oculixapi` without inheriting the whole tree. That autonomy made every transitive CVE painful: either introduce a parent pom and break the standalone publishing lifecycle, or duplicate the same `<dependencyManagement>` block across four child poms and pray they stay in sync (they never did). We added a small Maven core extension in `build-extensions/` — `DependencyManagementInjector extends AbstractMavenLifecycleParticipant` — that walks the reactor at `afterProjectsRead` and injects a shared `<dependencyManagement>` block into every project's effective model. Pins are declared as a static `Pin(groupId, artifactId, version, ghsaComment)` array inside the extension, registered via SPI (`META-INF/plexus/components.xml`) so Maven picks it up automatically with no `-D` flag and no child pom edit. Initial payload bumps Bouncy Castle to 1.84 — kills GHSA-p93r-85wp-75v3 (covert timing channel), GHSA-c3fc-8qff-9hwx (LDAP injection), GHSA-wg6q-6289-32hp (broken crypto algorithm) and GHSA-4cx2-fc23-5wg6 (excessive allocation), all pulled transitively by `jython-slim 2.7.4` which is a hard runtime dep of OculiX with no newer upstream release. Downstream consumers inherit the CVE-free graph via standard Maven resolution without knowing the extension exists; future CVE flags land as a one-line `Pin(...)` entry, everywhere at once. (#422)

#### i18n / native reviews

- 🌍 **i18n foundation — 22 locales, Google-Translate seeded, native-review workflow** — Between 2026-05-09 and 2026-06-03 we stood up the i18n pipeline OculiX v4.0 ships on.

    **Bundle generator** — `scripts/translate-bundles.py` (Python 3.10, stdlib-only, no `pip install` needed) fills the missing keys of `IDE_<locale>.properties` from the `IDE_en_US.properties` source of truth, protecting MessageFormat placeholders (`{0}`, `{1,number,integer}`) and HTML tags (`<br>`, `<i>`) with `<x0/>` sentinels and stitching out brand/tech tokens (OculiX, SikuliX, Apertix, Legerix, OpenCV, PaddleOCR, Tesseract, Robot Framework, Ctrl/Shift/Alt/Cmd, API/IDE/OCR/…) so the engine never touches them.

    **Translation backend pivot** — we started on a self-hosted LibreTranslate (Argos) backend but pivoted to Google Translate's public web endpoint after Argos returned:
    - `Docs → Doctors` (bg)
    - `Dark theme → Brand` (bg)
    - `VISUAL AUTOMATION → Eliminating discrimination against women` (ar)

    Technical sentinels held, semantic quality did not. Google Translate uses no API key, no Docker, and privacy is a non-issue since the strings live in every release jar on GitHub anyway.

    **Staging vs live** — bundles land in `translation/IDE_<X>.properties` as staging complements (banner header explains "not loaded by the IDE, corrections go through `i18n-Languages` issues"), written as raw UTF-8 so a Korean reviewer can read `의는` instead of `밀늘`; the merge-to-live path re-encodes to `\uXXXX` ASCII escapes. `scripts/merge-staging-to-live.py` promotes reviewed keys into the live bundle.

    **De-hardcoded IDE surfaces** — the Welcome tab, the OculiX Sidebar, the StatusBar and all 9 `Recorder*.java` files now route every user-facing string through `SikuliIDEI18N._I("key")` (Phase 1 wiring + Phase 2 ~160 new keys covering Recorder + dialogs + popups + preferences).

    **Live locale switch** — a `LanguagePicker` pill in the sidebar footer lets users switch locale live with a JOptionPane restart prompt (`setSelectedLocale` renamed to avoid a weak-access override on `JComboBox`).

    **Non-Latin font fallback** — `OculixFonts` falls back to `Font.DIALOG` on non-Latin locales (ar/he/ja/ko/zh/hi/bn/te/ta/ru/uk/…) so glyphs stop rendering as tofu boxes on Windows and Linux, and `Sikulix` + `PreferencesWin` were patched with the same fallback.

    **Community feedback loop** — the GitHub issue form `.github/ISSUE_TEMPLATE/translation_issue.yml` (111 lines) lets any native speaker report a bad string in three fields, and the Welcome tab footer surfaces a "Report a translation issue" link that lands on it.

    **Hand-translated tail** — FR and `ta_IN` stay hand-translated by a native speaker (Argos has no Tamil model anyway), and `hi`/`bn`/`te` were added to the locale map on release day since India is a top-3 QA market.

    This is the pipeline that let the native reviews of `zh_TW`, `pt_BR`, `it`, `de`, `nl`, `es`, `ru`, `uk`, `ja`, `ko`, `pl`, `sv`, `da`, `ca`, `he`, `tr`, `bg`, `ar` land through GitHub issues instead of maintainer inboxes.

- 🌐 **i18n Phase 3 — 12 more IDE surfaces de-hardcoded, +55 keys propagated to 22 locales** — Phase 3 finished the walk started in Phase 2: we stripped the last hardcoded strings out of `SikulixFileChooser`, `ButtonCapture`, `PreferencesWin`, `PreferencesWindowMore`, `SikuliIDEPopUpMenu`, `SikulixIDE`, `OculixSidebar`, `ScriptExplorer`, `WorkspaceDialog` and `ExtensionManager` (12 source files, ~285 lines rerouted through `SikuliIDEI18N._I()`), added 55 new keys to `IDE_en_US.properties` + hand-translated FR, then ran `scripts/translate-bundles.py` to propagate them to the 22 staging locales in one pass (`translation/IDE_<X>.properties`). The integration branch `claude/i18n-phase3` also absorbed the 3.0.4 + 10-CVE master merge and pinned `.claude/` out of the tracked tree as local AI workspace hygiene, so no more file-picker labels, popup entries, extension-manager prompts or workspace dialogs sit in English on a `de`/`ru`/`ja`/`zh_TW` install.

- 🧰 **Safer i18n staging→live promotion** — `merge-staging-to-live.py` rewritten with two explicit modes: additive (default, safe: never touches an existing live value) and `--overwrite` upsert (updates only where staging differs, preserves live-only keys, comments and ordering verbatim).

    At promotion, `<xN/>` sentinels are now restored to the exact `MessageFormat` placeholder from `IDE_en_US` (`{0}`, `{0,number,integer}`, …), bare apostrophes on placeholder-carrying values are doubled so `SikuliIDEI18N._I()` no longer swallows them, and `\n` / `\r` / `\t` / `\\` escapes decode fully — root cause of the recurring sentinel bug that shipped broken pt_BR/zh_CN/zh_TW and later bit Italian. Backed by 12 unit-test assertions in `scripts/test_merge_staging_to_live.py`. (#264)

- 🇹🇼 **Traditional Chinese native review (`zh_TW`)** — a native reviewer walked the Phase 1 + Phase 3 staging bundle and returned a full pass on the ~80 UI strings introduced by the refreshed Welcome tab, Sidebar, PreferencesWindow, capture overlay, Modern Recorder and LanguagePicker. We applied the review verbatim (42 lines rewritten, Traditional vocabulary preferred over Simplified-leaning fallbacks from the Google Translate seed) and promoted the reviewed keys from `translation/IDE_zh_TW.properties` into the live `IDE/src/main/resources/i18n/` bundle alongside the reviewed `pt_BR` batch. (#265)

- 🇧🇷 **pt-BR native review** — a Brazilian native reviewer took the auto-translated `IDE_pt_BR.properties` and gave it two full native passes: Phase 1 in May (35 key updates across the Welcome tab + sidebar strings) and Phase 3 covering all 235 keys with 48 corrections for false cognates, incorrect verb forms, and pt-PT spellings that read unnaturally to Brazilian users. Promoted from `translation/` staging to the live IDE bundle. Brazil sits in the top 3 OculiX download regions per SourceForge analytics, so this one materially lifts the out-of-the-box experience for a large share of users. (#259 #288)

- 🇨🇳 **zh_CN native review** — a native reviewer built the IDE from source and walked every visible string in context, flagging the classic Google Translate misses that no auto-translator catches:
    - `script` rendered as 剧本 (theatre play) instead of 脚本 (code)
    - `STATUS` as 地位 (social rank) instead of 状态 (system state)
    - `Dark theme` as 黑暗的 (literal darkness) instead of 深色
    - `recorder` as 记录 (log entry) instead of 录制 (capture)
    - plus keeping "Raiman" as a personal name rather than transliterating it to 雷曼

    China sits in the top 3 OculiX download regions per SourceForge analytics, so these corrections materially improve the out-of-the-box experience for a large slice of users. (#264)

- 🇺🇦 **Ukrainian native review (`uk`)** — a native reviewer walked the Phase 1 + Phase 3 staging bundle (Welcome tab, Sidebar, PreferencesWindow, capture overlay, Modern Recorder, LanguagePicker, SikuliX1 Extensions) and returned a full pass on the Google-Translate seed, catching the usual tech-UI traps (short labels, ambiguous `script` / `recorder` / `fork`, Ukrainian noun cases).

    We applied the review verbatim (286 new lines in the live bundle, 153 lines rewritten in staging) and promoted the reviewed keys from `translation/IDE_uk.properties` into the live `IDE/src/main/resources/i18n/` bundle. (#263)

- 🇮🇹 **Italian native review (`it`)** — a native reviewer walked the full staging bundle in three batches over two weeks and returned a native pass on the refreshed Welcome tab, Sidebar, PreferencesWindow, capture overlay, Modern Recorder and LanguagePicker strings.

    We integrated batches 1+2 verbatim (58 keys), then shipped a follow-up commit fixing 18 false-friends the Google-translated seed had slipped past everyone (`Tasto` vs `Chiave`, `Registratore` vs `Recorder`, and similar tech-UI traps), refreshed the staging file with batch-3 native values (18 lines rewritten), and finally promoted batch 3 (218 additions) from `translation/IDE_it.properties` into the live `IDE/src/main/resources/i18n/` bundle. (#254)

- 🇩🇪 **German Welcome tab (`de`) native review** — #403 flagged that the Welcome tab shipped untranslated on a `DE` IDE and that `welcomeBody` had a newline defect clipping text at line end.

    We seeded 19 `welcomeTab*` keys in `IDE_de.properties` as a DRAFT (24 lines added) and a native reviewer returned a full native pass, rewriting 13 keys in place with correct German phrasing and fixing the newline glitch. The broader `translation/IDE_de.properties` source bundle was also refreshed (107 lines touched across two passes) so the next locale rebuild carries the same voice end-to-end. (#403)

- 🪧 **`welcomeHero` rebrand across 6 non-EN locales** — we prefixed the Welcome tab hero line with "OculiX (ex SikuliX)" in `zh_TW`, `zh_CN`, `uk`, `pt_BR`, `it` and `fr` so the SikuliX lineage stays visible at first launch instead of disappearing behind the new brand. One-line change per bundle, no other keys touched.

#### Refactoring

- 🧹 **CLI args cleanup + legacy server code out** — the `cli-args-cleanup-and-server-isolate` branch was finalised on 2026-06-24 and we merged 17 commits into `master`.

    **Gone:**
    - `SikulixServer` (1081 lines)
    - `ServerRunner` (533 lines)
    - `Options` (parked)

    **Moved:**
    - `CommandArgs` / `CommandArgsEnum` — from `API/util` to `IDE/idesupport` where they belong
    - CLI parsing — from `Commons` to `Sikulix` so there is a single entry point instead of the historical `hasArg` vs `hasOption` dual API (#226), with startup-time validation on mutually exclusive options that used to be silently swallowed (#366)

    **Dropped or reworked:**
    - `-m` (multiple IDEs) dropped as meaningless
    - `-q` gets an explicit warning
    - `-e` / multiple-`-l` handling revised to hide the IDE window during the run and execute the last-loaded tab

    Net result: ~1600 lines removed from the IDE core, no server module dragged along, and CLI behaviour is finally predictable. (#399 #226 #366)

- 🧨 **RunTime dismantled, API demoted to a library** — a 46-commit refactor between 2026-05-13 and 2026-06-09 finally kills the `org.sikuli.script.support.RunTime` god-object (~2200 lines) after 15 years of accretion.

    **RunTime migration then removal** — `runcmd`, `pause`, `terminate` / `cleanUp`, the global constants and the extension-manager hooks were migrated section by section into `org.sikuli.support.Commons` (new 817-line home), then `RunTime` was deleted outright ("RunTime finally removed").

    **API demoted to library** — the `Sikulix` class in API is gone, the Maven `mainClass` switches to `SX` and the API pom drops the executable descriptor. API is now a library, not a launcher, so nothing running against `oculixapi` on the classpath can accidentally boot an IDE any more.

    **Scripting runtime moved out of API** — the whole runner subsystem (`Runner`, `InvalidRunner`, `NetworkRunner`, `PowershellRunner`, `AppleScriptRunner`, `JRubyRunner`, `JythonRunner`, `ServerRunner`) plus `JythonSupport` / `JRubySupport` / `SikulixForJython` moved from API into IDE, and the dead `NotUsedSikuliFromAPI.py` (704 lines) and unused `Sikuli.py` copies were renamed or deleted.

    **Support/ housekeeping** — in the same series:
    - historical `curvedMouseMove` / `minimizedWindow` experiments (-424 lines)
    - old `MacUtil.m` / `WinUtil.cc` / `WinUtil.dll` native prototypes superseded by the Java path (-746 lines)
    - SikuliX1 MavenCentral scaffolding (`pom_template.xml`, `README_template_*`, `maven-deploy-*`, `settings.xml`) (-390 lines)
    - deleted stray files that had been sitting in the repo (not shipped in the jars, just cluttering every clone): `README_21-11-12.md` (a 2012 README), `runide11m` (an obsolete IDE launcher script), `test.txt` (leftover scratch)
    - `ExtensionManager` moved under `Support/` (parked, not deleted — kept out of the build pending a rework)
    - `.gitignore` excludes `Support/personal/` so private scratch directories stop leaking into diffs

    **Net effect:** the API jar is smaller, has no scripting runtime bundled, boots faster, and the module boundary (API = engine, IDE = launcher + scripting) is finally clean.

#### Build & DX

- 🤝 **`Co-authored-by` git hook (bidirectional)** — we added `.githooks/prepare-commit-msg` so the contributor graph reflects the actual pair work on this repo. Mode 1 (web/SDK, Claude is the Author): the hook appends the human's identity, configured once per clone via `git config oculix.coauthor "Your Name <you@example.com>"`. Mode 2 (CLI-assisted, human is the Author): the hook always appends `Co-authored-by: Claude (Anthropic) <noreply@anthropic.com>`, because every human commit in this repo is Claude-assisted by construction. Activated with `git config core.hooksPath .githooks`; documented under Development setup in `CONTRIBUTING.md`. (#275)

#### Docs & community

- 📸 **PUB400 mainframe proof shot** — we committed the AS/400 `pub400.com` F3 screenshot to `assets/issues/` as the field-validation artefact behind #396: the smoking-gun evidence that `type(Key.F3)` had been silently rewriting to `Ctrl+V` on IBM Swing terminals since the #232 clipboard fallback landed. (#396)

### Maven coordinates

```xml
<dependency>
    <groupId>io.github.oculix-org</groupId>
    <artifactId>oculixapi</artifactId>
    <version>4.0.0</version>
</dependency>
```

API, IDE, MCP, and Reporter modules all publish to Maven Central under the same 4.0.0 version. Because API no longer bundles the scripting runtime, projects depending on `oculixapi` for automation get a lighter jar; projects that used to boot an IDE from the API classpath should depend on `oculixide` instead — see the module boundary note in the Refactoring section.

### Upgrading from 3.0.x

Read the Refactoring section before bumping. Two moves are potentially source-breaking for advanced integrators:

- **`org.sikuli.script.support.RunTime` is gone.** Callers of `RunTime.get()`, `RunTime.runcmd()`, `RunTime.pause()`, `RunTime.terminate()` should switch to the equivalents now living on `org.sikuli.support.Commons`.
- **Scripting runtime moved from API to IDE.** If your project depended on `oculixapi` and imported `org.sikuli.script.runners.*`, `JythonSupport`, `JRubySupport`, or `SikulixForJython`, add `oculixide` as a dependency (or park the scripting boot in a dedicated module).

Everything else — `Region`, `Screen`, `Pattern`, `Match`, `Finder`, OCR public entry points, `App`, VNC, ADB — stays byte-for-byte compatible with 3.0.x callers.

### Deferred to 4.0.1 / 4.1

- **`TesseractLastSeen` v2** — the persistent OCR cache lands in 4.0.0 with a single-key hit path; Discussion #400 tracks the follow-on features (multi-hit ranking, TTL, per-region eviction policy, telemetry hook). Feedback from the field will decide what makes 4.0.1 vs 4.1.
- **CodeQL long-tail** — the April 2026 triage cleared `#361` (Recorder character equality) and `#362` (ButtonGenCommand NPE) in this cycle; `#358` (`Runner` self-assignment) and `#360` (`allowedIPs`) were closed by the CLI-args cleanup that removed `SikulixServer` outright. Remaining low-severity items surface as they get filed.
- **macOS coverage on the parallel OpenCV loader** — #348/#349 validated on Ubuntu 22.04 and 24.04 and smoke-tested on Windows; a matching macOS stress run is a 4.0.1 follow-up rather than a blocker.
- **Additional native reviews** — `nl`, `es`, `ru`, `ja`, `ko`, `pl`, `sv`, `da`, `ca`, `he`, `tr`, `bg`, `ar`, `hi`, `bn`, `te` are seeded via the Google-Translate pipeline and staged under `translation/IDE_<locale>.properties`; native passes land through GitHub issues on the same workflow that shipped `zh_TW` / `pt_BR` / `it` / `uk` / `de` / `zh_CN` in this release.

### Contributors

This release stands on the work of many hands.

**Reporters and testers** — filed the clean repros, logs and setup details that made every fix in this cycle possible. Their contributions are referenced by issue number throughout the release:

- macOS Accessibility preflight (#234)
- Reporter demo mvn output (#271)
- Jython `print` routing (#272)
- Finder similarity threshold + scaled-mode chain (#273 #274)
- Recorder NPE on last event (#286)
- Wheel dialog crosshair anchor (#289)
- OpenCV native loader parallel safety (#348 #349)
- Ubuntu 22 `libjpeg` / Legerix (#350)
- Recorder CJK / accented-key equality (#361)
- `ButtonGenCommand` NPE (#362)
- CLI args cleanup and `Options` feature review (#226 #366 #399)
- `SikulixServer` allowed-IPs enforcement + hardening (#360 #368 #369)
- `Runner` engine missing message (#371)
- `exists()` phantom Match on solid-color templates (#395)
- `type(Key.F3)` / `type(Key.F9)` PUA regressions + PUB400 field proof (#396 #416)

**Native i18n reviewers** — walked the auto-translated bundles in context and returned full native passes through the GitHub issue workflow:

- Traditional Chinese (`zh_TW`) — #265
- Simplified Chinese (`zh_CN`) — #264
- Brazilian Portuguese (`pt_BR`) — #259 #288
- Ukrainian (`uk`) — #263
- Italian (`it`) — #254
- German Welcome tab (`de`) — #403
- French (`fr`) — hand-translated end-to-end
- Tamil (`ta_IN`) — inherited from the SikuliX era, native contributor identity predates OculiX

**Code contributors and reviewers:**

- @RaiMan — `RunTime` dismantle, CLI args cleanup, native `de` / `fr` / `ta_IN` translations, architectural direction, and the reports listed above
- Adrian Costin — cross-OS validation on the parallel OpenCV loader, Reporter runner review, i18n Phase 3 cross-check
- Junie (JetBrains AI) — assistant on parts of the maintainer work (trace: `.junie` in `.gitignore`)
- @julienmerconsulting — project lead, features and fixes throughout this cycle
- Claude (Anthropic AI) — assistant on the majority of commits in this cycle (visible in `Co-authored-by` trailers across the git log)

Full contributor graph in the git log (`git log --pretty=fuller`) and in every referenced issue.

🦎

---

## [v3.0.4] - 2026-05-20

![status](https://img.shields.io/badge/release-stable-brightgreen)
![maven](https://img.shields.io/badge/maven_central-pending-lightgrey)
![track](https://img.shields.io/badge/upgrade-drop--in_from_3.0.x-orange)

> **Security + Linux stability** — promotes `3.0.4-rc1` to stable, kills 10 transitive CVEs, and pulls in Legerix `5.5.0-6` with self-contained codec natives (resolves the Ubuntu 22 `libjpeg.so.62` mismatch, #350).

### What's new for users

- 🛡️ **10 CVEs killed** — `netty-codec*` pinned to `4.1.133`, `bouncycastle:bcprov-jdk18on` to `1.84`, `plexus-utils` to `3.6.1`. Removes all known transitive vulnerabilities flagged by the latest CodeQL / Dependabot run. No API change.

- 🐧 **Linux codec stability (Legerix `5.5.0-6`)** — Legerix now ships self-contained codec runtime libraries (`libjpeg`, `libwebp`, `libtiff` with `libsharpyuv` / `lzma` / `zstd` / `jbig` / `Lerc` transitives) on Linux/macOS via the vcpkg modern bundle. Resolves the long-standing `libjpeg.so.62 not found` error on Ubuntu 22.04 containers (#350). Validated by Adrian Costin on a fresh Ubuntu 22.04 container.

- 🔒 **Promotes `3.0.4-rc1` to stable** — All hardening from `3.0.4-rc1` (CodeQL `hashCode()` fixes, array OOB fix, workflow `contents: read` permissions, OpenSSF Best Practices badge, README modernization) is now stable. No behavior change vs `rc1` — pure version promotion + CVE patches + Legerix bump.

- 🦎 **Reporter and build-extensions deploy skips** — `central-publishing-maven-plugin` now correctly skips the `oculixreporter` and `oculix-build-extensions` modules under the release profile, preventing the deployment failure observed during the `3.0.4-rc1` publish attempt.

### Maven coordinates

```xml
<dependency>
    <groupId>io.github.oculix-org</groupId>
    <artifactId>oculixapi</artifactId>
    <version>3.0.4</version>
</dependency>
```

### Deferred to 3.0.5 / 4.0

- CodeQL triage bugs identified at `rc1` time: #358 (`Runner` self-assignment), #360 (`allowedIPs`), #361 (`RecordedEventsFlow`), #362 (`ButtonGenCommand`)
- Android `ADBDevice` cleanup #297 — branch ready, awaiting cross-OS validation (Linux/macOS) before merge
- SikuliX1 community PRs #345, #346 (EPIC #344)

---

## [v3.0.4-rc1] - 2026-05-18

![status](https://img.shields.io/badge/release-pre--release-yellow)
![maven](https://img.shields.io/badge/maven_central-pending-lightgrey)
![track](https://img.shields.io/badge/upgrade-safe_from_3.0.3-orange)

> **Polish release candidate** — community standards, security hygiene, README refresh. No breaking changes, no behavior changes. The 3.0.4 stable will follow with the CodeQL triage fixes (#358–#362) and the Legerix manifestSections fix (#350).

### What's new for users

- 🔒 **Security hygiene** — CodeQL findings cleaned up: `hashCode()` added to 8 classes that already override `equals()` (#352), array index OOB on pair-formatted arrays (#351). No user-facing impact, but the codebase is now CodeQL-clean on these rules.

- 🛡️ **Workflows hardened** — every GitHub Actions workflow now declares `contents: read` at the top level (#354), reducing token blast radius. Conforms to OpenSSF Scorecard expectations.

- 📋 **Community standards** — OpenSSF Best Practices badge added (#355), README redesigned to align with `docs.oculix.org` tone (#356, #357), CODEOWNERS scoped (RaiMan removed per his explicit request, MCP scoped to maintainer).

### Maven coordinates

```xml
<dependency>
    <groupId>io.github.oculix-org</groupId>
    <artifactId>oculixapi</artifactId>
    <version>3.0.4-rc1</version>
</dependency>
```

### Known follow-ups before 3.0.4 stable

- Legerix `manifestSections` fix (#350) — Ubuntu 22 `libjpeg.so.62` mismatch
- CodeQL triage bugs (#358 Runner self-assignment, #360 allowedIPs, #361 RecordedEventsFlow, #362 ButtonGenCommand)
- Cherry-pick remaining SikuliX1 community PRs (#345, #346 under EPIC #344)

---

## [v3.0.3] - 2026-05-09

![status](https://img.shields.io/badge/release-stable-brightgreen)
![maven](https://img.shields.io/badge/maven_central-published-blue)
![track](https://img.shields.io/badge/upgrade-drop--in_from_3.0.x-orange)

> **Major evolution release** — visual rebrand, full multilingual typing,
> slimmer fat-jars, MCP server matures. Drop-in upgrade from 3.0.x.

### What's new for users

- 🌏 **Universal typing** — write `region.type("你好")`, `region.type("こんにちは")`, `region.type("привет")` and it just works. ASCII path unchanged, non-ASCII routed automatically through clipboard. Asian-language and accented-Latin automation now first-class.

- 🎨 **Polished IDE** — full visual rebrand with dark/light theme system, Welcome tab, sidebar redesign, theme-aware console + workspace explorer. The IDE now looks and feels like a 2026 tool.

- 📦 **Lighter footprint** — fat-jars trimmed by **~50 MB on Linux** and **~114 MB on Windows**. Faster downloads, faster CI, lower bandwidth bill. Same API, same behavior.

- 🚀 **CLI auto-run** — launch the IDE with a script preloaded and auto-executed: `java -jar oculixide.jar -l my-script.sikuli -e`. Cross-platform (Linux + WSL + Windows).

- 🤖 **MCP server matured** — auditable visual-control server with Ed25519-signed action journal, ready for regulated environments (finance, health, defense). Plug any MCP-compatible AI agent on top of OculiX as its visual layer.

- 🦎 **Build banner** — every `mvn` / `mvnd` invocation greets you with the OculiX gecko. Personality at the build level. Easter egg, but also a discreet brand marker.

### Maven coordinates

```xml
<dependency>
    <groupId>io.github.oculix-org</groupId>
    <artifactId>oculixapi</artifactId>
    <version>3.0.3</version>
</dependency>
```

API, IDE, and MCP modules all available on Maven Central.

### Upgrading from 3.0.x

Drop-in. No breaking API changes. Bump the version in your pom and rebuild.

### Known issues / not in this release

We ship `3.0.3` knowing some things are still pending — preferring honesty over polish:

- **Android device picker** (#229) — when multiple emulators / devices are connected via ADB, the selection prompt is missing. Workaround: stop other devices, or manually export `ANDROID_SERIAL`. Targeted for `3.0.4`.

- **GNOME 3+ system tray icons** — IDE tray indicator (#223) not yet implemented. Linux KDE/XFCE/MATE/Cinnamon would already work; planned for `OculiX 4.0`. The Shift+Alt+C kill switch and the IDE message panel remain the canonical "is my script alive?" surfaces in the meantime.

- **CLI args parser** (#226) — internal duplication between `Commons.hasArg` and the Apache Commons CLI `Options` API. No user-facing impact, but the cleanup is on the V4 tech-debt list.

- **Visual Intelligence** (#160, #170-#179) — the natural-language `AItype("the red button below the menu")` API and the embedded ML pipeline are roadmap, not 3.0.x. They live on the [`OculiX 4.0`](https://github.com/oculix-org/Oculix/milestone/5) and [`OculiX 5.0 — Game Changers`](https://github.com/oculix-org/Oculix/milestone/6) milestones.

Full open backlog: [oculix-org/Oculix/issues](https://github.com/oculix-org/Oculix/issues). Bug reports and field feedback drive the next cycle — open an issue, a fork, or a PR.

### Contributors

This release was made possible by:

- [@julienmerconsulting](https://github.com/julienmerconsulting) — release lead
- [@RaiMan](https://github.com/RaiMan) — `ScreenUnion` removal, Java 8 cleanup
- [@kelvinkirima014](https://github.com/kelvinkirima014) — foundational EDT fix
- [@adriancostin6](https://github.com/adriancostin6), [@blackball](https://github.com/blackball), [@micves](https://github.com/micves), [@roboraptor](https://github.com/roboraptor) — bug reports and field validation
- [Claude](https://claude.com) (Anthropic) — pair-programming partner
- And the testers who pulled every RC and stuck around 🦎

### Detailed changelog

Full per-RC technical breakdown is preserved in the [v3.0.3-rc5](#v303-rc5---2026-05-09) section below. This stable release aggregates rc1 through rc5.

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
