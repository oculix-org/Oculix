# Rapport d'Analyse — Modernisation du Module IDE OculiX

**Date** : Mars 2026
**Auteur** : Claude Code
**Module** : `/IDE` (125 fichiers Java, ~27 702 lignes de code)
**Version analysée** : 3.0.1

---

## 1. Inventaire des Dépendances Maven

### 1.1 Tableau récapitulatif

| Dépendance | Version actuelle | Dernière stable | CVEs connus | Breaking changes | Statut |
|---|---|---|---|---|---|
| `oculixapi` | 3.0.1 | 3.0.1 (interne) | — | — | OK |
| `jython-slim` | 2.7.2 | 2.7.4 | Aucun critique | Aucun | Minor bump |
| `jruby-complete` | 9.2.11.1 | 9.4.12.0 | Aucun critique | API JRuby Embed stable | Major bump (9.2→9.4) |
| `mac_widgets` | 0.9.5 | 0.9.5 | Aucun | — | **Abandonné** (dernier release 2011) |
| `swing-layout` | 1.0.3 | 1.0.3 | Aucun | — | **Abandonné** (intégré dans JDK 6+ via `javax.swing.GroupLayout`) |
| `jackson-core` | **2.9.10** | 2.18.3 | **CVE-2022-42003, CVE-2022-42004** | Changements mineurs d'API entre 2.9→2.18 | **CRITIQUE** |
| `jackson-databind` | **2.9.10.1** | 2.18.3 | **CVE-2020-36518, CVE-2022-42003, +20 CVEs** | Désérialisation polymorphe renforcée | **CRITIQUE** |
| `undertow-core` | **2.0.27.Final** | 2.3.18.Final | **CVE-2024-3884, CVE-2024-4027, CVE-2025-12543** | API handler modifiée en 2.2+ | **CRITIQUE** |
| `commons-io` | 2.8.0 | 2.18.0 | CVE-2024-47554 (DoS dans 2.0-2.13) | Aucun breaking change majeur | Bump recommandé |

### 1.2 Analyse d'utilisation par dépendance

| Dépendance | Fichiers importants | Intensité d'usage |
|---|---|---|
| `jython-slim` | `JythonSupport.java` (1 234 lignes) | Profonde — interpréteur Python complet |
| `jruby-complete` | `JRubySupport.java` | Profonde — interpréteur Ruby complet |
| `mac_widgets` | `SikuliIDEStatusBar.java`, `ButtonOnToolbar.java` | Minimale — 2 classes UI macOS |
| `swing-layout` | `PreferencesWin.java` | Modérée — layout de la fenêtre préférences |
| `jackson-core/databind` | `SikulixServer.java`, `Style.java`, `Lexer.java` | Profonde — sérialisation JSON REST API + config |
| `undertow-core` | `SikulixServer.java` (1 023 lignes) | Critique — serveur HTTP complet (20+ imports) |
| `commons-io` | 13 fichiers (`EditorPane`, `SikulixIDE`, runners...) | Modérée — `FilenameUtils`, `FileUtils` |

### 1.3 Dépendances mortes

**Aucune dépendance morte identifiée.** Les 9 dépendances déclarées sont toutes activement utilisées.

### 1.4 Dépendances sans maintenance

| Dépendance | Dernier release | Recommandation |
|---|---|---|
| `mac_widgets` 0.9.5 | 2011 | Remplacer par FlatLaf ou supprimer si non essentiel |
| `swing-layout` 1.0.3 | 2006 | Migrer vers `javax.swing.GroupLayout` (JDK standard) |

### 1.5 Dépendances dupliquées avec le module API

`jython-slim` et `jruby-complete` sont déclarées dans les deux POM (API et IDE). La version devrait être centralisée dans le POM parent.

---

## 2. Inventaire Fonctionnel

### 2.1 Editeur de code

