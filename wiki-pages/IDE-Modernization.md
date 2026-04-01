# IDE Modernization

![Refactor](https://img.shields.io/badge/type-refactor-cyan?style=for-the-badge)

> Breaking up the monolithic `SikulixIDE.java` (3000+ lines) into focused manager classes.

---

## The Problem

`SikulixIDE.java` was a God class handling:
- Window management
- All menus (File, Edit, Run, View, Tool, Help)
- All menu actions
- Editor tab management
- Script execution
- File operations
- Recent files tracking
- Undo/redo state

## What Was Extracted

| Class | Lines | Responsibility | PR |
|-------|-------|----------------|-----|
| `IDEMenuManager` | 1,078 | Menu creation, action classes, menu state | #6, #7 |
| `IDEWindowManager` | — | Window lifecycle, positioning | branch |
| `IDEFileManager` | — | File operations, recent files | branch |
| `IDERunManager` | — | Script execution, abort | branch |
| `PaneContext` | — | Editor tab state (was inner class) | branch |

## Architecture

```
Before:
  SikulixIDE ─── everything (3000+ lines)

After:
  SikulixIDE ─── window + tabs + coordination
      ├── IDEMenuManager ─── menus + actions
      ├── IDEWindowManager ─── window lifecycle
      ├── IDEFileManager ─── file operations
      └── IDERunManager ─── script execution
```

## Status

- `IDEMenuManager` is **merged** into master (#6, #7)
- Other managers are on **branch** `claude/modernize-oculix-ide-sgjuh`
- Java 17 modernization (var, try-with-resources, pattern matching) also on that branch
