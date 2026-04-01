# Architecture Overview

---

## Module Structure

```
Oculix (parent POM)
├── API (oculixapi)     ← Core library, all logic
└── IDE (oculixide)     ← GUI IDE, thin layer over API
```

## API Package Map

```
org.sikuli.script       ← Core API: Screen, Region, Finder, Image, Pattern, Match
org.sikuli.support      ← Infrastructure: Commons, FileManager, RunTime
org.sikuli.android      ← ADB: ADBDevice, ADBScreen, ADBRobot, ADBClient
org.sikuli.vnc          ← VNC: VNCScreen, VNCRobot, XKeySym
org.sikuli.basics       ← Settings, Debug, PreferencesUser

com.sikulix.vnc         ← VNC protocol: VNCClient, VNCFrameBuffer, VNCClipboard
com.sikulix.ocr         ← OCR engines: PaddleOCR, Tesseract, TextNormalizer
com.sikulix.util        ← Utilities: SSHTunnel, SikuliLogger

com.tigervnc.*          ← Vendored TigerVNC (RFB protocol, decoders, TLS)
com.jcraft.jsch.*       ← Vendored JSch (SSH protocol, tunneling)
se.vidstige.jadb.*      ← Vendored JADB (ADB protocol)
org.opencv.*            ← OpenCV Java bindings (via Apertix 4.10.0)
```

## Image Matching Flow

```
User script: find("button.png")
  │
  ├── Region.find(target)
  │     └── Region.doFind()
  │           └── new Finder(region)
  │                 └── Finder2.find(findInput)
  │                       └── doFindImage()
  │                             ├── Mode 1: Exact matchTemplate
  │                             ├── Mode 2: DPI-aware resize
  │                             ├── Mode 3: GaussianBlur tolerant
  │                             ├── Mode 4: Grayscale smart
  │                             └── Mode 5: Multi-scale brute-force
  │
  └── Returns Match (x, y, w, h, score)
```

## OpenCV Loading Flow

```
Commons.loadOpenCV()
  │
  ├── Stage 1: nu.pattern.OpenCV.loadLocally()     ← Apertix/JNA
  ├── Stage 2: System.loadLibrary("opencv_java4100") ← java.library.path
  └── Stage 3: Extract DLL from JAR → System.load()  ← manual fallback
```

## Screen Capture Flow

```
Screen.capture(rect)
  └── ScreenDevice.capture(rect)
        └── Robot.createScreenCapture(rect)     ← java.awt.Robot
              └── BufferedImage (raw pixels)
                    └── FileManager.writePngWithDpi(img, file)
                          └── PNG with pHYs chunk (DPI metadata)
```

## Startup Paths

### IDE Mode
```
main() → initOptions → splash → SikulixIDE.start()
  → JythonRunner.doInit() → SikulixIDE.showAfterStart()
  → OpenCV loaded lazily on first find()
```

### Headless Mode (-r)
```
main() → initOptions → Commons.hasOption(RUN)
  → Commons.loadOpenCV()        ← explicit, early
  → Runner.runScripts(scripts)
  → RunTime.terminate(exitCode)
```

### Server Mode
```
main() → initOptions → Commons.hasOption(SERVER)
  → SikulixServer.run()
  → RunTime.terminate()
```
