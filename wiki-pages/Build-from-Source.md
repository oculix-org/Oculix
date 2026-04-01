# Build from Source

---

## Prerequisites

| Requirement | Version |
|-------------|---------|
| **Java JDK** | 17+ (Eclipse Temurin recommended) |
| **Maven** | 3.8+ |
| **Git** | any |

## Clone and Build

```bash
git clone https://github.com/oculix-org/Oculix.git
cd Oculix
mvn clean package -DskipTests
```

## Build Outputs

```
API/target/oculixapi-3.0.1.jar            ← API library
IDE/target/oculixide-3.0.1.jar            ← IDE (thin JAR)
IDE/target/oculixide-3.0.1-complete.jar   ← Fat JAR (all platforms)
```

## Platform-Specific Fat JARs

```bash
# Windows only (smaller)
mvn clean package -DskipTests -Pcomplete-win-jar

# macOS only
mvn clean package -DskipTests -Pcomplete-mac-jar

# Linux only
mvn clean package -DskipTests -Pcomplete-lux-jar
```

## Run

```bash
# Launch IDE
java -jar IDE/target/oculixide-3.0.1-complete-win.jar

# Run script headless
java -jar IDE/target/oculixide-3.0.1-complete-win.jar -r script.py
```

## Project Structure

```
Oculix/
├── API/                    ← Core library (sikuli API, OpenCV, VNC, ADB, OCR)
│   ├── src/main/java/
│   │   ├── org/sikuli/     ← SikuliX core API
│   │   ├── org/opencv/     ← OpenCV Java bindings
│   │   ├── com/sikulix/    ← OculiX additions (VNC, OCR, SSH)
│   │   ├── com/tigervnc/   ← Vendored TigerVNC
│   │   ├── com/jcraft/     ← Vendored JSch
│   │   └── se/vidstige/    ← Vendored JADB
│   └── pom.xml
├── IDE/                    ← GUI IDE
│   ├── src/main/java/
│   │   └── org/sikuli/ide/ ← IDE classes (SikulixIDE, IDEMenuManager, etc.)
│   └── pom.xml
├── .github/workflows/      ← CI/CD
└── pom.xml                 ← Parent POM
```

## Troubleshooting

| Error | Solution |
|-------|----------|
| `UnsupportedClassVersionError` | Use Java 17+, not Java 11 or 8 |
| `UnsatisfiedLinkError: opencv_java*` | Check PR #12 is merged (Core.java version alignment) |
| Maven deps not found | Apertix is on Maven Central, check network access |
