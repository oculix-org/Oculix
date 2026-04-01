# Differences from SikuliX

> Complete list of changes between OculiX 3.0.1 and the original SikuliX1 (archived March 2026).

---

## Feature Additions

| Feature | Description | Files |
|---------|-------------|-------|
| **VNC Full Stack** | Existing SikuliX VNC upgraded to production-grade. Vendored TigerVNC (100+ files). ZRLE/Tight corruption fixes. Raw encoding optimization. Thread-safe parallel sessions. | `org.sikuli.vnc.*`, `com.sikulix.vnc.*`, `com.tigervnc.*` |
| **SSH Tunnel** | Embedded JSch library (100+ files). Port forwarding, key-based auth, auto-reconnect. Compatible with legacy servers (SUSE 12). | `com.sikulix.util.SSHTunnel`, `com.jcraft.jsch.*` |
| **Android/ADB** | Full ADB stack with JADB vendored (30+ files). Screen capture, touch/swipe, keyboard. Android 12+ WiFi ADB pairing. | `org.sikuli.android.*`, `se.vidstige.jadb.*` |
| **Pluggable OCR** | PaddleOCR (HTTP, zero-dep client) + Tesseract fallback. Auto-detection. Amount variant generator for monetary formats. | `com.sikulix.ocr.*` |
| **DPI-Aware Pipeline** | 5-mode cascade matching for cross-resolution. PNG metadata injection. Multi-scale brute-force fallback. | `Finder.java`, `FileManager.java`, `Image.java` |
| **Headless Mode Fixes** | `-r` mode works reliably. JythonRunner/JythonSupport guards. OpenCV loading fixed for non-GUI. | `JythonRunner.java`, `JythonSupport.java`, `Sikulix.java` |

---

## Infrastructure Changes

| Area | SikuliX1 | OculiX |
|------|----------|--------|
| **OpenCV** | `org.openpnp:opencv:4.5.4` | `io.github.julienmerconsulting.apertix:opencv:4.10.0-0` |
| **Native loading** | JNI via `System.loadLibrary` | JNA via `nu.pattern.OpenCV.loadLocally()` with 3-stage fallback |
| **Java version** | Source/target 1.8 | Source/target 17 |
| **CI platform** | Travis CI | GitHub Actions |
| **CI Java** | AdoptOpenJDK 11 | Eclipse Temurin 17 |
| **Actions** | checkout@v2, setup-java@v2 | checkout@v4, setup-java@v4 |
| **Fat JAR build** | Single platform | 6 platform-specific JARs in parallel |
| **Build var** | `TRAVIS_BUILD_NUMBER` | `GITHUB_RUN_NUMBER` |

---

## Dependency Upgrades

| Dependency | SikuliX1 | OculiX | Reason |
|------------|----------|--------|--------|
| `jna-platform` | 5.6.0 | 5.14.0 | glibc + Apple Silicon compat |
| `jython-standalone` | 2.7.2 | 2.7.4 | Bug fixes |
| `jackson-databind` | 2.9.10 | 2.18.3 | Security CVEs |
| `undertow-core` | 2.0.27.Final | 2.3.18.Final | Security CVEs |
| `commons-io` | 2.8.0 | 2.18.0 | Bug fixes |
| `json` | older | 20251224 | Latest |
| `maven-compiler-plugin` | 3.8.1 | 3.14.0 | Java 17 support |
| `maven-assembly-plugin` | 3.1.1 | 3.7.1 | — |
| `maven-jar-plugin` | 3.1.2 | 3.4.2 | — |
| `maven-gpg-plugin` | 1.6 | 3.2.8 | — |
| `maven-javadoc-plugin` | 3.1.1 | 3.12.0 | — |

---

## Removed Modules

| Module | Reason |
|--------|--------|
| **Jygments** | Syntax highlighting, unused |
| **Libslux** | Linux screen utilities, obsolete |
| **Tesseract (module)** | Moved to external dependency via Tess4J |

---

## Vendored Libraries

OculiX embeds these libraries directly in the JAR (no external dependencies needed):

| Library | Package | Files | Purpose |
|---------|---------|-------|---------|
| **TigerVNC** | `com.tigervnc.*` | 100+ | RFB protocol, decoders, TLS |
| **JSch** | `com.jcraft.jsch.*` | 100+ | SSH protocol, tunneling |
| **JADB** | `se.vidstige.jadb.*` | 30+ | ADB protocol |
| **OpenCV bindings** | `org.opencv.*` | 50+ | Java bindings for Apertix |

---

## IDE Refactoring

| Extracted Class | Responsibility | From |
|----------------|----------------|------|
| `IDEMenuManager` | File/Edit/Run/View/Tool/Help menus + actions | `SikulixIDE.java` |
| `IDEWindowManager` | Window lifecycle, positioning | `SikulixIDE.java` |
| `IDEFileManager` | File operations, recent files | `SikulixIDE.java` |
| `IDERunManager` | Script execution, abort | `SikulixIDE.java` |
| `PaneContext` | Editor tab state (was inner class) | `SikulixIDE.java` |

---

## Artifact Naming

| Component | SikuliX1 | OculiX |
|-----------|----------|--------|
| API JAR | `sikulixapi-*.jar` | `oculixapi-*.jar` |
| IDE JAR | `sikulixide-*.jar` | `oculixide-*.jar` |
| Project display name | SikuliX | OculiX |
| Version | 2.1.0 | 3.0.1 |
