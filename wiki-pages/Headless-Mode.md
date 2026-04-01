# Headless Mode (-r)

![Fix](https://img.shields.io/badge/type-fix-red?style=for-the-badge)
![PR %2312](https://img.shields.io/badge/PR-%2312-blue?style=for-the-badge)

> Running scripts from the command line (`-r` mode) without the IDE GUI now works reliably.

---

## The Problem

```bash
java -jar oculixide-3.0.1-complete-win.jar -r script.py
# → UnsatisfiedLinkError: 'long org.opencv.core.Mat.n_Mat()'
```

In SikuliX and early OculiX, the `-r` mode crashed because:
1. OpenCV native library was never loaded (name mismatch + silent failure)
2. `SikulixIDE.showAfterStart()` was called even without a GUI
3. `resetBeforeScriptStart()` threw errors in non-IDE context

## The Fixes

### OpenCV Loading (PR #12)

| Problem | Fix |
|---------|-----|
| `Core.NATIVE_LIBRARY_NAME` returned `opencv_java454` but DLL is `opencv_java4100` | Updated `Core.java` to match Apertix 4.10.0 |
| `loadOpenCV()` set `libOpenCVloaded = true` even on failure | Only `true` on success, with 3-stage fallback |
| Apertix `nu.pattern.OpenCV.loadLocally()` never called | Now the primary loading method |

### JythonRunner Guard

```java
// Before: always called, crashed in -r mode
SikulixIDE.showAfterStart();

// After: only in IDE mode
if (!Commons.hasOption(RUN)) {
    SikulixIDE.showAfterStart();
}
```

### JythonSupport Safety

```python
# Before: crashed if resetBeforeScriptStart() unavailable
resetBeforeScriptStart()

# After: safe fallback
try:
  resetBeforeScriptStart()
except:
  pass
```

## Startup Flow Comparison

### IDE Mode (normal)
```
main() → initOptions → SikulixIDE.start()
  → ... IDE initialization ...
  → Finder2 static {} → Commons.loadOpenCV()  (lazy, on first find)
```

### -r Mode (headless)
```
main() → initOptions → Commons.hasOption(RUN)
  → Commons.loadOpenCV()         ← explicit, early
  → Runner.runScripts(scripts)   ← execute and exit
  → RunTime.terminate(exitCode)
```

## Usage

```bash
# Run a Jython script headless
java -jar oculixide-3.0.1-complete-win.jar -r path/to/script.py

# With arguments passed to the script
java -jar oculixide-3.0.1-complete-win.jar -r script.py -- arg1 arg2
```
