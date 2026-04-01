# Android ADB

![Enhanced](https://img.shields.io/badge/type-enhanced-blue?style=for-the-badge)
![Android 12+](https://img.shields.io/badge/Android-12+-green?style=for-the-badge&logo=android)

> Android support existed in SikuliX1 as experimental. OculiX made it production-ready with Android 12+ fixes, WiFi ADB pairing, and vendored JADB.

---

## What was in SikuliX1

- Basic ADB classes (experimental)
- Relied on external `adb` binary
- Broken on Android 12+ (display dimension parsing, screencap buffer)

## What OculiX changed

### Android 12+ Compatibility (commit `0ba1dd0`)

| Problem | Fix |
|---------|-----|
| `wm size` output format changed in Android 12 | Updated display dimension parser |
| `screencap` raw buffer format changed | Fixed buffer reading and color format |
| OpenCV not loaded for ADB operations | Added `Commons.loadOpenCV()` at ADB init |

### New Features

| Feature | Description |
|---------|-------------|
| `isDeviceConnected()` | Device detection via `adb shell getprop` |
| WiFi ADB pairing | Support for wireless debugging (Android 12+) |
| Better error handling | Detailed debug output for connection failures |
| SSH tunnel integration | ADB over SSH via `SSHTunnel` |

### Vendored JADB

The `se.vidstige.jadb.*` package (30+ files) is embedded — no need for external `adb` binary:
- `AdbServer`, `AdbConnection`, `AdbDevice`
- Sync protocol for file push/pull
- Port forwarding

### Architecture

```
ADBScreen       → Screen abstraction (capture, bounds)
  └── ADBRobot  → Touch/swipe/keyboard via ADB
  └── ADBDevice → Device management, screencap
  └── ADBClient → ADB protocol (via JADB)
        └── SSHTunnel (optional) → Remote ADB over SSH
```

### Validated Configurations

- Samsung devices, 1080x2400, Android 12+
- WiFi ADB pairing
- ADB over SSH tunnel to remote servers
