---
title: Installation
description: Installer OculiX sur Windows, macOS ou Linux. IDE, bibliothèque ou build depuis les sources.
---

OculiX tourne comme une application Java pure. La seule chose à installer vous-même est un JDK récent. Tout le reste — OpenCV, Tesseract, helpers natifs, données OCR — est embarqué dans le JAR.

## Prérequis système

| Élément              | Minimum                                  | Recommandé              |
| -------------------- | ---------------------------------------- | ----------------------- |
| **Système**          | Windows 10 · macOS 12 · Ubuntu 20.04 LTS | Dernière version stable |
| **Java (JDK)**       | 11                                       | 17 ou 21 LTS            |
| **Architecture**     | x86-64                                   | x86-64 (Apple Silicon supporté via Rosetta et nativement depuis v3.0.2) |
| **RAM**              | 1 Go libre                                | 4 Go libres             |
| **Affichage**        | Un écran                                 | Multi-écran supporté    |

OculiX est testé sur Eclipse Temurin et Azul Zulu — les deux gratuits. Les autres builds OpenJDK certifiés (Liberica, Microsoft Build of OpenJDK, Amazon Corretto) fonctionnent aussi.

```bash
java -version
# doit afficher "openjdk version "11" / "17" / "21""
```

## Option 1 — Lancer l'IDE (le plus courant)

1. Ouvrez la [page des releases](https://github.com/oculix-org/Oculix/releases) et téléchargez le dernier `oculixide-X.Y.Z.jar`.
2. Double-cliquez dessus, ou lancez depuis un terminal :

   ```bash
   java -jar oculixide-3.0.3.jar
   ```

L'IDE s'ouvre avec :

- **Explorateur Workspace** à gauche
- **Éditeur de script** au centre
- **Console** en bas (log live + output d'exécution)
- **Modern Recorder** accessible depuis la barre d'outils
- **Welcome tab** au premier lancement avec des snippets de démarrage

Ouvrez un bundle `.sikuli`, cliquez **Run** (▶), et OculiX prend le contrôle de l'écran.

### Lancer un script directement depuis la ligne de commande

```bash
# Ouvre l'IDE sur un script spécifique
java -jar oculixide-3.0.3.jar -l mon-script.sikuli

# Ouvre ET exécute immédiatement (idéal pour cron / tâches planifiées)
java -jar oculixide-3.0.3.jar -l mon-script.sikuli -e
```

Multi-plateforme — fonctionne sur Linux, macOS, Windows (y compris WSL).

## Option 2 — Utiliser OculiX comme bibliothèque Java

OculiX est publié sur **Maven Central** sous `io.github.oculix-org` :

```xml
<dependency>
    <groupId>io.github.oculix-org</groupId>
    <artifactId>oculixapi</artifactId>
    <version>3.0.3</version>
</dependency>
```

Gradle :

```kotlin
implementation("io.github.oculix-org:oculixapi:3.0.3")
```

Si votre projet appelle aussi OpenCV directement, ajoutez le binaire **Apertix** contre lequel OculiX a été buildé :

```xml
<dependency>
  <groupId>io.github.julienmerconsulting.apertix</groupId>
  <artifactId>opencv</artifactId>
  <version>4.10.0-0</version>
</dependency>
```

Apertix est un build JNA custom d'OpenCV 4.10.0 qui évite les conflits classiques de `System.loadLibrary` quand on mélange OpenCV avec d'autres bibliothèques natives sur Windows.

## Option 3 — Builder depuis les sources

```bash
git clone https://github.com/oculix-org/Oculix.git
cd Oculix
mvn clean install -DskipTests
```

Le build Maven produit :

| Module                  | Artefact                              |
| ----------------------- | ------------------------------------- |
| `API/`                  | `oculixapi-<version>.jar`             |
| `IDE/`                  | `oculixide-<version>.jar`             |
| `MCP/`                  | `oculix-mcp-server-<version>.jar`     |
| `Reporter/`             | `oculix-reporter-<version>.jar`       |
| `Additional-Wrappers/`  | modules wrapper de langage            |

Les fat-jars sont générés par les profils Maven `make-API` et `make-IDE`. Les configurations de run IntelliJ IDEA sont commitées dans `.idea/runConfigurations/` — vous pouvez lancer l'IDE en mode dev en un clic.

## Notes spécifiques aux plateformes

### Windows

- Tous les helpers natifs (`WinUtil.dll`, binaires OpenCV JNA) sont dans le JAR. Pas de PATH à configurer, pas de runtime MSVC à installer, rien.
- Multi-écran : OculiX détecte chaque `Screen(n)` via l'environnement graphique AWT standard.
- High DPI : les scripts capturent et rejouent dans le système de coordonnées de l'OS. Si vous enregistrez à 100 % et que vous rejouez à 150 %, la similarité peut baisser — utilisez `Pattern("foo.png").similar(0.7f)` pour élargir le matcher.

### macOS

- Apple Silicon (M1/M2/M3) supporté nativement depuis OculiX **3.0.2**.
- Au premier lancement, macOS demande les permissions **Accessibilité** et **Enregistrement d'écran** pour le runtime Java. Accordez les deux dans *Réglages système → Confidentialité et sécurité* — sans elles, OculiX ne peut ni bouger la souris ni capturer l'écran.
- Le helper natif `MacUtil.m` gère les ponts AppleScript natifs et l'inspection de fenêtres.

### Linux

- Testé sur Ubuntu 20.04 / 22.04 / 24.04, Debian 12, Fedora 40, Arch.
- X11 entièrement supporté. Wayland fonctionne via XWayland — un support Wayland direct est dans la roadmap.
- Serveurs headless (runners CI) : utilisez le mode **server runner** pour l'exécution headless. Voir la [référence CLI](/fr/reference/cli/).
- `LinuxSupport.java` gère le listage de fenêtres, le focus et les particularités du presse-papiers Linux.

## Optionnel — Serveur HTTP PaddleOCR

Tesseract est **embarqué** et suffit pour les scripts latins. Pour de l'OCR haute précision CJK ou des mises en page complexes, installez l'optionnel [paddleocrserver-powered](https://pypi.org/project/paddleocrserver-powered/) :

```bash
pip install paddleocrserver-powered
paddleocrserver
# Écoute sur http://localhost:5000
```

Le `PaddleOCREngine` d'OculiX découvre automatiquement un serveur en cours sur `localhost:5000`. Pas de config.

## Vérifier l'installation

Le smoke test le plus rapide depuis l'IDE :

```python
from sikuli import *
popup("OculiX is running. Java: " + getJavaVersion())
```

Depuis un projet Java :

```java
import org.sikuli.script.Screen;

public class Smoke {
  public static void main(String[] args) {
    Screen s = new Screen();
    System.out.println("Primary screen: " + s.getW() + "x" + s.getH());
  }
}
```

Vous êtes prêt à [écrire votre premier script](/fr/getting-started/first-script/).
