# CI/CD Infrastructure

![Upgrade](https://img.shields.io/badge/type-upgrade-blue?style=for-the-badge)
![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-v4-black?style=for-the-badge&logo=github-actions)
![Java 17](https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=openjdk)

> Modernized from Travis CI / Java 11 to GitHub Actions / Java 17 Temurin.

---

## Workflow Upgrades

| Component | SikuliX1 | OculiX |
|-----------|----------|--------|
| CI platform | Travis CI | GitHub Actions |
| `actions/checkout` | v2 | v4 |
| `actions/setup-java` | v2 | v4 |
| Java version | 11 | 17 |
| JDK distribution | AdoptOpenJDK (EOL) | Eclipse Temurin |
| Build number | `TRAVIS_BUILD_NUMBER` | `GITHUB_RUN_NUMBER` |

## Why Java 17 is Mandatory

Apertix `4.10.0-0` is compiled with Java 17 (class file version 61.0). Running it on Java 11 causes:
```
UnsupportedClassVersionError: io/github/julienmerconsulting/apertix/...
has been compiled by a more recent version of the Java Runtime (class file version 61.0)
```

## Build Profiles

OculiX builds **6 platform-specific fat JARs** in parallel:

| Profile | Output | Contains |
|---------|--------|----------|
| `complete-jar` | `oculixide-*-complete.jar` | All platforms |
| `complete-win-jar` | `oculixide-*-complete-win.jar` | Windows x86-64 |
| `complete-mac-jar` | `oculixide-*-complete-mac.jar` | macOS x86-64 |
| `complete-lux-jar` | `oculixide-*-complete-lux.jar` | Linux x86-64 |

## Maven Plugin Versions

| Plugin | SikuliX1 | OculiX |
|--------|----------|--------|
| `maven-compiler-plugin` | 3.8.1 | 3.14.0 |
| `maven-assembly-plugin` | 3.1.1 | 3.7.1 |
| `maven-jar-plugin` | 3.1.2 | 3.4.2 |
| `maven-gpg-plugin` | 1.6 | 3.2.8 |
| `maven-source-plugin` | 3.1.0 | 3.4.0 |
| `maven-javadoc-plugin` | 3.1.1 | 3.12.0 |
| `nexus-staging-maven-plugin` | 1.6.8 | 1.7.0 |
