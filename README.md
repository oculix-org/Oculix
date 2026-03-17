<div align="center">

<img src="https://raw.githubusercontent.com/RaiMan/SikuliX1/master/Support/sikulix-red.png" width="120" alt="OculiX"/>

# OculiX

**Visual automation for the real world.**

[![Java](https://img.shields.io/badge/Java-11%2B-orange?style=flat-square&logo=openjdk)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)](LICENSE)
[![Fork of](https://img.shields.io/badge/Fork%20of-SikuliX1%20%28archived%29-lightgrey?style=flat-square)](https://github.com/RaiMan/SikuliX1)
[![Maintained](https://img.shields.io/badge/Maintained-Yes-brightgreen?style=flat-square)](https://github.com/julienmerconsulting/OculiX)
[![Changes](https://img.shields.io/badge/Changes%20vs%20SikuliX-123%2C728%20insertions-blue?style=flat-square)]()

<br/>

*From the Latin **oculi** — eyes.*  
*Because if you can see it, you can automate it.*

<br/>

**Desktops &nbsp;·&nbsp; POS terminals &nbsp;·&nbsp; Kiosks &nbsp;·&nbsp; SCO machines &nbsp;·&nbsp; Android devices &nbsp;·&nbsp; VNC remote systems &nbsp;·&nbsp; Legacy apps**

<br/>

> **SikuliX1 was archived on March 3, 2026. OculiX is its active continuation.**  
> 511 files changed. 123,728 insertions. Built for production — not for demos.

</div>

---

## ⚡ What OculiX adds over SikuliX

<table>
<tr>
<td width="50%">

### 🖥️ Full VNC stack
Connect to any remote machine — no local display required.  
`VNCScreen`, `VNCRobot`, `VNCClient`, `VNCFrameBuffer`, `VNCClipboard`.  
Complete keyboard mapping via `XKeySym` (2200+ key definitions).  
Thread-safe parallel sessions via `ThreadLocalSecurityClient`.

</td>
<td width="50%">

### 🔐 Native SSH — zero external deps
`jcraft/jsch` is embedded directly in the JAR.  
Open SSH tunnels from Java. No WSL. No shell wrapper. No sshpass.  
`SSHTunnel` available as a standalone utility in `com.sikulix.util`.

</td>
</tr>
<tr>
<td width="50%">

### 📱 Android automation via ADB
Full ADB integration: `ADBClient`, `ADBDevice`, `ADBRobot`, `ADBScreen`.  
Control Android devices with the same API as desktop screens.  
Powered by `jadb` — embedded, no external tools required.

</td>
<td width="50%">

### 🏃 Multi-language script runners
Run automation scripts in: **Jython, JRuby, Python, PowerShell, AppleScript, Robot Framework**.  
Network runner for distributed execution.  
Server runner for headless CI/CD integration.

</td>
</tr>
<tr>
<td width="50%">

### 🔧 Native OS libraries — bundled
**Windows:** `WinUtil.dll` + `WinUtil.cc` — native Windows interaction, precompiled and bundled.  
**macOS:** `MacUtil.m` — native macOS support.  
**Linux:** `LinuxSupport.java` — 421 lines of Linux-specific handling.  
No manual native lib setup on any platform.

</td>
<td width="50%">

### 🏗️ Production build system
Platform-specific `makeapi` scripts for Windows, macOS, Linux.  
TigerVNC deployed as a proper Maven dependency.  
`maven-deploy-API` for reproducible CI builds.  
IntelliJ run configurations included out of the box.

</td>
</tr>
</table>

---

## 🚀 Getting started

**Prerequisite: Java 11+**  
→ [Eclipse Temurin](https://adoptium.net) &nbsp;|&nbsp; [Azul Zulu](https://www.azul.com/downloads/?package=jdk#download-openjdk)

**Build from source:**

```bash
git clone https://github.com/julienmerconsulting/OculiX.git
cd OculiX
mvn clean install -DskipTests
```

**Maven (local install):**

```xml
<dependency>
  <groupId>com.oculix</groupId>
  <artifactId>oculixapi</artifactId>
  <version>2.1.0</version>
</dependency>
```

---

## 🖥️ VNC — automate remote machines

```java
// Connexion directe à une machine distante via VNC
VNCScreen vnc = VNCScreen.start("192.168.1.10", 5900, "", 1920, 1080);
vnc.click("validate_button.png");
vnc.type("1234");
vnc.waitVanish("loading_spinner.png", 10);
vnc.stop();
```

---

## 🔐 SSH tunnel — from Java, no shell

```java
// Tunnel SSH natif — jcraft/jsch embarqué
SSHTunnel tunnel = new SSHTunnel("user", "remote-host", 22, "password");
tunnel.open(5900, "localhost", 5900);

// Puis connexion VNC sur le port local tunnelé
VNCScreen vnc = VNCScreen.start("localhost", 5900, "", 1920, 1080);
```

---

## 📱 Android — same API, different device

```java
// Contrôle d'un device Android via ADB
ADBScreen android = ADBScreen.get();
android.click("accept_button.png");
android.type("hello");
android.waitForImage("home_screen.png", 10);
```

---

## 🏃 Script runners — run anything

```java
// Jython
Runner.run("myscript.py", new String[]{});

// PowerShell
Runner.run("myscript.ps1", new String[]{});

// Robot Framework
Runner.run("test.robot", new String[]{});
```

---

## 📦 What's inside

| Component | Status | Details |
|---|---|---|
| Core API — Screen, Region, Pattern, Match | ✅ Active | Extended from SikuliX 2.0.5 |
| VNCScreen + VNCRobot + VNCClient | ✅ Active | 1000+ lines reworked, production-tested |
| VNCFrameBuffer + VNCClipboard + XKeySym | ✅ Active | Full remote control stack |
| SSH tunnel — jcraft/jsch | ✅ Embedded | No external SSH dependency |
| Android ADB — ADBScreen + jadb | ✅ Active | Full device control |
| Script runners — Jython, JRuby, Python, PS, Robot | ✅ Active | Multi-language automation |
| Native libs — Windows DLL + macOS + Linux | ✅ Bundled | No manual setup |
| TigerVNC Maven dependency | ✅ Active | Reproducible builds |
| SikuliX IDE | 🔧 Maintained | No new features planned |
| OculiX simplified API | 🔬 In design | Contributions welcome |

---

## 🗺️ Roadmap

- [ ] **OculiX simplified API** — single entry point, zero internal knowledge required
- [ ] **Fat JAR** — one file, no manual dependency setup
- [ ] **Maven Central** publication
- [ ] Python wrapper

---

## 🤝 Contributing

Issues, bug reports, pull requests — all welcome.

OculiX targets production QA engineers and automation specialists working on environments without API access — desktops, kiosks, POS terminals, legacy software, Android devices.

If you were using SikuliX and looking for a continuation — **you found it.**

- **Bug reports / feature requests** → [Issues](../../issues)
- **Bug fixes** → Pull request against `master`
- **Major changes** → Open an issue first

---

## 👤 Maintainer

<table>
<tr>
<td width="60">
<img src="https://avatars.githubusercontent.com/julienmerconsulting" width="56" style="border-radius:50%"/>
</td>
<td>

**Julien MER** — QA Architect · 20+ years in defense, biotech, aerospace, retail  
Katalon Top Partner Europe &nbsp;|&nbsp; JMer Consulting  
Newsletter [Bonnes Pratiques QA](https://bonnespratiqueqa.fr) — 7000+ subscribers

</td>
</tr>
</table>

---

## 📄 License

MIT — same as the original SikuliX project.

Original project: [RaiMan/SikuliX1](https://github.com/RaiMan/SikuliX1) (archived March 2026)  
Original author: Raimund Hocke — all credits for the foundational work.
