# OculiX — Enterprise Compliance & Security Audit

> **Audit date:** 2026-04-01  
> **Version audited:** 3.0.1  
> **Repository:** `oculix-org/Oculix`  
> **Methodology:** Static source code analysis, dependency inventory, CI/CD configuration review, CVE database cross-reference  
> **Auditor:** Automated analysis (Claude Code)

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [License Audit](#1-license-audit)
3. [Security Audit](#2-security-audit)
4. [Maintenance & Project Health](#3-maintenance--project-health)
5. [CI/CD Audit](#4-cicd-audit)
6. [Final Verdict](#5-final-verdict)

---

## Executive Summary

OculiX 3.0.1 is a fork of the archived SikuliX1 project, providing visual automation capabilities via screen capture, OCR, and scripting. The project is MIT-licensed and actively maintained. However, this audit identifies **significant security risks** in outdated dependencies (particularly `jackson-databind` and `undertow-core`), **no automated test suite in CI/CD**, and several code-level concerns around temporary file handling and external process execution. These issues are addressable but require immediate attention for enterprise deployment.

---

## 1. License Audit

### 1.1 Project License

| Item | Value |
|------|-------|
| Project license | MIT |
| Copyright holder | Raimund Hocke (2018), maintained by oculix-org |
| License file present | Yes (`/LICENSE`) |
| SPDX identifier | MIT |

**Status: OK** — MIT is permissive and enterprise-friendly.

### 1.2 Dependency License Inventory

#### API Module (`oculixapi`)

| Dependency | Version | License | Enterprise Compatible | Notes |
|------------|---------|---------|----------------------|-------|
| org.python:jython-slim | 2.7.2 | PSF License v2 | Yes | Python Software Foundation License |
| org.jruby:jruby-complete | 9.2.11.1 | EPL-2.0 / GPL-2.0 / LGPL-2.1 | **Attention** | Triple-licensed; EPL-2.0 is enterprise-safe but GPL-2.0 requires care |
| org.json:json | 20251224 | JSON License | **Attention** | Contains "shall be used for Good, not Evil" clause |
| javax.xml.bind:jaxb-api | 2.3.1 | CDDL 1.1 / GPL-2.0 w/ CPE | Yes | Classpath Exception makes it safe |
| io.github.julienmerconsulting.apertix:opencv | 4.10.0-0 | Apache-2.0 | Yes | Custom build wrapping OpenCV |
| commons-cli:commons-cli | 1.4 | Apache-2.0 | Yes | |
| org.apache.commons:commons-exec | 1.3 | Apache-2.0 | Yes | |
| net.java.dev.jna:jna-platform | 5.14.0 | Apache-2.0 / LGPL-2.1 | Yes | Dual-licensed |
| net.oneandone.reflections8:reflections8 | 0.11.6 | WTFPL / Apache-2.0 | Yes | |
| net.sourceforge.tess4j:tess4j | 4.5.4 | Apache-2.0 | Yes | JNA wrapper for Tesseract |
| org.slf4j:slf4j-nop | 1.7.28 | MIT | Yes | |
| net.sf.py4j:py4j | 0.10.9.1 | BSD-3-Clause | Yes | |
| com.1stleg:jnativehook | 2.1.0 | LGPL-3.0 | **Attention** | LGPL requires dynamic linking awareness |
| org.rococoa:rococoa-core | 0.5 | LGPL-3.0 | **Attention** | macOS-only; LGPL requires compliance |
| cglib:cglib-nodep | 2.2.2 | Apache-2.0 | Yes | |
| org.apache.commons:commons-lang3 | 3.12.0 | Apache-2.0 | Yes | |

#### IDE Module (`oculixide`)

| Dependency | Version | License | Enterprise Compatible | Notes |
|------------|---------|---------|----------------------|-------|
| io.github.oculix-org:oculixapi | 3.0.1 | MIT | Yes | Internal dependency |
| com.explodingpixels:mac_widgets | 0.9.5 | LGPL-3.0 | **Attention** | macOS UI widgets |
| org.swinglabs:swing-layout | 1.0.3 | LGPL-2.1 | **Attention** | Swing layout library |
| com.fasterxml.jackson.core:jackson-core | 2.9.10 | Apache-2.0 | Yes | |
| com.fasterxml.jackson.core:jackson-databind | 2.9.10.1 | Apache-2.0 | Yes | |
| io.undertow:undertow-core | 2.0.27.Final | Apache-2.0 | Yes | |
| commons-io:commons-io | 2.8.0 | Apache-2.0 | Yes | |

#### Vendored (Source-Embedded) Libraries

| Library | Location | License | Enterprise Compatible | Notes |
|---------|----------|---------|----------------------|-------|
| JSch (SSH) | `API/src/main/java/com/jcraft/jsch/` (135 files) | BSD-style | Yes | Full SSH implementation embedded |
| TigerVNC (Java) | `API/src/main/java/com/tigervnc/` | GPL-2.0+ | **Risk Identified** | GPL code embedded directly in MIT project |

### 1.3 License Conflicts

| Issue | Severity | Details |
|-------|----------|---------|
| TigerVNC (GPL-2.0+) embedded in MIT project | **Risk Identified** | TigerVNC Java viewer source is GPL-2.0+. Embedding GPL source code in an MIT-licensed project creates a license incompatibility. The combined work must comply with GPL-2.0+ terms, which conflicts with the MIT license claim. This needs legal review. |
| org.json "Good, not Evil" clause | **Attention** | The JSON.org license contains a non-free clause ("shall be used for Good, not Evil"). Some organizations (e.g., Debian, FSF) consider this non-free. Enterprise legal teams may flag this. |
| JRuby triple-license (includes GPL-2.0) | **Attention** | JRuby is triple-licensed (EPL-2.0/GPL-2.0/LGPL-2.1). Enterprise use should explicitly select EPL-2.0 or LGPL-2.1 to avoid GPL obligations. |
| LGPL dependencies (jnativehook, rococoa, mac_widgets, swing-layout) | **Attention** | LGPL-licensed libraries are used. Enterprise deployments must ensure dynamic linking or provide re-linking capability per LGPL terms. Fat JAR packaging may complicate LGPL compliance. |

---

## 2. Security Audit

### 2.1 Known CVEs in Dependencies

#### CRITICAL — Immediate Action Required

| Dependency | Version | CVE(s) | Severity | Fixed In | Status |
|------------|---------|--------|----------|----------|--------|
| jackson-databind | 2.9.10.1 | CVE-2019-20330, CVE-2020-8840, CVE-2020-9546/9547/9548, CVE-2020-10672/10673, CVE-2020-10968/10969, CVE-2020-11111/11112/11113, CVE-2020-14060/14061/14062, CVE-2020-24616, CVE-2020-24750, CVE-2020-25649, CVE-2020-35490/35491, CVE-2020-36179 through CVE-2020-36190, CVE-2021-20190 | HIGH to CRITICAL | 2.12.x+ | **Risk Identified** |
| undertow-core | 2.0.27.Final | CVE-2020-1757, CVE-2020-10687, CVE-2021-3597, CVE-2021-3690, CVE-2024-3884, CVE-2024-4109, CVE-2025-12543, CVE-2026-28368, CVE-2026-28369 | HIGH to CRITICAL (up to CVSS 9.6) | 2.3.x+ | **Risk Identified** |

#### HIGH — Should Be Addressed

| Dependency | Version | CVE(s) | Severity | Fixed In | Status |
|------------|---------|--------|----------|----------|--------|
| commons-io | 2.8.0 | CVE-2024-47554 | HIGH | 2.14.0 | **Risk Identified** |
| jython-slim | 2.7.2 | CVE-2013-2027, transitive Bouncy Castle CVEs | MEDIUM-HIGH | 2.7.4 | **Attention** |
| jruby-complete | 9.2.11.1 | Inherited Ruby/RubyGems CVEs, outdated Bouncy Castle | MEDIUM | 9.4.x | **Attention** |

#### LOW / NO DIRECT CVE

| Dependency | Version | Status | Notes |
|------------|---------|--------|-------|
| tess4j | 4.5.4 | OK (direct) | No direct CVEs; transitive dependency CVEs exist |
| jna-platform | 5.14.0 | OK | Current version |
| commons-cli | 1.4 | OK | No known CVEs |
| commons-exec | 1.3 | OK | No known CVEs |
| slf4j-nop | 1.7.28 | OK | No known CVEs |
| py4j | 0.10.9.1 | OK | No known CVEs |
| commons-lang3 | 3.12.0 | OK | No known CVEs for this version |
| jaxb-api | 2.3.1 | OK | No known CVEs |
| json (org.json) | 20251224 | OK | Recent version |

#### Vendored Libraries

| Library | Status | Notes |
|---------|--------|-------|
| JSch (embedded) | **Attention** | Vendored version; cannot be independently updated. Original JSch project is unmaintained. No version tracking possible. |
| TigerVNC (embedded) | **Attention** | Vendored version; cannot be independently updated. No version tracking. SSH tunnel code handles key material. |

### 2.2 Screenshot & Temporary File Handling

| Finding | Location | Severity | Status |
|---------|----------|----------|--------|
| Screenshots saved to temp directory unencrypted | `Recorder.java:175` | MEDIUM | **Attention** |
| `deleteOnExit()` used for cleanup (unreliable) | `FileManager.java:511`, `Recorder.java:176` | MEDIUM | **Attention** |
| Temp files created for script execution (PowerShell, AppleScript) | `PowershellRunner.java:21`, `AppleScriptRunner.java:26` | MEDIUM | **Attention** |
| Screenshots written as PNG files in IDE capture | `ButtonCapture.java:144-148` | LOW | OK |
| No encryption of any temp files at rest | Multiple locations | MEDIUM | **Attention** |

**Details:**
- The `Recorder` class creates a temp directory via `Files.createTempDirectory("sikulix")` and marks it with `deleteOnExit()`. This means screenshots remain on disk until JVM termination — or indefinitely if the JVM crashes.
- `FileManager.createTempFile()` uses the same `deleteOnExit()` pattern.
- No file permission restrictions (e.g., `PosixFilePermissions`) are applied to temp files.
- In an enterprise context, unencrypted screenshots in world-readable temp directories may leak sensitive screen content.

### 2.3 Network Connections

| Finding | Location | Direction | Status |
|---------|----------|-----------|--------|
| Full SSH client implementation (JSch) | `API/src/main/java/com/jcraft/jsch/` (135 files) | Outbound | **Attention** |
| VNC client (RFB protocol) | `API/src/main/java/com/tigervnc/` | Outbound | **Attention** |
| SSH tunnel creation for VNC | `Tunnel.java:39-200` | Outbound | **Attention** |
| HTTP/SOCKS proxy support | `ProxyHTTP.java`, `ProxySOCKS4.java`, `ProxySOCKS5.java` | Outbound | **Attention** |
| ADB connections (Android) | `ADBDevice.java` | Outbound | **Attention** |
| Undertow web server in IDE | `oculixide` dependency | Inbound (localhost) | **Attention** |
| PaddleOCR client | `PaddleOCRClient.java` | Outbound | **Attention** |

**Assessment:** OculiX is a desktop automation tool that intentionally connects to remote systems (VNC, SSH, ADB). These are core features, not vulnerabilities. However, enterprise firewalls and security policies should account for these outbound connections. The embedded Undertow server in the IDE module opens a local HTTP listener.

### 2.4 Sensitive Data in Logs

| Finding | Location | Severity | Status |
|---------|----------|----------|--------|
| All user preferences logged at DEBUG level | `UserPreferences.java:85,97` | MEDIUM | **Attention** |
| JSch operations logged through bridge logger | `Tunnel.java:83-100` | LOW | OK |
| SSH key password handled in memory | `Tunnel.java:155-178` | LOW | OK (not logged) |

**Assessment:** The `UserPreferences` debug logging dumps all stored preference key-value pairs without filtering. If SSH passwords, API keys, or other credentials are stored in preferences, they would appear in DEBUG-level logs. The SSH key handling itself is done correctly (passwords in byte arrays, not logged).

### 2.5 External Process Execution

| Finding | Location | Severity | Status |
|---------|----------|----------|--------|
| ProcessBuilder with dynamic arguments | `ProcessRunner.java:46-267` | HIGH | **Attention** |
| Glob pattern expansion in command args | `ProcessRunner.java:58-76` | MEDIUM | **Attention** |
| ADB command execution | `ADBDevice.java:296` | MEDIUM | **Attention** |
| OS-level process execution | `GenericOsUtil.java:101` | MEDIUM | **Attention** |
| Runtime.exec for SikuliX scripts | `SikulixRun.java:118-121` | MEDIUM | **Attention** |

**Assessment:** Process execution is a core feature of the automation tool. However, `ProcessRunner` constructs commands dynamically with glob expansion, which could be a command injection vector if user-controlled input reaches these paths without sanitization. Enterprise deployments should ensure script inputs are validated.

### 2.6 Cryptographic Material Handling

| Finding | Location | Severity | Status |
|---------|----------|----------|--------|
| SSH private keys written to filesystem | `KeyPair.java:202-264` | HIGH | **Attention** |
| X.509 certificates saved as PEM | `CSecurityTLS.java:298-337` | MEDIUM | **Attention** |
| SSH known_hosts file access | `Tunnel.java:151` | LOW | OK |
| No file permission enforcement on key files | Multiple locations | MEDIUM | **Attention** |

---

## 3. Maintenance & Project Health

### 3.1 Repository Status

| Metric | Value | Status |
|--------|-------|--------|
| Current version | 3.0.1 | OK |
| License file | Present (MIT) | OK |
| Security policy | Present (`SECURITY.md`) | OK |
| README | Present (detailed) | OK |
| Release notes | Present (`RELEASE_NOTES.md`) | OK |
| Last commit activity | Active (recent commits visible) | OK |
| Issue templates | Feature request template configured | OK |
| Branch protection | Not verified | **Not Verified** |

### 3.2 Relationship with SikuliX1

| Fact | Source | Status |
|------|--------|--------|
| OculiX is a fork of SikuliX1 | README.md, commit history | Verified |
| SikuliX1 was archived on 2026-03-03 | README.md states this | **Attention** |
| Fork contains 123,728 insertions from SikuliX1 | README.md | Verified |
| Original copyright: Raimund Hocke (2018) | LICENSE file | Verified |
| Current maintainer: Julien Mer (oculix-org) | pom.xml, commits | Verified |
| Vendored code (JSch, TigerVNC) inherited from SikuliX1 | Source analysis | Verified |

**Assessment:** OculiX is the continuation of SikuliX1 after the original project was archived. The fork inherits both the capabilities and the technical debt of the original project. The vendored libraries (JSch, TigerVNC) were already embedded in SikuliX1 and have not been updated to standalone dependencies, making security patching difficult.

### 3.3 Code Quality Observations

| Observation | Status |
|-------------|--------|
| Java 17 compilation target | OK |
| Maven multimodule structure | OK |
| UTF-8 encoding enforced | OK |
| No static analysis tooling configured (SpotBugs, PMD, Checkstyle) | **Attention** |
| No dependency vulnerability scanning (OWASP Dependency-Check) | **Risk Identified** |
| No SBOM (Software Bill of Materials) generation | **Attention** |
| Package naming still uses `org.sikuli` (not `org.oculix`) | **Attention** |

---

## 4. CI/CD Audit

### 4.1 GitHub Actions Workflows

| Workflow | File | Function | Tests Run | Status |
|----------|------|----------|-----------|--------|
| API Compile | `api-compile.yml` | Compile API module | No (`-DskipTests`) | **Attention** |
| API Snapshot | `api-snapshot.yml` | Build API snapshot | No (`-DskipTests`) | **Attention** |
| IDE Compile | `ide-compile.yml` | Compile IDE module | No (`-DskipTests`) | **Attention** |
| IDE Snapshot | `ide-snapshot.yml` | Multi-platform IDE builds | No (`-DskipTests`) | **Attention** |
| Release | `release.yml` | Cross-platform fat JARs + GitHub Release | No (`-DskipTests`) | **Attention** |
| macOS Launch Test | `test-macos-launch.yml` | macOS-specific smoke test | Partial | OK |
| Maven Publish | `plublish-maven.yml` | Maven Central publishing | No | **Attention** |
| Eval | `eval.yml` | Evaluation workflow | Unknown | **Not Verified** |

**Critical finding:** ALL build and release workflows use `-DskipTests`. There is **no automated test execution** in CI/CD.

### 4.2 Headless Capabilities

| Capability | Headless Support | Status |
|------------|-----------------|--------|
| VNC-based automation (`VNCScreen`) | Yes — fully headless | OK |
| Android automation (`ADBScreen`) | Yes — fully headless | OK |
| Desktop automation (`Screen`) | **No** — throws `SikuliXception` in headless | **Attention** |
| IDE (`SikulixIDE`) | **No** — requires display | Expected |
| Maven build / compile | Yes | OK |
| Headless detection | Yes — `GraphicsEnvironment.isHeadless()` | OK |
| Xvfb / virtual display setup | Not configured in any workflow | **Attention** |

**Assessment:** The architecture correctly supports headless operation via VNC and ADB backends. Desktop automation (`Screen` class) explicitly fails in headless mode with a clear error. This is architecturally sound — the VNC path is the intended CI/CD integration point. However, no workflow sets up Xvfb for desktop-mode testing, and no automated tests validate any of these paths.

### 4.3 Test Infrastructure

| Item | Status | Details |
|------|--------|---------|
| Unit test suite | **Not Present** | No JUnit/TestNG dependencies in pom.xml |
| Integration tests | **Not Present** | No `src/test/` directories with automated tests |
| Manual test file | Present | `ADBTest.java` (manual Android testing) |
| Script-based test runners | Present | Jython test runners in IDE resources |
| Test coverage tooling | Not configured | No JaCoCo or similar |
| Docker/container support | Not present | No Dockerfile |

**Verdict:** The project has **zero automated tests** in its CI/CD pipeline. This is the most significant enterprise readiness gap.

### 4.4 Build Reproducibility

| Item | Status |
|------|--------|
| Maven wrapper (`mvnw`) | Not present |
| Dependency lock file | Not present (Maven does not natively support this) |
| JDK version pinned in CI | Yes (JDK 17 via `setup-java@v4`) |
| `.java-version` file | Present (says `11`, but CI uses `17`) | **Attention** |
| Build uses `-B` (batch mode) | Yes | OK |

**Note:** The `.java-version` file specifies Java 11, but all pom.xml files and CI workflows target Java 17. This inconsistency could cause confusion.

---

## 5. Final Verdict

### Summary by Category

| Category | Verdict | Key Issues |
|----------|---------|------------|
| **Licensing** | **Attention Required** | TigerVNC GPL-2.0+ embedded in MIT project is a license conflict. org.json "Good, not Evil" clause. Multiple LGPL dependencies in fat JARs. |
| **Security — Dependencies** | **Risk Identified** | `jackson-databind 2.9.10.1` has 20+ known CVEs (CRITICAL). `undertow-core 2.0.27.Final` has 10+ CVEs including CVSS 9.6. Immediate upgrades required. |
| **Security — Code** | **Attention Required** | Unencrypted temp files, unreliable cleanup via `deleteOnExit()`, dynamic process execution, no file permission enforcement on key material. |
| **Security — Network** | **OK (by design)** | Network connections are core features (VNC, SSH, ADB). Enterprise firewall policies should account for these. |
| **Maintenance** | **OK** | Active fork of archived SikuliX1. Recent commits, security policy in place, proper release workflow. |
| **CI/CD** | **Risk Identified** | Zero automated tests. All workflows skip tests. No static analysis, no dependency scanning, no SBOM. |
| **Headless / Enterprise Deployment** | **OK (via VNC/ADB)** | Headless works correctly via VNC and ADB backends. Desktop mode correctly refuses headless. |

### Priority Actions for Enterprise Readiness

1. **CRITICAL — Upgrade `jackson-databind`** from 2.9.10.1 to 2.17.x+ (20+ known CVEs, multiple CRITICAL)
2. **CRITICAL — Upgrade `undertow-core`** from 2.0.27.Final to 2.3.x+ (CVSS 9.6 vulnerabilities)
3. **HIGH — Resolve TigerVNC GPL license conflict** — either re-license the project, extract TigerVNC as a separate module, or replace with a compatible VNC implementation
4. **HIGH — Add automated tests** and remove `-DskipTests` from CI workflows
5. **HIGH — Upgrade `commons-io`** from 2.8.0 to 2.14.0+
6. **MEDIUM — Add OWASP Dependency-Check** to CI pipeline for continuous CVE monitoring
7. **MEDIUM — Upgrade `jython-slim`** to 2.7.4 and `jruby-complete` to 9.4.x
8. **MEDIUM — Replace vendored JSch** with maintained fork (e.g., `com.github.mwiede:jsch`)
9. **LOW — Fix `.java-version`** file to match actual Java 17 target
10. **LOW — Add SBOM generation** (e.g., CycloneDX Maven plugin)

### Disclaimer

This report is based on static analysis of the source code and dependency declarations as of 2026-04-01. It does not include runtime penetration testing, dynamic analysis, or legal review. CVE data was cross-referenced with NVD, Snyk, and CVE Details databases. License classifications are based on commonly accepted interpretations and do not constitute legal advice. Organizations should conduct their own legal review for license compliance.
