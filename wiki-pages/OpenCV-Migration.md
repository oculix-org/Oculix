# OpenCV Migration: openpnp to Apertix

![Upgrade](https://img.shields.io/badge/type-upgrade-blue?style=for-the-badge)
![OpenCV 4.10.0](https://img.shields.io/badge/OpenCV-4.10.0-green?style=for-the-badge&logo=opencv)

> OculiX migrated from openpnp/opencv 4.5.4 (JNI) to Apertix 4.10.0-0 (JNA).

---

## Why Migrate

| Issue with openpnp 4.5.4 | Resolution with Apertix 4.10.0 |
|--------------------------|-------------------------------|
| OpenCV 4.5.4 is outdated (2021) | OpenCV 4.10.0 (2024) |
| JNI-based native loading (fragile) | JNA-based (robust, cross-platform) |
| `System.loadLibrary` conflicts | `nu.pattern.OpenCV.loadLocally()` |
| No Apple Silicon support | Apple Silicon via JNA |

## What is Apertix

[Apertix](https://github.com/julienmerconsulting/Apertix) is a custom fork of openpnp/opencv by the OculiX maintainer:
- OpenCV 4.10.0 with JNA migration
- Packaging under `nu.pattern.OpenCV`
- Published to Maven Central
- 24 PRs of build infrastructure work

## Maven Dependency

```xml
<!-- Before (SikuliX) -->
<dependency>
    <groupId>org.openpnp</groupId>
    <artifactId>opencv</artifactId>
    <version>4.5.4</version>
</dependency>

<!-- After (OculiX) -->
<dependency>
    <groupId>io.github.julienmerconsulting.apertix</groupId>
    <artifactId>opencv</artifactId>
    <version>4.10.0-0</version>
</dependency>
```

## Loading Mechanism

`Commons.loadOpenCV()` uses a 3-stage fallback:

```
Stage 1: nu.pattern.OpenCV.loadLocally()    ← Apertix (preferred)
    │ fail
Stage 2: System.loadLibrary("opencv_java4100")  ← java.library.path
    │ fail
Stage 3: Extract DLL from JAR → System.load()   ← manual extraction
    │ fail
    └── Log error (libOpenCVloaded stays false)
```

> **Critical fix (PR #12):** `libOpenCVloaded` is only set to `true` on actual success. Previously it was always `true`, masking load failures.

## Files Changed

| File | Change |
|------|--------|
| `Core.java` | `NATIVE_LIBRARY_NAME`: `opencv_java454` → `opencv_java4100` |
| `Commons.java` | `loadOpenCV()` rewritten with 3-stage fallback |
| `sikulixcontent` | Manifest aligned to `opencv_java4100` |
| `API/pom.xml` | Dependency changed to Apertix |

## Impact

- **Java 17 mandatory**: Apertix 4.10.0-0 is compiled with Java 17 (class file version 61.0)
- **JNA 5.14.0 required**: for glibc + Apple Silicon compatibility
