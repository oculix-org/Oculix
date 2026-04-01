# OculiX

![OculiX](https://img.shields.io/badge/OculiX-3.0.1-blue?style=for-the-badge)
![OpenCV](https://img.shields.io/badge/OpenCV-4.10.0-green?style=for-the-badge&logo=opencv)
![Java](https://img.shields.io/badge/Java-17+-orange?style=for-the-badge&logo=openjdk)
![License](https://img.shields.io/badge/license-MIT-lightgrey?style=for-the-badge)

> **Active fork of [SikuliX1](https://github.com/RaiMan/SikuliX1)** — visual automation via OpenCV, enhanced for modern enterprise environments.

OculiX picks up where SikuliX1 left off (archived March 2026) and adds production-grade capabilities for remote, mobile, and multi-resolution automation.

---

## What's different from SikuliX?

| Area | SikuliX1 | OculiX |
|------|----------|--------|
| **OpenCV** | openpnp 4.5.4 (JNI) | Apertix 4.10.0 (JNA) |
| **Java** | 8+ | 17+ |
| **VNC** | Basic/broken | Full stack with TigerVNC |
| **SSH** | None | Embedded JSch tunnel |
| **Android** | Experimental | Android 12+ production-ready |
| **OCR** | Tesseract only | PaddleOCR + Tesseract (pluggable) |
| **DPI** | None | 5-mode cascade pipeline |
| **Headless (-r)** | Broken in many cases | Fixed and tested |
| **CI** | Travis CI, Java 11 | GitHub Actions, Java 17 |

> **For SikuliX core documentation** (API, scripting, Region, Screen, Pattern, etc.), see [sikulix.github.io](https://sikulix.github.io). This wiki documents **what OculiX adds or changes**.

---

## Wiki Navigation

### Getting Started
- [[Build from Source]]
- [[Architecture Overview]]

### What Changed
- [[Differences from SikuliX]] — full comparison table
- [[Changelog]] — all PRs and releases

### Features
- [[VNC Full Stack]] — remote desktop automation
- [[SSH Tunnel]] — ADB over SSH
- [[Android ADB]] — mobile automation (Android 12+)
- [[OCR Engines]] — PaddleOCR + Tesseract
- [[DPI-Aware Matching Pipeline]] — multi-resolution support
- [[Headless Mode]] — running scripts with `-r`

### Infrastructure
- [[OpenCV Migration]] — openpnp to Apertix
- [[CI CD Infrastructure]] — GitHub Actions, Java 17
- [[IDE Modernization]] — refactoring the monolith
