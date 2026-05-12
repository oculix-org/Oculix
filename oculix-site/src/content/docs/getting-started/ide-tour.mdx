---
title: IDE tour
description: A guided tour of the OculiX IDE — Workspace, Script Explorer, Modern Recorder, Welcome tab.
---

import { Image } from 'astro:assets';
import splash from '../../../assets/screenshots/oculix-ide-splash.png';
import welcome from '../../../assets/screenshots/welcome.png';
import scriptOpen from '../../../assets/screenshots/script-open.png';
import recorder from '../../../assets/screenshots/recorder.png';

The OculiX IDE is the visible face of the project. It's where you record, edit, and run scripts. This page walks through every panel and what it does.

## Launch

```bash
java -jar oculixide-3.0.3.jar
```

Or double-click the JAR. On launch you briefly see the splash:

<Image src={splash} alt="OculiX IDE 3.0.3 splash screen with the gecko mascot — starting on Java 25" />

Then the main window opens on the **Welcome tab**.

## Layout

<Image src={welcome} alt="The full OculiX IDE on first launch — left sidebar with workspace and Script/Tools/Status/Last run/Help groups, Welcome tab in the center with the SikuliX quote and 'What OculiX adds' panel, bottom Message console, theme switcher" />

Five distinct zones:

| Zone           | Role                                                                                  |
| -------------- | ------------------------------------------------------------------------------------- |
| **Left sidebar** | Project info, Script / Tools menus, live Status panel, Last run, theme switch       |
| **Workspace**  | Tabs of open scripts, with file path in the title bar                                  |
| **Editor**     | The actual script editing area, with inline image thumbnails                           |
| **Message**    | The bottom console — debug / info / error logs                                         |
| **Status bar** | Version, Java version, OCR engine status, current cursor position                      |

## Welcome tab

On first launch (or when you close all editor tabs), OculiX opens the **Welcome tab**:

- A short pitch lifted from RaiMan's original SikuliX description — *"automates anything you see on the screen"*
- A **What OculiX adds** panel listing the project's distinctive additions (VNC remote screens, Modern Recorder, bundled OCR & OpenCV)
- Buttons for **New script** (Ctrl+N), **Open script** (Ctrl+O), **New workspace** (Ctrl+Shift+N), **Open workspace** (Ctrl+Shift+O)
- A footer with `v3.0.3`, `MIT`, `fork of SikuliX1`, and quick links to Docs, Release notes, and translation issue reporting

The Welcome tab handles missing-context cases safely (no NPE on empty workspace, no image-ratio glitches).

## Workspace

A **workspace** is a directory that holds your scripts. OculiX remembers the last workspace and re-opens it on launch.

- **File → New Workspace…** creates an empty workspace.
- **File → Open Workspace…** points OculiX at an existing folder of `.sikuli` bundles.
- **File → Rename Workspace…** renames it on disk and updates the cards.
- The workspace panel auto-refreshes when you create, rename, or delete a script from the file system.

Each script appears as a card with its name, image count, and status (`idle`, `running`, `error`). Click to open it.

## Script editor with inline image thumbnails

<Image src={scriptOpen} alt="OculiX IDE showing ExampleScript.py with two lines — img = (thumbnail of a red circle) and match = click(img). The image is rendered inline in the code." />

This is one of OculiX's signature features. Captured images live **inline in the code**:

```python
img = (thumbnail rendered here)
match = click(img)
```

When you click a thumbnail, the IDE lets you re-capture or replace it. The image file lives in the `.sikuli` bundle next to the script — you can rename, version, or share it like any other asset.

The editor supports:

- Syntax highlighting (theme-aware, dark and light)
- Inline image thumbnails for every captured image reference
- `Cmd/Ctrl + R` to run
- `Cmd/Ctrl + S` to save
- **Shift + Alt + C** to kill any running script — even one stuck in a `while True` loop

## Sidebar — live info panels

The left sidebar is more than a menu. It surfaces live information about the current state:

