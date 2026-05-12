---
title: Your first script
description: Open an app, find a button by image, click it. The OculiX hello world.
---

This guide walks you through the canonical OculiX workflow: **see → match → act**. By the end, you'll have a script that finds a button on your screen by its image and clicks it.

## 1. Capture what you want to click

1. Launch the OculiX IDE.
2. Click **New Script** (or *File → New*). The IDE creates a `.sikuli` bundle — a folder that holds your script and its images.
3. Position your target on screen — for this example, open your file manager and pick any button visible (e.g. the **Save** button in a text editor).
4. In the IDE toolbar, click the **Take Screenshot** camera icon. The screen freezes, your cursor turns into crosshairs.
5. Drag a tight rectangle around your target. Release.

OculiX inserts the captured image into the script as a clickable thumbnail:

```python
click("save_button.png")
```

The image is saved inside your `.sikuli` folder. You can rename, replace, or reuse it across scripts.

## 2. Run it

Press **▶ Run** (or *Cmd/Ctrl + R*). OculiX:

1. Takes a fresh screenshot of the current screen.
2. Looks for `save_button.png` anywhere on it.
3. Moves the mouse to the match and clicks.

If it finds nothing, you'll see `FindFailed` in the console — usually because the target moved, was hidden, or your similarity threshold is too strict.

## 3. Build something useful

Here's a one-screen script that exports a daily report:

```python
from sikuli import *

# Open the app via its taskbar/dock icon (image captured beforehand)
click("app_icon.png")
wait("app_main_window.png", 10)   # wait up to 10 s for the main window

# Walk through the export menu
click("file_menu.png")
click("export_to_csv.png")
wait("save_dialog.png", 5)

# Type the file name in the focused field
type("filename_field.png", "report_" + getDate() + ".csv")
click("save_button.png")

# Wait for the confirmation toast before closing
waitVanish("loading_spinner.png", 30)
popup("Done!")
```

Three things to notice:

- **No selectors.** OculiX never asked you what the button's CSS class or accessibility ID is.
- **`wait()` and `waitVanish()`** are how you sync with the application: wait for something to appear (or disappear) before continuing.
- **Standard Python.** The `+ getDate()` part is plain Python — anything you can do in Jython (Python 2.7 syntax, full JVM access) you can do here.

## 4. Tune similarity when the match is too strict

By default OculiX requires 70 % similarity. For UIs with anti-aliasing, transparency, or theme changes, you may want to loosen that:

```python
click(Pattern("save_button.png").similar(0.65))
```

Or globally for the whole script:

```python
Settings.MinSimilarity = 0.65
```

## 5. Save and re-run

Hit **Save**. Your script bundle (`my-export.sikuli`) is a regular folder you can:

- Version-control with Git
- Share with a teammate (zip the folder, that's it)
- Schedule with cron / Task Scheduler (`java -jar oculixide.jar -l my-export.sikuli -e`)

## What to read next

- [Visual matching in depth](/guides/visual-matching/) — `Region`, `Pattern`, `Match`, similarity, anchors
- [Read text on screen with OCR](/guides/ocr/) — find a button by its label, not its image
- [Tour the IDE](/getting-started/ide-tour/) — Workspace, Modern Recorder, Welcome tab
