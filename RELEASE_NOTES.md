## OculiX 3.0.2

Date: 2026-04-20
Maven Central: **oculixapi-3.0.2** + **oculixide-3.0.2**

Major release: new MCP server module, expanded Modern Recorder, IDE stabilization, Apple Silicon support.

### MCP Server (new module)

- New `oculix-mcp-server` module: exposes OculiX as a Model Context Protocol server (stdio + HTTP)
- Signed audit trail: Ed25519 + SHA-256 chained JSONL journal + deterministic rotation + CanonicalJson
- HTTP transport: confidential mode, multi-session, HMAC-signed session tokens, rotatable keyring, TLS policy, Bearer auth
- 12 exposed tools: Click / DblClick / RClick / Find / FindText / Exists / Wait / KeyCombo / OCR / Screenshot / Type
- ActionGate V1 (auto-approve, V2 human-in-the-loop in preparation)

### Modern Recorder

- Closes #87: Browse file option for image captures, Click/DblClick/RClick/Wait unified via `pickImage()`
- New actions: Swipe (configurable start + smart direction), DragDrop (capture/browse/library), Wheel (interactive modal), Key Combo (modal with modifiers)
- Image library: reuse captured images across actions
- Name prompt before saving + automatic copy to script bundle on Insert & Close
- Full cleanup of `oculix_recorder_*` temp dirs
- Fix NPE in `EditorImageButton.toString` when options is null
- Load OpenCV once in `RecorderAssistant` constructor

### IDE — Workspace & stability

- Workspace management: create / open / rename / auto-refresh
- ScriptExplorer rewritten: visual script cards, Eclipse-style layout (explorer left, console bottom, dark welcome)
- Sidebar: OculiX bold Serif logo + IDE subtitle, live info panels (project, status, last run)
- File menu: flat popup with visible section headers (built manually for visibility)
- File dialogs: open on workspace/script directory
- Save As: automatic `.py` extension in `.sikuli` bundle, refresh workspace cards
- Welcome Tab: stabilized lifecycle, fixed image ratio, null context safety
- Fix startup crash: `restoreSession` with empty script
- Fix quit crash: `saveSession` NPE when no scripts open
- Fix Save As / workspace open: `setFile` NPE, `.sikuli` bundle validation, flexible `.py` detection

### Platform — Apple Silicon

- Native Apple Silicon M1/M2/M3 support (Apertix 4.10.0-2)
- Fix OpenCV `UnsatisfiedLinkError` on macOS: native lib load in `Commons` static init
- Fix OpenCV version mismatch in `sikulixcontent` manifests on macOS/Linux
- Fix native lib paths aligned with JNA conventions
- macOS ARM64 regression test: OpenCV load-before-Finder

### OCR & OculixKeywords

- New Robot Framework `OculixKeywords` library: composite clicks, waits, regions, ROI, highlights, captures
- Pluggable OCR engine (Tesseract / PaddleOCR) for `OculixKeywords`
- PaddleOCRClient rewritten with `org.json` (buggy minimal parser replaced)
- Fix Jython Unicode crash in PaddleOCR probe
- Jython integration test script for `OculixKeywords`

### DPI & matching pipeline

- DPI-aware pipeline: scoring fix, PNG metadata injection, multi-scale fallback
- Robust OpenCV loading: `Core.java` aligned with Apertix 4.10.0

### TigerVNC

- TigerVNC Java sources extracted to external GPL repo `tigervnc-java-oculix` (license isolation)
- Transport bundle prepared

### Legacy API

- Restored `-s` CLI flag (legacy ServerRunner on port 50001) — validated baseline before modern HTTP refactor (Javalin)

### Build & CI

- `publish-maven.yml`: Maven Central publication
- Build and Release workflow pushes on `release/oculix` branch
- Removed legacy SikuliX ide-snapshot workflow
- `.mailmap`: remap legacy commit identity
- `project-status-sync` Action: syncs `status:*` labels with Project v2 Status field

### Closed issues

- **#87** Image captures: add Browse file option alongside screen capture
- **#138** API: remove vendored `org.opencv.*` bindings, rely on Apertix

### Contributors

- Julien Mer (@julienmerconsulting) — fork maintainer
- Raimund Hocke (@RaiMan) — original SikuliX1 creator

---

## OculiX 3.0.0 - First official release

Active fork of SikuliX1 (archived on March 3, 2026). Visual automation for the real world.

### Major additions
- **VNC full stack**: VNCScreen, VNCRobot, VNCClient, VNCFrameBuffer, VNCClipboard, XKeySym (2200+ key definitions)
- **Native SSH**: SSHTunnel via embedded jcraft/jsch, zero external dependency
- **Android ADB**: ADBScreen, ADBRobot, ADBDevice, ADBClient, ADBTest via embedded jadb
- **Multi-engine OCR**: PaddleOCREngine (629 lines), TesseractEngine, pluggable OCREngine
- **Multi-language runners**: Jython, JRuby, Python, PowerShell, AppleScript, Robot Framework, Network, Server
- **VNC fixes**: Raw encoding, ZRLE/Tight corruption fix, SetPixelFormat negotiation, VncAuth only

### Dependencies
- **Apertix 4.10.0-0**: replacement of openpnp/opencv 4.5.4 with OpenCV 4.10.0 compiled from scratch (Windows x86-64)
- Full JNA migration (compatible with JDK 16+, zero `--add-opens`)

### Build
- Java 17+, Maven multimodule
- Artifacts: **oculixapi-3.0.0.jar** + **oculixide-3.0.0.jar**

### Base
- Fork of RaiMan/SikuliX1 @ MIT License
