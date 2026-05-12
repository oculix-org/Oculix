---
title: IDE tour
description: A guided tour of the OculiX IDE — Workspace, Script Explorer, Modern Recorder, Welcome tab.
---

The OculiX IDE is the visible face of the project. It's where you record, edit, and run scripts. This page walks through every panel and what it does.

## Launch

```bash
java -jar oculixide-3.0.3.jar
```

Or double-click the JAR. On first launch you land on the **Welcome tab**.

## Layout

```
┌─────────────────────────────────────────────────────────────────┐
│  ◧  Workspace ▾   File   Edit   Run   View   Tools   Help       │  ← Menu bar
├─────────────────────────────────────────────────────────────────┤
│  📁 Workspace      │  🦎 Welcome  ×  │  my_script.py  ×          │
│  ┌───────────┐     │                                              │
│  │ script_1  │     │   # Script editor                            │
│  │ script_2  │     │   from sikuli import *                       │
│  │ script_3  │     │   click("button.png")                        │
│  └───────────┘     │   ...                                        │
│                    │                                              │
│  ℹ️ Project info   │                                              │
│  Status: idle      │                                              │
│  Last run: 14:32   │                                              │
├─────────────────────────────────────────────────────────────────┤
│  Console ▾                                                       │  ← Live log
│  [info] Script started                                           │
│  [info] click(button.png) at (450, 220) sim=0.98                 │
│  [info] Script ended in 1.3 s                                    │
└─────────────────────────────────────────────────────────────────┘
```

## Workspace

A **workspace** is a directory that holds your scripts. OculiX remembers the last workspace and re-opens it on launch.

- **File → New Workspace…** creates an empty workspace.
- **File → Open Workspace…** points OculiX at an existing folder of `.sikuli` bundles.
- **File → Rename Workspace…** renames it on disk and updates the cards.
- The left panel auto-refreshes when you create, rename, or delete a script from the file system.

Each script is shown as a **card** with its name, last-modified date, and a small icon. Click to open.

## Script editor

The center pane is a standard Python/Jython editor with:

- Syntax highlighting (theme-aware, dark and light)
- Image thumbnails inline — every `click("foo.png")` shows the captured image at the cursor
- Click a thumbnail to re-capture or replace the image
- **Run** (▶) executes the script. **Stop** (■) aborts.
- **Shift + Alt + C** kills any running script — even one stuck in a `while True` loop.

## Modern Recorder

The Recorder is the easiest way to build a script if you've never written one before. Open it from the toolbar.

Pick an action, then capture or browse for the image:

| Action       | What it does                                                              |
| ------------ | ------------------------------------------------------------------------- |
| **Click**    | Single click                                                              |
| **Dbl Click**| Double click                                                              |
| **R Click**  | Right click                                                               |
| **Wait**     | Wait for an image to appear before continuing                             |
| **Swipe**    | Mouse-drag from a captured anchor in a direction you pick                 |
| **DragDrop** | Drag from one captured image to another                                   |
| **Wheel**    | Scroll up/down a configurable amount over a captured target               |
| **Key Combo**| Send a keyboard combo (modal with checkboxes for Ctrl/Shift/Alt/Cmd)      |

The Recorder maintains an **image library** so you can reuse the same captures across actions without re-capturing each time. When you click **Insert & Close**, the images are copied into the script's `.sikuli` bundle and the corresponding Python lines are appended to your script.

## Welcome tab

On first launch (or when you close all editor tabs), OculiX opens a **Welcome tab** with:

- A starter snippet (capture-click-run in three lines)
- Quick links to the Workspace explorer and the Modern Recorder
- A "What's new in this version" panel reading from `CHANGELOG.md`

The Welcome tab handles missing-context cases safely (no NPE on empty workspace, no image-ratio glitches).

## Sidebar — live info panels

Below the Workspace list, three live panels:

- **Project** — current workspace path, total scripts, total images
- **Status** — `idle` / `running` / `error`, with the last error message
- **Last run** — timestamp + duration of the most recent execution

## Console

The bottom panel is a unified log:

- **info** for normal script output (`print` statements land here)
- **debug** for OculiX internals when `Settings.DebugLogs = True`
- **error** for stack traces and `FindFailed`
- Right-click → **Clear** / **Copy** / **Save log…**

The console is theme-aware: the colors switch with the IDE theme.

## Theme system

Open **View → Theme** to switch between dark and light. Both themes touch the editor, console, workspace, and Welcome tab consistently. The choice persists across launches.

## File menu — at a glance

| Item              | Shortcut       | What it does                              |
| ----------------- | -------------- | ----------------------------------------- |
| New Script        | Ctrl/Cmd + N   | Create a new `.sikuli` bundle             |
| Open Script…      | Ctrl/Cmd + O   | Open an existing bundle                   |
| New Workspace…    |                | Create an empty workspace directory       |
| Open Workspace…   |                | Open an existing workspace                |
| Rename Workspace… |                | Rename the current workspace on disk      |
| Save              | Ctrl/Cmd + S   | Save the current script                   |
| Save As…          |                | Save under a new name in the workspace    |
| Exit              | Ctrl/Cmd + Q   | Close the IDE (saves session)             |

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
