---
title: OCR & text recognition
description: Two OCR engines in OculiX — Tesseract (embedded) and PaddleOCR (opt-in HTTP server).
---

OculiX gives you two complementary OCR engines, with very different trade-offs:

| Engine     | When to use it                                                       | Setup     |
| ---------- | -------------------------------------------------------------------- | --------- |
| **Tesseract** | Latin scripts, simple layouts, no extra process                   | Bundled, zero install |
| **PaddleOCR** | CJK, mixed scripts, complex layouts, high accuracy required       | Optional HTTP server |

You can use both in the same script. Pick per-call based on what you're reading.

## Tesseract — bundled via Legerix

Tesseract is **fully embedded** in OculiX through [Legerix](https://github.com/oculix-org/Legerix). The Maven coordinate `io.github.oculix-org:legerix:5.5.0-4` brings the native binaries *and* the `tessdata` traineddata files into the JAR. Nothing for you to install.

**Languages bundled out of the box:**

| Code        | Language              |
| ----------- | --------------------- |
| `eng`       | English               |
| `fra`       | French                |
| `spa`       | Spanish               |
| `chi_sim`   | Simplified Chinese    |
| `hin`       | Hindi                 |
| `osd`       | Orientation & Script Detection (needed for auto-rotation) |

### Reading text — high-level API

```python
from sikuli import *

# Read the whole screen
print Screen(0).text()
# → "File  Edit  View  ...   Save   Cancel"

# Read inside a region
btn_area = Region(100, 200, 300, 50)
print btn_area.text()
```

`Region.text()` runs Tesseract on the captured region and returns the recognized text. The bounding region is automatically sharpened — for the best accuracy, capture a tight crop around the text you care about.

### Find a button by its label

```python
# Locate the "Submit" label on screen, click on it
target = Region(0, 0, 1920, 1080).findText("Submit")
target.click()
```

`findText(label)` returns a `Match` you can click, hover, type into, or chain with other region ops. Use it when the button's **image** changes (theme switch, dynamic icon) but its **label** is stable.

### Switch language per call

```python
Settings.OcrLanguage = "fra"
print Region(...).text()          # reads as French

Settings.OcrLanguage = "chi_sim"
print Region(...).text()          # reads as Simplified Chinese
```

Or temporarily:

```python
with Settings.use(OcrLanguage="chi_sim"):
    print btn_area.text()
```

### Tune the engine

Tesseract exposes Page Segmentation Modes (PSM) and OCR Engine Modes (OEM):

```python
Settings.OcrPSM = 7    # single uniform block of text (best for single labels)
Settings.OcrPSM = 11   # sparse text — find as much as possible
Settings.OcrOEM = 3    # default — neural + legacy
```

PSM `7` is the sweet spot for matching button labels — `11` is better when reading paragraphs.

## PaddleOCR — optional HTTP server

For CJK languages, vertical text, handwritten text, or noisy layouts, **PaddleOCR** outperforms Tesseract by a wide margin. It runs as a small Python HTTP server that OculiX talks to over localhost.

### Install the server

```bash
pip install paddleocrserver-powered
paddleocrserver
# Listens on http://localhost:5000
```

The server is published on PyPI as [`paddleocrserver-powered`](https://pypi.org/project/paddleocrserver-powered/) and runs on Flask + Waitress, single-binary, GPU-optional.

### Use it from a script

```python
from sikuli import *
from org.sikuli.script import PaddleOCREngine

ocr = PaddleOCREngine()   # auto-detects http://localhost:5000

# Capture the region, run OCR
img = capture(Region(0, 0, 1920, 1080))
json = ocr.recognize(img.getFile())

# Find coordinates of a text on screen
coords = ocr.findTextCoordinates(json, "Validate")
if coords:
    x, y, w, h = coords[0], coords[1], coords[2], coords[3]
    click(Location(x + w//2, y + h//2))
```

### Inspect everything PaddleOCR sees

```python
results = ocr.parseTextWithConfidence(json)
for text, confidence in results.items():
    print "%-30s  %.2f" % (text, confidence)
# Submit                          0.9997
# Cancel                          0.9981
# 你好                            0.9962
# ...
```

### When to prefer PaddleOCR

- **CJK** — Chinese, Japanese, Korean. PaddleOCR was trained for them.
- **Mixed scripts** in the same image (Latin + Asian)
- **Vertical text** (Japanese, traditional Chinese)
- **High accuracy needed** on small fonts, anti-aliased text, or noisy backgrounds

## Fallback chain (internals, FYI)

`Commons.loadTesseract()` runs a 4-level fallback when initializing the OCR stack:

1. **Legerix native binaries** (bundled) — first attempt
2. **Tess4J + Legerix tessdata** — second attempt if native loader fails
3. **System `tesseract` binary** — third attempt if the JVM can't load JNI
4. **`SikuliXception("Reinstall OculiX")`** — give up

In practice you'll never hit anything beyond step 1, but if you do see this in your logs, it's almost always a corrupted install — reinstall or rebuild from source.

## Performance tips

- **Crop tight.** Tesseract works on what you give it — a small focused region runs 10x faster than the whole screen.
- **Cache the engine.** `PaddleOCREngine ocr = new PaddleOCREngine()` once per script, not per call.
- **Use `findText()` over `text()` + string parsing** when you want to *click* a specific word — it's a single round-trip.
- **Don't OCR what you can match.** A PNG match is always cheaper than OCR. Use OCR for dynamic labels.

## Common pitfalls

| Symptom                                            | Likely cause                                |
| -------------------------------------------------- | ------------------------------------------- |
| Numbers come back as letters (`O` instead of `0`)  | PSM not set to single-line; relax similarity |
| Chinese returns Latin characters                   | `Settings.OcrLanguage` is still `"eng"`      |
| PaddleOCR returns empty results                    | Server not running on `localhost:5000`       |
| `Connection refused`                               | Install `paddleocrserver-powered` and start it |

## Next

- [Vision pipeline internals](/guides/vision-pipeline/) — how OculiX sees
- [Jython scripting in depth](/guides/jython/) — using OCR inside scripts
- [API reference](/reference/api/) — `Region.text()`, `Region.findText()`, `PaddleOCREngine`