| Fonctionnalité | Classe(s) principale(s) | Statut |
|---|---|---|
| Coloration syntaxique | `EditorViewFactory`, `SikuliEditorKit`, package `syntaxhighlight` (30+ classes) | Complet — système lexer/tokenizer avec state machine |
| Autocomplétion | `JythonCompleter`, `AbstractCompleter`, `IAutoCompleter` | Complet — Python/Jython |
| Indentation automatique | `PythonIndentation`, `PythonState`, `IIndentationLogic` | Complet — logique Python (indent/dedent) |
| Undo/Redo | `EditorUndoManager`, `EditorPaneUndoRedo` | Complet — groupement d'éditions |
| Numéros de ligne | `EditorLineNumberView` | Complet — avec marqueurs d'erreur |
| Surlignage ligne courante | `EditorCurrentLineHighlighter` | Complet |
| Tabulation personnalisée | `EditorMyDocument` (tab→espaces) | Complet |

### 2.2 Gestion de projets / fichiers

| Fonctionnalité | Classe(s) | Statut |
|---|---|---|
| Onglets fermables | `CloseableTabbedPane`, `CloseableModernTabbedPaneUI` | Complet — style macOS |
| Ouverture/sauvegarde .sikuli | `SikulixIDE` (méthodes doOpen/doSave) | Complet |
| Gestion des bundles | `SikulixIDE`, `EditorPane` | Complet |
| Menu contextuel onglets | `SikuliIDEPopUpMenu` | Complet |

### 2.3 Console de logs / output

| Fonctionnalité | Classe(s) | Statut |
|---|---|---|
| Console de sortie | `EditorConsolePane` | Complet — redirection stdout/stderr |
| Messages colorés | `EditorConsolePane` (styles HTML) | Complet |

### 2.4 Capture d'écran intégrée

| Fonctionnalité | Classe(s) | Statut |
|---|---|---|
| Bouton capture | `ButtonCapture` | Complet |
| Capture avec délai | `ButtonCapture.captureWithAutoDelay()` | Complet |
| Bouton région | `EditorRegionButton`, `EditorRegionLabel` | Complet |

### 2.5 Gestion des patterns / images

| Fonctionnalité | Classe(s) | Statut |
|---|---|---|
| Bouton image inline | `EditorImageButton` | Complet |
| Bouton pattern | `EditorPatternButton`, `EditorPatternLabel` | Complet |
| Fenêtre pattern | `PatternWindow` (483 lignes) | Complet |
| Slider similarité | `PatternSimilaritySlider` | Complet |
| Target offset | `PatternPaneTargetOffset` | Complet |
| Aperçu screenshot | `PatternPaneScreenshot` | Complet |
| Renommage image | `PatternPaneNaming` | Complet |
| Optimisation image | `SXDialogPaneImageOptimize` | Complet |
| Prévisualisation | `PreviewWindow` | Stub (implémentation minimale) |

### 2.6 Exécution de scripts (runners)

| Runner | Classe | Format supporté |
|---|---|---|
| Python/Jython | `JythonRunner` | `.py`, `.sikuli` |
| JRuby | `JRubyRunner` | `.rb` |
| Python externe | `PythonRunner` | `.py` (via binaire Python) |
| Robot Framework | `RobotRunner` | `.robot` |
| JAR | `JarRunner`, `JarExeRunner` | `.jar` |
| Réseau | `NetworkRunner` | URL |
| Serveur | `ServerRunner` | HTTP API |
| ZIP | `ZipRunner` | `.zip` |
| SKL | `SKLRunner` | `.skl` |
| SikuliX bundle | `SikulixRunner` | dossier `.sikuli` |
| Texte | `TextRunner` | `.txt` |
| Git test | `SilkulixGitTestRunner` | Git repos (désactivé) |

### 2.7 Serveur HTTP intégré

| Fonctionnalité | Classe | Détail |
|---|---|---|
| API REST | `SikulixServer` (1 023 lignes) | Routes : tasks, scripts, groups, controller |
| Client handler | `ServerRunner` (533 lignes) | Gestion des requêtes |
| JSON serialization | Via Jackson ObjectMapper | Réponses JSON |

### 2.8 Paramètres / Préférences

