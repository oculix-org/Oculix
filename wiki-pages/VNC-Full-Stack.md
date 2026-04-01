# VNC Full Stack

![Enhanced](https://img.shields.io/badge/type-enhanced-blue?style=for-the-badge)
![TigerVNC](https://img.shields.io/badge/TigerVNC-vendored-green?style=for-the-badge)

> VNC support existed in SikuliX1 but was incomplete and unstable. OculiX upgraded it to production-grade with vendored TigerVNC, corruption fixes, and multi-encoding support.

---

## What was in SikuliX1

- Basic VNC screen class (`VNCScreen`)
- Relied on external TigerVNC dependency (sikulix2tigervnc)
- Frequent image corruption issues
- Limited encoding support

## What OculiX changed

### Vendored TigerVNC
The entire `com.tigervnc.*` package (100+ Java files) is now embedded directly in the JAR — no external dependency needed.

Includes:
- **Decoders**: Raw, ZRLE, Tight, Hextile
- **Encryption**: TLS support, `SSLEngineManager`
- **Compression**: Zlib streams
- **Network**: `TcpSocket`, `TcpListener`

### Corruption Fixes

| Commit | Fix |
|--------|-----|
| `a22913e` | ZRLE/Tight corruption fix with `SetPixelFormat` negotiation |
| `fa6860c` | Disabled VeNCrypt/TLS, use VncAuth only (stability) |
| `b1b2e2f` | Raw encoding: refresh before capture, screen stability |
| `99e1c8a` | Disabled CPIXEL optimization in ZRLE/Tight (source of corruption) |

### Architecture

```
VNCScreen          → Screen abstraction (capture, bounds)
  └── VNCRobot     → Keyboard/mouse via RFB protocol
  └── VNCClient    → RFB protocol implementation (776 lines)
        └── VNCFrameBuffer  → Pixel buffer management
        └── VNCClipboard    → Clipboard sync
        └── TigerVNC decoders (Raw, ZRLE, Tight, Hextile)
```

### Key Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `VNCScreen` | `org.sikuli.vnc` | Screen interface for SikuliX API |
| `VNCRobot` | `org.sikuli.vnc` | Input events over VNC |
| `VNCClient` | `com.sikulix.vnc` | RFB protocol, 776 lines |
| `VNCFrameBuffer` | `com.sikulix.vnc` | Framebuffer management |
| `XKeySym` | `org.sikuli.vnc` | 2200+ international key definitions |
| `ThreadLocalSecurityClient` | `com.sikulix.vnc` | Thread-safe parallel sessions |

### Usage

```python
from sikuli import *
vnc = VNCScreen.start("192.168.1.100", 5900, "password")
vnc.find("button.png")
vnc.click("button.png")
vnc.stop()
```

> **Note**: A branch `claude/fix-vnc-corruption-WvspU` contains additional VNC fixes not yet merged.