### Project block

The project block shows the **current script name**, its **path** (truncated to fit), and quick stats — image count and runtime status (`idle` / `running` / `error`).

### Script / Tools / Help menus

Three flat dropdown menus:

- **Script** — File, Edit, Run
- **Tools** — Modern Recorder, OCR settings, VNC connect, ADB connect
- **Help** — Welcome tab, About, Open docs

### Status panel

Real-time engine status:

- **PaddleOCR** — `offline` / `online`. Green dot when the localhost:5000 server responds.
- **Tesseract** — `built-in`. Always green (it ships with the JAR).
- **Java** — the JVM version currently running OculiX.

### Last run

Time, duration, and exit status of the most recent script execution. `— Not run yet` before the first run.

### Theme switcher

A pill toggle at the bottom: **DARK** / **LIGHT**. Choice persists across launches.

## Modern Recorder

The Recorder is the easiest way to build a script if you've never written one before. Open it from **Tools → Modern Recorder**.

<Image src={recorder} alt="The OculiX Modern Recorder modal with sections for Application (Launch App / Close App), Image actions (Click, DblClick, RClick, Drag&Drop, Swipe, Wheel, Wait), Text actions (T.Click, T.Wait, T.Exists), Keyboard (Type, Key Combo, Pause), a Generated Code preview, and Insert & Close / Clear buttons." />

The Recorder is organized in five sections:

| Section          | Buttons                                                  |
| ---------------- | -------------------------------------------------------- |
| **Application**  | Launch App · Close App · Scope actions to this app       |
| **Image actions**| Click · DblClick · RClick · Drag&Drop · Swipe · Wheel · Wait |
| **Text actions** | T.Click · T.Wait · T.Exists (OCR-driven)                 |
| **Keyboard**     | Type · Key Combo · Pause                                  |
| **Generated code** | Live preview of the Python lines being built          |

You pick a button, capture or browse for the image (for image actions) or type the text (for text actions), and the corresponding line is appended to the **Generated code** box. When you click **Insert & Close**, the images are copied into the active `.sikuli` bundle and the generated lines are inserted at the cursor in the editor.

The Recorder also maintains an **image library** so you can reuse the same capture across actions without re-capturing every time.

## Message console

The bottom panel is a unified log:

- **info** for normal script output (`print` statements land here)
- **debug** for OculiX internals when `Settings.DebugLogs = True`
- **error** for stack traces and `FindFailed`
- Startup logs include parsed CLI flags, JVM version, and Jython version
- Right-click → **Clear** / **Copy** / **Save log…**

The console is theme-aware: the colors switch with the IDE theme.

## File menu — at a glance

| Item              | Shortcut             | What it does                              |
| ----------------- | -------------------- | ----------------------------------------- |
| New Script        | Ctrl/Cmd + N         | Create a new `.sikuli` bundle             |
| Open Script…      | Ctrl/Cmd + O         | Open an existing bundle                   |
| New Workspace…    | Ctrl/Cmd + Shift + N | Create an empty workspace directory       |
| Open Workspace…   | Ctrl/Cmd + Shift + O | Open an existing workspace                |
| Save              | Ctrl/Cmd + S         | Save the current script                   |
| Save As…          |                      | Save under a new name in the workspace    |
| Exit              | Ctrl/Cmd + Q         | Close the IDE (saves session)             |

## Run menu

- **Run** (▶) — execute the current script
- **Run Slow Motion** — visualize each match with a brief highlight before clicking
- **Stop** — stop the current script
- **Kill switch** (`Shift + Alt + C`) — emergency abort, available globally

## Recovery

If the IDE crashes mid-edit, your work isn't lost. OculiX writes an auto-save under `~/.OculiX/recovery/` every few seconds and restores it on next launch via the Welcome tab.

## What's next

- [Write your first script step by step](/getting-started/first-script/)
- [The full visual-matching guide](/guides/visual-matching/)
- [The CLI reference](/reference/cli/) — running scripts without the IDE