| Fonctionnalité | Classe(s) | Statut |
|---|---|---|
| Fenêtre préférences | `PreferencesWin` (685 lignes) | Complet — hotkeys, fonts, capture |
| Préférences avancées | `PreferencesWindowMore` (558 lignes) | Complet |

### 2.9 Internationalisation (i18n)

**22 langues supportées** via fichiers `.properties` :
en_US, es, de, fr, it, ja, zh_CN, zh_TW, ko, ru, pl, pt_BR, tr, nl, sv, da, ca, he, ar, bg, uk, ta_IN

Classe de support : `SikuliIDEI18N._I()` pour les traductions.

### 2.10 Intégration plateforme

| Fonctionnalité | Classe(s) | Statut |
|---|---|---|
| Desktop macOS/Java 9+ | `IDEDesktopSupport` (2 versions : `idesupport` et `support.ide`) | Complet — About, Preferences, Quit, Open File |
| Icône taskbar/dock | `IDETaskbarSupport` | Complet — Java 9+ et macOS Java 8 |
| Splash screen | `IDESplash` | Complet |
| Auto-update | `AutoUpdater` | Présent mais marqué TODO |

### 2.11 Autres

| Fonctionnalité | Classe(s) | Statut |
|---|---|---|
| Barre de statut | `SikuliIDEStatusBar` | Complet — position curseur, type fichier |
| Décodeur GIF | `GifDecoder` (759 lignes) | Complet — animations |
| Loading spinner | `LoadingSpinner` | Complet |
| Extensions manager | `ExtensionManager`, `ExtensionManagerFrame` | Complet |
| Génération commandes | `ButtonGenCommand` | Complet |

---

## 3. Analyse de la Dette Technique

### 3.1 Version Java

- **Source/Target actuel** : `1.8` (Java 8)
- **Fichier `.java-version`** : `11`
- **Cible recommandée** : Java 17 LTS (minimum), Java 21 LTS (préféré)
- **Impact** : Toutes les modernisations syntaxiques Java 9-21 sont applicables

### 3.2 God Classes (>500 lignes)

| Fichier | Lignes | Sévérité |
|---|---|---|
| `SikulixIDE.java` | **3 319** | **CRITIQUE** — ~206 méthodes, responsabilités multiples |
| `JythonSupport.java` | 1 234 | Élevée |
| `EditorPane.java` | 1 153 | Élevée |
| `SikulixServer.java` | 1 023 | Élevée |
| `PythonState.java` | 803 | Modérée |
| `GifDecoder.java` | 759 | Modérée |
| `SikuliEditorKit.java` | 685 | Modérée |
| `PreferencesWin.java` | 685 | Modérée |
| `ExtensionManager.java` | 602 | Modérée |
| `PreferencesWindowMore.java` | 558 | Modérée |
| `EditorViewFactory.java` | 556 | Modérée |
| `ServerRunner.java` | 533 | Modérée |
| `SikuliIDEPopUpMenu.java` | 526 | Modérée |

### 3.3 `printStackTrace()` — 10 occurrences

| Fichier | Ligne |
|---|---|
| `AutoUpdater.java` | 221 |
| `SikulixIDE.java` | 3133 |
| `JRubySupport.java` | 89 |
| `EditorPane.java` | 1129 |
| `EditorPaneUndoRedo.java` | 77 |
| `SikulixServer.java` | 700, 743, 793, 881, 978 |

### 3.4 Exceptions avalées (catch vides)

| Fichier | Ligne | Sévérité |
|---|---|---|
| `EditorConsolePane.java` | 231-232 | **CRITIQUE** — exceptions complètement ignorées au shutdown |

### 3.5 `@SuppressWarnings` — 14+ occurrences

| Fichier | Type | Peut être corrigé ? |
|---|---|---|
| `PatternPaneScreenshot.java:19` | `"serial"` | Oui — ajouter serialVersionUID |
| `CloseableModernTabbedPaneUI.java:42-46` | champs non utilisés | Oui — supprimer les champs |
| `PreferencesWindowMore.java:44` | `"unchecked"` | Oui — corriger les generics |
| `IIDESupport.java:11` | `"serial"` | Oui |
| `Style.java:56,215` | unchecked casts | Oui — corriger les generics |
| `IDETaskbarSupport.java:51,54` | rawtypes | Oui — migration Java 9+ |
| `IDEDesktopSupport.java:111-138` | reflection | Oui — migration Java 9+ Desktop API |

