---
title: Visual matching
description: Region, Pattern, Match — the core of OculiX visual recognition.
---

Visual matching is the heart of OculiX. You give it a small image, it tells you where on the current screen that image appears. Everything else — clicking, typing, waiting — is built on top of that primitive.

## Core classes

| Class       | What it represents                                                                            |
| ----------- | --------------------------------------------------------------------------------------------- |
| **`Screen`**| A physical monitor. `Screen(0)` is the primary. Multi-monitor: `Screen(1)`, `Screen(2)`, … |
| **`Region`**| Any rectangular area you can search in. `Screen` is a `Region` covering the whole display.    |
| **`Pattern`**| An image plus matching parameters (similarity, targetOffset).                                 |
| **`Match`** | The result of a successful find — a `Region` plus a similarity score and the original pattern.|
| **`Location`**| A single point (x, y).                                                                       |

You can use `Pattern` everywhere `Region` expects an image, and vice-versa — the API is overloaded for both.

## The five verbs

Every visual operation is one of these:

```python
find(image)          # locate once, throw FindFailed if missing
exists(image)        # locate once, return None if missing
wait(image, secs)    # poll until it appears or timeout
waitVanish(image, s) # poll until it disappears or timeout
findAll(image)       # locate every occurrence (iterator)
```

All five accept a path (`"button.png"`), a `Pattern`, or another `Region`.

## A worked example

```python
from sikuli import *

s = Screen(0)                     # primary monitor

# Wait up to 10 s for the app to appear
app = s.wait("main_window.png", 10)

# Restrict search to the right pane only — faster, less ambiguous
right_pane = app.right(app.getW() // 2)
save = right_pane.find("save_button.png")

save.highlight(1.5)               # flash a red box on screen for 1.5 s
save.click()
```

`s.wait()` returns the matching `Region`. `right_pane.find()` returns a `Match` whose `.click()` does what you expect.

## Similarity

Default similarity is **0.7** (70 %). You can override per call or globally:

```python
# Per call
click(Pattern("button.png").similar(0.85))

# Per script
Settings.MinSimilarity = 0.65
```

Higher = stricter, lower = more permissive. Anti-aliased text, transparency, theme changes, and font hinting all reduce match scores — relax similarity rather than re-capture the image each time.

## Target offset

By default OculiX clicks the **center** of a match. To click somewhere else relative to the image:

```python
# Click 30 px to the right and 5 px down from the center of the icon
icon = Pattern("icon.png").targetOffset(30, 5)
click(icon)
```

This is the canonical way to click a label that sits next to a recognizable icon.

## Region operations

A `Region` knows where it is and how to chop itself up:

```python
r = Screen(0)

r.left(200)           # 200-px-wide strip on the left
r.right(200)          # 200-px-wide strip on the right
r.above(100)          # 100-px strip above r
r.below(100)          # 100-px strip below r
r.inside()            # the region itself
r.nearby(50)          # a 50-px-larger version
r.grow(50, 100)       # asymmetric grow
r.morphTo(otherRegion)# resize/move to match another region
```

Combined with `find()` these make scripts dramatically more robust:

```python
# Don't search the whole screen — only inside the dialog
dialog = wait("save_dialog_title.png", 5)
dialog.click("save_button.png")    # search constrained to dialog
```

## Anchors — define a region relative to something you can find

```python
header = find("header_logo.png")
search_box = header.right(800).below(0)   # 800-px-wide region to the right
search_box.click()
search_box.type("oculix")
```

When the layout shifts but the relative position is stable, anchors keep your script working.

## findAll — every occurrence

```python
# Iterate over every match
for m in findAll("checkbox_unchecked.png"):
    m.click()
```

`findAll()` returns an iterator of `Match` objects, sorted by similarity score (highest first by default).

## Highlight — debug visually

```python
match = find("save_button.png")
match.highlight(2)         # red box for 2 s
match.highlight("green")   # named colors
match.highlight(2, "yellow")
```

Great for figuring out *why* your script clicked somewhere unexpected — `highlight()` shows you exactly what OculiX found.

## Slow Motion mode

From the IDE: **Run → Run Slow Motion**. Each match flashes for half a second before the action fires. You see exactly what OculiX sees, in order.

## Settings that affect matching

```python
Settings.MinSimilarity = 0.7        # default similarity floor
Settings.AlwaysResize = 1.0         # scale captures by N before matching
Settings.MoveMouseDelay = 0.3       # seconds to glide the cursor
Settings.WaitScanRate = 3           # find() scans per second during wait()
Settings.ObserveScanRate = 3        # observer scan rate
Settings.AutoWaitTimeout = 3.0      # implicit wait before every action
```

`AutoWaitTimeout` is particularly useful: if it's > 0, `click(image)` *implicitly waits* for the image before clicking, so you rarely need explicit `wait()` calls.

## Performance tips

- **Smaller patterns match faster.** Tight crops > wide screenshots.
- **Restrict to a `Region`** instead of searching the whole `Screen`.
- **Cache `Match` objects** when the layout is stable — `target.click()` is free; `find()` costs CPU.
- **Use `exists()` for branching**, not `find()` — `exists()` returns `None` instead of throwing.

## Common errors

| Error                | Typical cause                                                                  |
| -------------------- | ------------------------------------------------------------------------------ |
| `FindFailed`         | Image not visible, hidden behind another window, similarity too strict         |
| `ImageMissing`       | Path is wrong, image not in the `.sikuli` bundle                               |
| `org.opencv.core…`   | Apertix mismatch — see [Installation](/getting-started/installation/)          |

## Next

- [Read text on screen with OCR](/guides/ocr/) — when you need text, not pixels
- [The vision pipeline](/guides/vision-pipeline/) — under the hood: Apertix, template matching, OpenCV
- [API reference](/reference/api/) — every method, every parameter
