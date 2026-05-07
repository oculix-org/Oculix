# CDC — Operix (Python)
## Python wrapper for OculiX via Py4J

**Auteur :** Julien Mer — JMer Consulting
**Date :** 19 avril 2026
**Statut :** A implementer
**Repo cible :** oculix-org/operix
**PyPI :** oculix
**Dependance :** oculixapi (Maven Central)

---

## 1. Probleme

OculiX est un JAR Java. Pour l'utiliser, il faut soit :
- Un projet Java/Maven (devs Java uniquement)
- L'IDE OculiX avec Jython (limite, Python 2.7)
- La ligne de commande `-r script.py` (pas integrable dans un workflow Python)

Les devs Python, les data engineers, les QA qui utilisent pytest/Robot Framework
ne peuvent pas utiliser OculiX nativement.

## 2. Solution

Un package PyPI `oculix` qui expose toute l'API OculiX en Python natif via Py4J.

```bash
pip install oculix
```

```python
from oculix import Screen, App, Pattern

screen = Screen()
screen.click("button.png")
app = App.open("notepad")
```

## 3. Architecture

```
+---------------------+         +---------------------+
|   Python process    |  Py4J   |   JVM process       |
|                     | socket  |                     |
|   from oculix ...   |<------->|   oculixapi.jar     |
|   screen.click()    |         |   + GatewayServer   |
|                     |         |                     |
+---------------------+         +---------------------+
```

1. Au premier `import oculix`, le package :
   - Verifie si Java est installe (`java -version`)
   - Verifie si le JAR OculiX est present dans `~/.oculix/lib/`
   - Si absent, le telecharge depuis Maven Central
   - Demarre une JVM avec le JAR + GatewayServer Py4J
   - Connecte le client Python au GatewayServer

2. Les appels Python sont traduits en appels Java via Py4J (transparent)

3. A la fin du script (ou `atexit`), la JVM est arretee proprement

## 4. Structure du repo

```
oculix-org/operix/
|-- README.md
|-- LICENSE (MIT)
|-- pyproject.toml
|-- setup.cfg
|-- src/
|   +-- oculix/
|       |-- __init__.py          # import principal, auto-start JVM
|       |-- gateway.py           # gestion JVM lifecycle (start/stop/download)
|       |-- screen.py            # wrapper Screen
|       |-- region.py            # wrapper Region
|       |-- pattern.py           # wrapper Pattern
|       |-- app.py               # wrapper App
|       |-- vnc.py               # wrapper VNCScreen, SSHTunnel
|       |-- adb.py               # wrapper ADBScreen
|       |-- ocr.py               # wrapper OCR, PaddleOCR
|       |-- keys.py              # constantes Key, KeyModifier
|       +-- _version.py          # version du package
|-- tests/
|   |-- test_gateway.py
|   |-- test_screen.py
|   |-- test_pattern.py
|   +-- test_app.py
|-- java/
|   +-- OculixGateway.java       # point d'entree JVM avec GatewayServer
+-- scripts/
    +-- download_jar.py          # telecharge oculixapi depuis Maven Central
```

## 5. Composants detailles

### 5.1 gateway.py — JVM lifecycle

```python
import subprocess
import atexit
import os
from py4j.java_gateway import JavaGateway, GatewayParameters

_gateway = None
_jvm_process = None

JAR_DIR = os.path.expanduser("~/.oculix/lib")
JAR_NAME = "oculixapi-3.0.2.jar"
JAR_URL = "https://repo1.maven.org/maven2/io/github/oculix-org/oculixapi/3.0.2/oculixapi-3.0.2.jar"

def _ensure_jar():
    jar_path = os.path.join(JAR_DIR, JAR_NAME)
    if not os.path.exists(jar_path):
        os.makedirs(JAR_DIR, exist_ok=True)
        import urllib.request
        print(f"[OculiX] Downloading {JAR_NAME}...")
        urllib.request.urlretrieve(JAR_URL, jar_path)
        print(f"[OculiX] Saved to {jar_path}")
    return jar_path

def start():
    global _gateway, _jvm_process
    if _gateway is not None:
        return _gateway

    jar_path = _ensure_jar()
    _jvm_process = subprocess.Popen(
        ["java", "-cp", jar_path, "org.sikuli.script.OculixGateway"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE
    )

    import time
    time.sleep(3)

    _gateway = JavaGateway(gateway_parameters=GatewayParameters(port=25333))
    atexit.register(stop)
    return _gateway

def stop():
    global _gateway, _jvm_process
    if _gateway:
        _gateway.shutdown()
        _gateway = None
    if _jvm_process:
        _jvm_process.terminate()
        _jvm_process = None

def jvm():
    if _gateway is None:
        start()
    return _gateway.jvm
```

### 5.2 __init__.py — API publique

