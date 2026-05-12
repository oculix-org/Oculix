---
title: Votre premier script
description: Ouvrir une app, trouver un bouton par image, le cliquer. Le hello world OculiX.
---

Ce guide vous accompagne dans le workflow OculiX canonique : **voir → matcher → agir**. À la fin, vous aurez un script qui trouve un bouton à l'écran par son image et le clique.

## 1. Capturer ce que vous voulez cliquer

1. Lancez l'IDE OculiX.
2. Cliquez **New Script** (ou *File → New*). L'IDE crée un bundle `.sikuli` — un dossier qui contient votre script et ses images.
3. Positionnez votre cible à l'écran — pour cet exemple, ouvrez votre explorateur de fichiers et choisissez n'importe quel bouton visible (par exemple le bouton **Save** d'un éditeur de texte).
4. Dans la barre d'outils de l'IDE, cliquez l'icône **Take Screenshot** (appareil photo). L'écran se fige, votre curseur devient un viseur.
5. Tracez un rectangle serré autour de votre cible. Relâchez.

OculiX insère l'image capturée dans le script comme une miniature cliquable :

```python
click("save_button.png")
```

L'image est sauvegardée dans le dossier `.sikuli`. Vous pouvez la renommer, la remplacer, ou la réutiliser dans d'autres scripts.

## 2. L'exécuter

Appuyez sur **▶ Run** (ou *Cmd/Ctrl + R*). OculiX :

1. Prend une capture fraîche de l'écran actuel.
2. Cherche `save_button.png` n'importe où sur celle-ci.
3. Déplace la souris jusqu'au match et clique.

S'il ne trouve rien, vous verrez `FindFailed` dans la console — généralement parce que la cible a bougé, a été masquée, ou que votre seuil de similarité est trop strict.

## 3. Construire quelque chose d'utile

Voici un script en une fenêtre qui exporte un rapport quotidien :

```python
from sikuli import *

# Ouvre l'app via son icône dans la barre des tâches/dock (image capturée auparavant)
click("app_icon.png")
wait("app_main_window.png", 10)   # attend jusqu'à 10 s la fenêtre principale

# Parcourt le menu export
click("file_menu.png")
click("export_to_csv.png")
wait("save_dialog.png", 5)

# Tape le nom du fichier dans le champ focalisé
type("filename_field.png", "rapport_" + getDate() + ".csv")
click("save_button.png")

# Attend que le toast de confirmation disparaisse avant de fermer
waitVanish("loading_spinner.png", 30)
popup("Terminé !")
```

Trois choses à remarquer :

- **Aucun sélecteur.** OculiX ne vous a jamais demandé la classe CSS ou l'ID d'accessibilité du bouton.
- **`wait()` et `waitVanish()`** sont la façon de se synchroniser avec l'application : attendre qu'un élément apparaisse (ou disparaisse) avant de continuer.
- **Python standard.** Le `+ getDate()` est du pur Python — tout ce que vous savez faire en Jython (syntaxe Python 2.7, accès JVM complet) peut être fait ici.

## 4. Ajuster la similarité quand le match est trop strict

Par défaut OculiX exige 70 % de similarité. Pour des UIs avec antialiasing, transparence ou changements de thème, vous voudrez peut-être détendre :

```python
click(Pattern("save_button.png").similar(0.65))
```

Ou globalement pour tout le script :

```python
Settings.MinSimilarity = 0.65
```

## 5. Sauvegarder et rejouer

Faites **Save**. Votre bundle de script (`mon-export.sikuli`) est un dossier ordinaire que vous pouvez :

- Versionner avec Git
- Partager avec un collègue (zippez le dossier, c'est tout)
- Planifier avec cron / Task Scheduler (`java -jar oculixide.jar -l mon-export.sikuli -e`)

## Pour lire la suite

- [La reconnaissance visuelle en profondeur](/fr/guides/visual-matching/) — `Region`, `Pattern`, `Match`, similarité, anchors
- [Lire du texte à l'écran avec l'OCR](/fr/guides/ocr/) — trouver un bouton par son libellé, pas par son image
- [Visite de l'IDE](/fr/getting-started/ide-tour/) — Workspace, Modern Recorder, Welcome tab