### 3.6 TODO/FIXME — 29+ occurrences

Principaux :
- `IDETaskbarSupport.java:31` — "Replace reflective code with code snippet below as soon as we ditch Java 8 support" → **Actionnable maintenant (Java 17+)**
- `PatternPaneTargetOffset.java:77` — "rewrite completely for ScreenUnion"
- `AutoUpdater.java:15` — classe entière marquée TODO
- `PatternPaneScreenshot.java:67,97` — multiples TODOs
- `ButtonGenCommand.java:125` — code commenté

### 3.7 APIs dépréciées Java

| Pattern | Occurrences | Remplacement Java 17+ |
|---|---|---|
| `Stack` | `PythonState.java` | `Deque<>` / `ArrayDeque<>` |
| `Hashtable` | `PatternPaneScreenshot.java` | `HashMap<>` |
| `Enumeration` | `ZipRunner.java` | Iterators |
| `Charset.forName("utf-8")` | `SikulixIDE.java` (4x) | `StandardCharsets.UTF_8` |
| Reflection pour Desktop API | `IDEDesktopSupport.java`, `IDETaskbarSupport.java` | `java.awt.Desktop` direct (Java 9+) |

### 3.8 Fuites de ressources (absence de try-with-resources)

| Fichier | Ligne | Ressource |
|---|---|---|
| `Sikulix.java` | 122 | `FileOutputStream` |
| `GifDecoder.java` | 316 | `FileInputStream` |
| `SikulixIDE.java` | 1067, 1084, 1305 | `FileInputStream`/`FileOutputStream` |
| `Util.java` (syntaxhighlight) | 37, 148 | `BufferedReader` |
| `SikulixServer.java` | 86, 253 | `FileOutputStream` statique (jamais fermé) |
| `ServerRunner.java` | 70, 99 | `FileOutputStream` statique (jamais fermé) |
| `Lexer.java` | 151 | `JarInputStream` |

### 3.9 Violations EDT (Thread Safety)

| Fichier | Problème |
|---|---|
| `PatternPaneScreenshot.java:140-158` | Création Thread + mise à jour GUI |
| `PatternPaneTargetOffset.java:69-74` | Race condition potentielle |
| `SikulixIDE.java` | 6+ créations `new Thread()` sans pool |
| `SikuliIDEPopUpMenu.java` | 3 créations `new Thread()` sans pool |
| `EditorPane.java:1045` | Thread sans pool |

### 3.10 System.out/System.err au lieu du logging

