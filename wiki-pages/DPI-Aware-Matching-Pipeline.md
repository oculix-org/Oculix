# DPI-Aware Matching Pipeline

![New](https://img.shields.io/badge/type-new%20feature-brightgreen?style=for-the-badge)
![PR %2312](https://img.shields.io/badge/PR-%2312-blue?style=for-the-badge)

> 5-mode cascade matching pipeline for cross-resolution robustness. Handles Windows display scaling changes (100% → 150%, etc.) transparently.

---

## The Problem

A template image captured at 100% Windows scaling (96 DPI) will **not match** when the script runs at 150% scaling (144 DPI) — the template is smaller than the on-screen element. SikuliX had zero DPI awareness.

## The Solution

OculiX adds a 5-mode cascade pipeline in `Finder2.doFindImage()`. Each mode only runs if all previous modes failed:

```
find() called
  │
  ├── Mode 1: Exact match (original threshold)
  │     └── Standard matchTemplate — works when resolution matches
  │
  ├── Mode 2: DPI-aware resize (original threshold)
  │     └── Reads DPI from PNG metadata or Image.captureDpi
  │     └── Calculates ratio (e.g. 144/96 = 1.5)
  │     └── Resizes template before matching
  │
  ├── Mode 3: Tolerant match (threshold × 0.90)
  │     └── GaussianBlur(3,3) on both template and screen
  │     └── Absorbs minor rendering differences
  │
  ├── Mode 4: Smart match (threshold × 0.85)
  │     └── Grayscale conversion before matching
  │     └── Handles color shifts, theme changes
  │
  └── Mode 5: Multi-scale brute-force (threshold × 0.90)
        └── Tries 7 common ratios: 1.25, 1.5, 1.75, 2.0, 0.8, 0.67, 0.5
        └── Last resort when DPI metadata is unavailable
```

## How DPI Metadata Works

### Writing (at capture time)

Every PNG saved by OculiX now includes a **pHYs chunk** with the system DPI:

```
FileManager.writePngWithDpi(img, file)
  → Reads system DPI via GraphicsDevice.getDefaultTransform().getScaleX()
  → Converts to pixels-per-meter (DPI × 39.3701)
  → Writes pHYs chunk in PNG metadata
```

**7 write points** updated: `ScreenImage.storeImage()`, `ScreenImage.saveLastScreenImage()`, `ScreenImage.saveInto()`, `FileManager.saveTimedImage()`, `Image.save(String, String)`, `Image.save(File)`, `ButtonCapture.update()`

### Reading (at match time)

`getDpiRatio()` uses a 3-level fallback:

| Priority | Source | When it works |
|----------|--------|---------------|
| 1 | `Image.getCaptureDpi()` | In-memory, same session capture |
| 2 | PNG pHYs metadata | Images saved with OculiX |
| 3 | Assume 96 DPI | Legacy templates (SikuliX era) |

### System DPI Detection

`getSystemDpi()` uses the modern Java API:

```java
GraphicsDevice.getDefaultConfiguration().getDefaultTransform().getScaleX()
// Returns: 1.0 (100%), 1.25 (125%), 1.5 (150%), 2.0 (200%)
// Converted to DPI: 96, 120, 144, 192
```

Fallback to `Toolkit.getDefaultToolkit().getScreenResolution()` if unavailable.

## Scoring Fix

> **Bug fixed in PR #12:** Modes 3, 4, and 5 compute a reduced threshold for the initial match check, but `FindResult2.hasNext()` was comparing against the **original** threshold, rejecting valid matches.

**Fix:** `findInput.setSimilarity(reducedThreshold)` is called before creating `FindResult2`, so `hasNext()` uses the correct gate.

## Files Changed

| File | Change |
|------|--------|
| `Finder.java` | Pipeline cascade (modes 2-5), `getDpiRatio()` fallback, `getSystemDpi()` via GraphicsDevice, scoring fix |
| `FileManager.java` | New `writePngWithDpi()` method |
| `Image.java` | `captureDpi` field, `injectSystemDpi()` in ScreenImage constructors |
| `ScreenImage.java` | All saves use `writePngWithDpi()` |
| `ButtonCapture.java` | IDE capture uses `writePngWithDpi()` |

## Example Scenario

```
1. User captures template at 100% scaling (96 DPI)
   → PNG saved with pHYs: 3780 pixels/meter (96 DPI)

2. User switches Windows to 150% scaling (144 DPI)

3. Script runs find("template.png")
   → Mode 1: exact match → 97.3% < 99% threshold → FAIL
   → Mode 2: reads template DPI=96, screen DPI=144
             ratio = 144/96 = 1.5
             resizes template × 1.5
             match → 99.8% > 99% → SUCCESS ✅
```
