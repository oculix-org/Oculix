---
name: oculix-dev
description: Bonnes pratiques pour le développement OculiX/SikuliX. Utiliser cette skill quand le projet contient org.sikuli, quand le repo est oculix-org/Oculix, ou quand l'utilisateur mentionne OculiX, SikuliX, ou SikuliX IDE. Activer aussi quand le code touche à Swing, FlatLaf, MigLayout, JDialog, ou JPanel dans le contexte OculiX.
---

# Règles de développement OculiX

## Architecture
- SikulixIDE.java est un monolithe de 3800+ lignes avec 21 inner classes — ne jamais extraire ni refactorer ses inner classes
- Toujours cohabiter avec le legacy, jamais remplacer directement
- Rendre les méthodes `public` si elles doivent être accessibles depuis d'autres packages — pas de hack Robot/clipboard/contournement
- Les inner classes (ButtonRecord, FileAction, EditAction, RunAction, etc.) ne sont jamais modifiées sauf demande explicite

## Code
- Regarder le code legacy existant AVANT de coder une solution — reproduire les patterns validés (ex: ButtonRecord pour le pattern capture)
- Pas d'overengineering — une saisie texte simple vaut mieux qu'un workflow OCR complexe
- Réutiliser les API existantes (Finder, JythonCodeGenerator, Screen.userCapture, OCREngine) au lieu de réinventer
- Appels bloquants (Screen.userCapture, OCREngine.recognize) dans un Thread séparé, jamais dans SwingUtilities.invokeLater ni sur l'EDT
- Quand on cache l'IDE pour une interaction (capture, recorder), utiliser parent.setVisible(false) comme fait le legacy ButtonRecord

## Packages et accès
- `org.sikuli.ide` — package principal IDE, contient SikulixIDE et ses inner classes
- `org.sikuli.ide.ui` — composants UI modernes (OculixSidebar, SidebarItem, WorkspaceDialog, etc.)
- `org.sikuli.ide.ui.recorder` — package du Modern Recorder
- `org.sikuli.script` — API SikuliX (Screen, Pattern, Finder, Image, Match)
- `org.sikuli.support.recorder` — actions et générateurs de code du recorder
- `com.sikulix.ocr` — moteurs OCR (OCREngine interface, PaddleOCREngine, PaddleOCRClient)
- Si une méthode est package-private et nécessaire depuis un autre package, la rendre public plutôt que contourner

## Git
- Auteur des commits : `julienmerconsulting <126155544+julienmerconsulting@users.noreply.github.com>`
- Pas de lien claude.ai dans les messages de commit
- Commits atomiques : chaque commit doit laisser l'IDE compilable et démarrable
- Ne jamais amender les commits de RaiMan (Raimund Hocke)

## Build Maven
- Le module API n'a PAS le profil `complete-win-jar` (seulement le module IDE)
- Build complet : `mvn clean package -pl IDE -am -DskipTests -P complete-win-jar`
- Le `-am` (also make) est obligatoire pour recompiler API quand on y touche
- Install API seul : `mvn install -pl API -DskipTests`

## UI / Swing
- FlatLaf hérité du thème global — ne jamais configurer FlatLaf dans les nouveaux composants
- MigLayout pour tous les layouts
- UIManager.getColor() et UIManager.getFont() pour les couleurs et polices — jamais de couleurs hardcodées sauf branding OculiX teal #00A89D
- Référence UI : WorkspaceDialog.java (pattern JDialog + MigLayout + UIManager)
- Référence boutons : SidebarItem.java (FlatClientProperties, borderless, hand cursor)

## i18n
- Fichiers : IDE_en_US.properties et IDE_fr.properties dans IDE/src/main/resources/i18n/
- Accès via SikuliIDEI18N._I(key, args...) avec fallback automatique en_US
- Les 20 autres fichiers i18n ne sont PAS touchés — le fallback gère

## Le mainteneur
- Le mainteneur principal d'OculiX est julienmerconsulting (Julien Mer), basé à Lyon
- OculiX est le successeur de SikuliX (créé au MIT CSAIL), repris de RaiMan
- C'est un projet utilisé par des centaines de milliers de personnes — coder en conséquence, pas de prototype yolo