```python
from oculix.gateway import jvm, start, stop

def __getattr__(name):
    _jvm = jvm()
    _classes = {
        "Screen": _jvm.org.sikuli.script.Screen,
        "Region": _jvm.org.sikuli.script.Region,
        "Pattern": _jvm.org.sikuli.script.Pattern,
        "Match": _jvm.org.sikuli.script.Match,
        "App": _jvm.org.sikuli.script.App,
        "Key": _jvm.org.sikuli.script.Key,
        "KeyModifier": _jvm.org.sikuli.script.KeyModifier,
        "VNCScreen": _jvm.org.sikuli.vnc.VNCScreen,
        "ADBScreen": _jvm.org.sikuli.script.ADBScreen,
        "SSHTunnel": _jvm.com.sikulix.util.SSHTunnel,
        "OCR": _jvm.org.sikuli.script.OCR,
        "FindFailed": _jvm.org.sikuli.script.FindFailed,
        "Image": _jvm.org.sikuli.script.Image,
        "Location": _jvm.org.sikuli.script.Location,
        "Settings": _jvm.org.sikuli.basics.Settings,
    }
    if name in _classes:
        return _classes[name]
    raise AttributeError(f"module 'oculix' has no attribute '{name}'")
```

### 5.3 OculixGateway.java — Point d'entree JVM

```java
package org.sikuli.script;

import py4j.GatewayServer;
import org.sikuli.support.Commons;

public class OculixGateway {
    public static void main(String[] args) {
        Commons.loadOpenCV();
        GatewayServer server = new GatewayServer(null, 25333);
        server.start();
        System.out.println("[OculiX] Gateway started on port 25333");
    }
}
```

## 6. Exemples d'usage

### 6.1 Script basique

```python
from oculix import Screen, Key

screen = Screen()
screen.click("login_button.png")
screen.type("admin")
screen.type(Key.TAB)
screen.type("password123")
screen.type(Key.ENTER)
screen.wait("dashboard.png", 10)
```

### 6.2 VNC remote

```python
from oculix import VNCScreen, SSHTunnel

tunnel = SSHTunnel("root", "10.184.10.147", 22, "password")
tunnel.open(5900, "localhost", 5900)

vnc = VNCScreen.start("localhost", 5900, "", 1920, 1080)
vnc.click("auchan_logo.png")
vnc.type("1234")
vnc.stop()
```

### 6.3 Android ADB

```python
from oculix import ADBScreen, Pattern

adb = ADBScreen.start("/usr/local/bin/adb")
adb.click(Pattern("accept_button.png").similar(0.7))
```

### 6.4 Avec pytest

```python
import pytest
from oculix import Screen, App

@pytest.fixture
def app():
    a = App.open("calculator")
    yield a
    a.close()

def test_addition(app):
    screen = Screen()
    screen.click("button_7.png")
    screen.click("button_plus.png")
    screen.click("button_3.png")
    screen.click("button_equals.png")
    assert screen.exists("result_10.png")
```

## 7. pyproject.toml

```toml
[build-system]
requires = ["setuptools>=68.0", "wheel"]
build-backend = "setuptools.build_meta"

[project]
name = "oculix"
version = "0.1.0"
description = "Visual automation for the real world - Python wrapper for OculiX"
readme = "README.md"
license = {text = "MIT"}
requires-python = ">=3.8"
authors = [{name = "Julien Mer", email = "julien.mer38@gmail.com"}]
keywords = ["visual-testing", "automation", "ocr", "sikuli", "gui-testing"]
dependencies = ["py4j>=0.10.9"]

[project.urls]
Homepage = "https://github.com/oculix-org/operix"
Repository = "https://github.com/oculix-org/operix"
```

## 8. Risques et mitigations

| Risque | Mitigation |
|---|---|
| Java pas installe | Message clair + lien download au premier import |
| JAR download echoue | Cache local, retry, message d'erreur avec URL manuelle |
| Port 25333 deja pris | Port configurable via env var OCULIX_GATEWAY_PORT |
| Py4J latence | Negligeable pour du visual testing (actions = secondes) |
| JVM qui crash | atexit cleanup + message d'erreur clair |

## 9. Roadmap

| Phase | Contenu | Duree |
|---|---|---|
| Phase 1 | Gateway + Screen + Pattern + App + Key | 2 jours |
| Phase 2 | VNCScreen + ADBScreen + SSHTunnel | 1 jour |
| Phase 3 | OCR + Settings + pytest examples | 1 jour |
| Phase 4 | Robot Framework library wrapper | 1 jour |
| Phase 5 | PyPI publication + README + docs | 1 jour |
| Phase 6 | CI (GitHub Actions: test on Win/Mac/Linux) | 1 jour |

**Total : ~1 semaine**

---

*"pip install oculix. That's it. Visual automation in Python, powered by 15 years of SikuliX."*