**16 fichiers** utilisent `System.out` ou `System.err` au lieu du framework de logging (le projet utilise déjà `Debug` de l'API).

### 3.11 Code mort / dupliqué

| Élément | Détail |
|---|---|
| `org.sikuli.idesupport.IDEDesktopSupport` | Dupliqué avec `org.sikuli.support.ide.IDEDesktopSupport` |
| `org.sikuli.idesupport.IDESupport` | Dupliqué avec `org.sikuli.support.ide.IDESupport` |
| `org.sikuli.idesupport.IIDESupport` | Interface dupliquée |
| `SilkulixGitTestRunner` | Désactivé (`isSupported() = false`), faute de frappe dans le nom |
| `org.sikuli.basics.SikulixForJython` | Marqué `@Deprecated` |
| `PreviewWindow.java` | Stub minimal (64 lignes, quasi vide) |
| 2 fichiers SPI dans META-INF/services | Registrations dupliquées pour les 2 packages |

---

## 4. État des Tests

### 4.1 Tests existants

**Aucun.** Le module IDE ne possède pas de répertoire `src/test/`. Zéro test unitaire, zéro test d'intégration.

### 4.2 Couverture approximative

**0%** — Aucune infrastructure de test n'est en place.

### 4.3 Classes critiques non couvertes (priorité haute)

1. `SikulixIDE` — point d'entrée principal
2. `EditorPane` — éditeur de code
3. `SikulixServer` — serveur HTTP
4. `JythonSupport` — interpréteur Python
5. `PythonIndentation` — logique d'indentation (testable unitairement)
6. `PythonState` — parsing de document (testable unitairement)
7. `EditorUndoManager` — undo/redo (testable unitairement)

---

## 5. Ressources du Module

- **22 fichiers i18n** (.properties) + fichiers .po de traduction
- **45+ icônes** (PNG, GIF, PSD)
- **2 fichiers SPI** (META-INF/services)
- **Templates GUI** : 7 fichiers .txt avec markup personnalisé
- **Bibliothèques Python** : Sikuli bindings, xlrd/xlwt/xlutils, guide
- **Fichier Ruby** : `sikulix.rb`
- **Scripts** : `sikuli2html.py`, `testRun.py`

---

## 6. Plan de Modernisation Recommandé

### Phase 1 : Dépendances Maven (priorité sécurité)

| Ordre | Action | Risque |
|---|---|---|
| 1 | Undertow 2.0.27 → 2.3.18 | Moyen — vérifier API handler |
| 2 | Jackson 2.9.10 → 2.18.3 | Faible — API rétro-compatible |
| 3 | Commons-IO 2.8.0 → 2.18.0 | Très faible |
| 4 | Jython 2.7.2 → 2.7.4 | Très faible |
| 5 | JRuby 9.2.11.1 → 9.4.12.0 | Moyen — vérifier API embed |
| 6 | `swing-layout` → `javax.swing.GroupLayout` (JDK) | Moyen — refactoring PreferencesWin |
| 7 | `mac_widgets` → évaluer remplacement FlatLaf | Faible |
| 8 | Maven compiler plugin 3.8.1 → 3.13.0 | Très faible |
| 9 | Maven assembly plugin 3.1.1 → 3.7.1 | Très faible |

### Phase 2 : Java 17+ syntaxique

- `source`/`target` : 1.8 → 17
- `var` pour variables locales avec type évident
- `instanceof` pattern matching
- Text blocks pour chaînes multilignes
- `StandardCharsets.UTF_8` partout
- `List.of()`, `Map.of()` pour collections immuables
- try-with-resources pour toutes les ressources
- `Stack` → `ArrayDeque`
- `Hashtable` → `HashMap`
- Desktop API directe (supprimer la reflection Java 8)

### Phase 3 : Refactoring architectural

1. **SikulixIDE.java** (3 319 lignes) — découper en :
   - `IDEMenuManager` (menus et actions)
   - `IDEFileManager` (open/save/close)
   - `IDERunManager` (exécution de scripts)
   - `IDEWindowManager` (layout et composants)
2. Supprimer les packages `org.sikuli.idesupport` dupliqués
3. Remplacer `printStackTrace()` par logging structuré
4. Corriger les exceptions avalées
5. Centraliser les constantes hardcodées

### Phase 4 : Tests (JUnit 5 + Mockito)

Priorité : `PythonIndentation`, `PythonState`, `EditorUndoManager`, `SikuliIDEI18N`

### Phase 5 : Documentation

Javadoc sur classes publiques + README module.

---

## 7. Risques Identifiés

| Risque | Impact | Mitigation |
|---|---|---|
| Undertow 2.0 → 2.3 : breaking changes API | Élevé | Tester `SikulixServer` après migration |
| JRuby 9.2 → 9.4 : API embed modifiée | Moyen | Tester `JRubySupport` après migration |
| `swing-layout` → JDK GroupLayout | Moyen | Refactoring `PreferencesWin` (685 lignes) |
| `mac_widgets` abandon | Faible | Pas de remplacement urgent, fonctionne encore |
| Pas de tests existants | Élevé | Validation manuelle obligatoire à chaque étape |

---

*Rapport généré automatiquement par Claude Code. Aucune modification de code n'a été effectuée.*
*En attente de validation avant de passer à la Phase 1 (mise à jour des dépendances).*
