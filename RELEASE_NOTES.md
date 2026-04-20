## OculiX 3.0.2

Date : 2026-04-20
Maven Central : **oculixapi-3.0.2** + **oculixide-3.0.2**

Release majeure : nouveau module MCP server, Modern Recorder étendu, stabilisation IDE, support Apple Silicon.

### MCP Server (nouveau module)

- Nouveau module `oculix-mcp-server` : expose OculiX comme serveur Model Context Protocol (stdio + HTTP)
- Audit trail signé : Ed25519 + SHA-256 chained JSONL journal + rotation déterministe + CanonicalJson
- Transport HTTP : confidential mode, multi-session, HMAC-signed session tokens, keyring rotatable, TLS policy, Bearer auth
- 12 tools exposés : Click / DblClick / RClick / Find / FindText / Exists / Wait / KeyCombo / OCR / Screenshot / Type
- ActionGate V1 (auto-approve, V2 human-in-the-loop en préparation)

### Modern Recorder

- Ferme #87 : Browse file option pour image captures, unification Click/DblClick/RClick/Wait via `pickImage()`
- Nouvelles actions : Swipe (start configurable + smart direction), DragDrop (capture/browse/library), Wheel (modal interactif), Key Combo (modal avec modifiers)
- Image library : réutilisation d'images capturées entre actions
- Naming prompt avant sauvegarde + copie automatique vers script bundle sur Insert & Close
- Cleanup complet des temp dirs `oculix_recorder_*`
- Fix NPE `EditorImageButton.toString` quand options null
- Load OpenCV une fois dans le constructeur `RecorderAssistant`

### IDE — Workspace & stabilité

- Workspace management : create / open / rename / auto-refresh
- ScriptExplorer réécrit : cartes visuelles pour les scripts, layout Eclipse (explorer left, console bottom, dark welcome)
- Sidebar : logo OculiX Serif bold + IDE subtitle, live info panels (project, status, last run)
- File menu : popup plat avec section headers (construit manuellement pour visibilité)
- File dialogs : ouverture sur répertoire workspace/script
- Save As : extension `.py` automatique dans le bundle `.sikuli`, refresh workspace cards
- Welcome Tab : lifecycle stabilisé, image ratio corrigé, null context safety
- Fix startup crash : `restoreSession` avec empty script
- Fix quit crash : `saveSession` NPE quand aucun script ouvert
- Fix Save As workspace open : NPE `setFile`, validation `.sikuli` bundles, detection flexible `.py`

### Platform — Apple Silicon

- Support natif Apple Silicon M1/M2/M3 (Apertix 4.10.0-2)
- Fix OpenCV `UnsatisfiedLinkError` macOS : chargement natif dans `Commons` static init
- Fix OpenCV version mismatch dans les manifests `sikulixcontent` macOS/Linux
- Fix paths native lib alignés sur conventions JNA
- Regression test macOS ARM64 : OpenCV load-before-Finder

### OCR & OculixKeywords

- Nouvelle librairie Robot Framework `OculixKeywords` : clicks composés, waits, regions, ROI, highlights, captures
- OCR engine pluggable (Tesseract / PaddleOCR) pour `OculixKeywords`
- PaddleOCRClient réécrit avec `org.json` (parser minimal buggué remplacé)
- Fix Jython Unicode crash dans le probe PaddleOCR
- Jython integration test script pour `OculixKeywords`

### DPI & matching pipeline

- DPI-aware pipeline : correction scoring, injection PNG metadata, multi-scale fallback
- OpenCV loading robuste : `Core.java` aligné sur Apertix 4.10.0

### TigerVNC

- Extraction des sources TigerVNC Java vers repo GPL externe `tigervnc-java-oculix` (isolation license)
- Transport bundle préparé

### Legacy API

- Restauration du flag CLI `-s` (legacy ServerRunner port 50001) — baseline validée avant refactor HTTP moderne (Javalin)

### Build & CI

- `publish-maven.yml` : publication Maven Central
- Build and Release workflow push sur branche `release/oculix`
- Suppression du legacy SikuliX ide-snapshot workflow
- `.mailmap` : remap ancien identifiant de commit
- `project-status-sync` Action : synchronise les labels `status:*` avec le Project v2 Status field

### Issues fermées

- **#87** Image captures: add Browse file option alongside screen capture
- **#138** API: remove vendored `org.opencv.*` bindings, rely on Apertix

### Contributeurs

- Julien Mer (@julienmerconsulting) — fork maintainer
- Raimund Hocke (@RaiMan) — créateur original de SikuliX1

---

## OculiX 3.0.0 - Première release officielle

Fork actif de SikuliX1 (archivé le 3 mars 2026). Visual automation for the real world.

### Ajouts majeurs
- **VNC full stack** : VNCScreen, VNCRobot, VNCClient, VNCFrameBuffer, VNCClipboard, XKeySym (2200+ key definitions)
- **SSH natif** : SSHTunnel via jcraft/jsch embarqué, zéro dépendance externe
- **Android ADB** : ADBScreen, ADBRobot, ADBDevice, ADBClient, ADBTest via jadb embarqué
- **OCR multi-engine** : PaddleOCREngine (629 lignes), TesseractEngine, OCREngine pluggable
- **Multi-language runners** : Jython, JRuby, Python, PowerShell, AppleScript, Robot Framework, Network, Server
- **VNC fixes** : Raw encoding, ZRLE/Tight corruption fix, SetPixelFormat negotiation, VncAuth only

### Dépendances
- **Apertix 4.10.0-0** : remplacement d'openpnp/opencv 4.5.4 par OpenCV 4.10.0 compilé from scratch (Windows x86-64)
- JNA migration complète (compatible JDK 16+, zéro --add-opens)

### Build
- Java 17+, Maven multimodule
- Artifacts : **oculixapi-3.0.0.jar** + **oculixide-3.0.0.jar**

### Base
- Fork de RaiMan/SikuliX1 @ MIT License
