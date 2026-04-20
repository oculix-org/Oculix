# CDC — Article technique long-form, option C (mainteneur)

## Métadonnées

- **Titre de travail** : *Ce que les frameworks modernes ont cassé dans l'automatisation de test : cartographie d'un problème et retour d'expérience d'un mainteneur open source*
- **Public cible** : ingénieurs QA seniors, QA Architects, CTOs, tech leads en charge des pipelines de test
- **Longueur visée** : 6000-8000 mots
- **Ton** : engineering blog post technique, style Shopify Engineering / Stripe Engineering / Cloudflare Blog. Factuel, sourcé, personnel sans être promotionnel.
- **Règles d'écriture** :
  - Pas de pitch commercial
  - Pas d'attaque frontale de concurrents (Playwright, Applitools, Eggplant mentionnés factuellement avec leurs limites documentées publiquement)
  - Chaque affirmation technique doit pointer vers une source vérifiable (issue GitHub, doc officielle, commit, article publié)
  - OculiX présenté comme *cas d'étude de construction d'alternative open source*, avec code et commits cités en référence
  - Auchan peut être cité comme contexte ("une mission retail sur environnement POS virtualisé") sans nommer le client ni faire d'encart marketing
  - Aucun emoji dans le corps de l'article
  - Titres de sections en français, code/commandes en anglais
- **Signature** : Julien Mer, QA Architect indépendant, mainteneur d'OculiX (fork SikuliX)
- **Licence** : à confirmer (CC BY-SA 4.0 par défaut)

---

## Sommaire détaillé

### Introduction — Pourquoi cet article

**Intention** : poser le contexte personnel et le cadre de l'analyse. Expliquer pourquoi un mainteneur open source écrit un article critique sur l'état de l'art, sans verser dans le pitch.

**À rédiger** :
- Contexte personnel : 20 ans en QA Architecture, secteurs régulés
- Constat répété sur les missions récentes : les équipes QA sur stacks modernes (React, Next.js, Vue) vivent une dégradation de la fiabilité de leurs tests qu'elles ne savent pas nommer
- Posture de l'article : ni neutre (l'auteur est partie prenante, maintient OculiX), ni commerciale (l'article documente un problème, pas un produit)
- Annonce : l'article parcourt la cartographie du problème, puis montre comment un fork SikuliX essaie d'y répondre avec les choix techniques assumés

**Longueur** : ~400 mots

---

### I. Le contrat implicite cassé : hydration et tests end-to-end

**Intention** : expliquer techniquement pourquoi les tests E2E sur frameworks modernes sont flaky par construction, pas par mauvaise configuration.

**À rédiger** :
- I.1 — Rappel technique sur l'hydration (SSR → HTML → client JS → rattachement handlers)
- I.2 — L'aveu officiel de Playwright via issue #27759 (octobre 2023). Citer la réponse de l'équipe Playwright : le fix est applicatif, pas dans l'outil.
- I.3 — Les hydration mismatches silencieux. Citer l'article Alex Op (avril 2026).
- I.4 — Pourquoi aucun outil DOM-based ne peut résoudre ça depuis l'extérieur (explication conceptuelle : le DOM ne reflète pas l'état interne du framework)

**Sources à maintenir** :
- github.com/microsoft/playwright/issues/27759
- alexop.dev/posts/catch-hydration-errors-playwright-tests/
- React docs sur l'hydration
- Next.js App Router docs

**Longueur** : ~800 mots

---

### II. Server Components et streaming SSR : une couche d'opacité supplémentaire

**Intention** : montrer que même en résolvant l'hydration, les frameworks récents introduisent de nouvelles classes d'états non observables par les outils DOM.

**À rédiger** :
- II.1 — React Server Components : ce qu'ils sont, ce qu'ils impliquent pour l'observabilité de test
- II.2 — Le streaming SSR : la question "à quel moment la page est-elle chargée ?" n'a plus de réponse simple
- II.3 — Suspense boundaries et le non-déterminisme temporel
- II.4 — Conclusion de section : la surface testable se réduit à mesure que les frameworks optimisent le rendu

**Sources à maintenir** :
- React RSC RFC
- Next.js streaming docs
- Vercel blog sur App Router

**Longueur** : ~700 mots

---

### III. Les sélecteurs et la pollution du code par testid

**Intention** : documenter le coût caché du testid-everywhere, sans moraliser.

**À rédiger** :
- III.1 — CSS-in-JS et la perte de stabilité des class names (citer issue #309 babel-plugin-styled-components)
- III.2 — La réponse industrielle : data-testid partout
- III.3 — Les effets secondaires : pollution prod, couplage dev-QA, explosion combinatoire des identifiants
- III.4 — Le cas Tailwind (plus stable, mais composition de 15-30 classes)
- III.5 — L'ironie : Playwright recommande en réalité getByRole contre getByTestId

**Sources à maintenir** :
- github.com/styled-components/babel-plugin-styled-components/issues/309
- Playwright locators documentation
- Article DEV "Test ID Best Practices Guide"
- Tailwind dynamic classes documentation

**Longueur** : ~900 mots

---

### IV. Chiffres publics sur le coût de la maintenance des tests

**Intention** : matérialiser le problème par des chiffres sourcés, pour éviter l'effet "anecdote".

**À rédiger** :
- IV.1 — Atlassian : 150 000 heures de dev/an perdues (source TestDino 2026)
- IV.2 — Slack : de 57% à 4% de failure rate après restructuration
- IV.3 — GitHub : amélioration de 18x
- IV.4 — Ce que ces chiffres signifient pour une équipe de taille moyenne (la conclusion honnête : non duplicable sans budget BigTech)

**Sources à maintenir** :
- testdino.com/blog/playwright-flaky-tests/
- Publications engineering Atlassian, Slack, GitHub (à sourcer précisément)

**Longueur** : ~500 mots

---

### V. Le visual testing comme alternative : ses propres limites, documentées

**Intention** : éviter de présenter le visual testing comme la solution miracle. Documenter honnêtement les problèmes connus.

**À rédiger** :
- V.1 — Le raisonnement alternatif (1999 Perl Test::Visual → 2009 SikuliX → 2013 Applitools → 2015+ Percy, Chromatic, Eggplant)
- V.2 — Les problèmes bien documentés : anti-aliasing, sub-pixel rendering, font OS-dépendant, dynamic content, accumulation de faux positifs
- V.3 — Les trois types de mitigations industrielles : thresholds, masking, AI diffing
- V.4 — La gamme de prix des solutions commerciales :
  - Applitools : fourchette publiée sur G2/Vendr
  - Eggplant Keysight : chiffres PeerSpot (16k$ à 45k$)
  - Percy/Chromatic : tarifs publiés
- V.5 — La souveraineté des données (RGPD, secret-défense, air-gapped) : critère souvent ignoré dans les comparatifs

**Sources à maintenir** :
- contextqa.com/blog/visual-regression-testing-ui-stability/
- desplega.ai/blog/deep-dive-7-visual-regression-testing-ui-bugs
- peerspot.com sur Eggplant pricing
- Applitools public pricing page
- main.vitest.dev/guide/browser/visual-regression-testing.html

**Longueur** : ~900 mots

---

### VI. Retour d'expérience : construire un fork SikuliX en 2026 (OculiX)

**Intention** : la section signature de l'article. Présenter OculiX comme cas d'étude technique concret, pas comme produit à vendre. Factuel, sourcé par le repo GitHub public.

**À rédiger par Claude Code Web avec le code sous les yeux** :

- **VI.1 — Le point de départ : SikuliX, ses forces, ses limites en 2024-2025**
  - SikuliX créé au MIT en 2009, repris par RaiMan ensuite
  - L'écosystème installé (documenter les ordres de grandeur : présence sur StackOverflow, tutoriels Selenium, usage comme fallback industriel depuis 15 ans)
  - Les limites techniques accumulées : OpenCV 4.5.4, Java 8, VNC partiel, pas d'Android 12+, OCR Tesseract seulement, DPI non-aware, CI bloqué en Travis
  - L'annonce officielle de RaiMan (2026) : arrêt du développement, fork OculiX béni publiquement

- **VI.2 — Les choix techniques structurants d'OculiX**
  - Migration OpenCV 4.5.4 (JNI, openpnp) → OpenCV 4.10.0 (JNA, via Apertix). Citer le repo apertix, justifier pourquoi JNA.
  - Migration Java 8 → Java 17-25. Justifier par les gains en ASM, la fin du support Java 8 sur macOS Apple Silicon.
  - Refactoring VNC : ZRLE encoding fix, intégration TigerVNC, gestion du full stack remote. Citer commits/PRs.
  - SSH embarqué via JSch pour tunneling ADB et exécution distante.
  - ADB Android 12+ production-ready (vs expérimental chez SikuliX).
  - OCR pluggable : Tesseract conservé, PaddleOCR ajouté comme moteur neuronal (justifier par les benchmarks sur écrans UI, typographies non-latines).
  - DPI cascade à 5 modes : justifier par le problème concret des écrans Retina + multi-monitor + VNC remote.
  - CI/CD : migration Travis → GitHub Actions, 5 plateformes dont QEMU ARM. Citer le workflow public.
  - IDE modernisation : FlatLaf, PR mergée upstream SikuliX avant fork.

- **VI.3 — Retour terrain pondéré (sans nommer le client)**
  - Phrase courte : "Une mission consulting dans le retail, sur environnement POS virtualisé, a servi de banc d'essai réel pour le pipeline OCR+image-matching entre 2022 et 2024. Sur 230+ scénarios automatisés, la fiabilité mesurée dépassait 99% après stabilisation du pipeline."
  - Pas d'encart, pas de mise en avant typographique. Juste mentionné comme validation terrain.
  - Discussion honnête : ce qui a pris du temps à stabiliser, ce qui ne marchait pas au début.

- **VI.4 — Ce que OculiX ne résout pas (limitations assumées)**
  - Smart Visual AI / diff classification : roadmap publique issue oculix-org/Oculix#170, shippée en v4.0
  - Auto-baseline management : à venir, v4.0
  - Modern Recorder : en beta, fonctionnel mais pas battle-tested sur 5 ans comme Eggplant Studio
  - Onboarding fragmenté entre wiki OculiX, doc SikuliX amont, Clean QA Academy
  - Pas de cloud execution grid natif : choix assumé pour la souveraineté

**Sources à citer** :
- github.com/oculix-org/Oculix
- github.com/oculix-org/Oculix/wiki
- Issues publiques : #157, #160, #163, #170, #171
- SikuliX docs pour le baseline technique
- Commits de référence (à sélectionner par Claude Code Web dans le repo)

**Longueur** : ~1500-2000 mots

---

### VII. Cadre de décision : choisir un outil de test en 2026

**Intention** : donner au lecteur un jeu de critères utilisables, pas une recommandation. Montrer qu'un outil qui marche dans un contexte peut être inadapté dans un autre.

**À rédiger** :
- VII.1 — Aucun outil n'est un silver bullet : rappel que chaque approche résout un sous-ensemble des classes de flakes
- VII.2 — Les cinq critères pragmatiques :
  1. Nature de la surface testée (web / desktop / mobile / legacy / remote / régulé)
  2. Rythme de refactor code vs refactor visuel
  3. Contexte réglementaire (RGPD, secret-défense, air-gapped)
  4. Budget disponible
  5. Taille et maturité de l'équipe QA
- VII.3 — Les pièges communs (marketing-driven choice, ignorance des issues officielles, TCO sous-estimé, monolithisme)
- VII.4 — Matrice de décision simple (quel outil pour quel contexte) sous forme de tableau

**Sources à maintenir** :
- Documentations officielles Playwright, Cypress, Selenium, SikuliX/OculiX, Applitools, Percy, BackstopJS, Eggplant

**Longueur** : ~700 mots

---

### VIII. L'approche hybride en pratique

**Intention** : montrer que les équipes matures combinent les outils. Situer OculiX dans un écosystème, pas en concurrence frontale.

**À rédiger** :
- La pyramide de tests moderne actualisée :
  - Unit : Vitest, Jest
  - Integration : Testing Library + MSW
  - E2E DOM-based : Playwright, Cypress
  - Visual regression : Percy, BackstopJS, Chromatic
  - Image-based automation : SikuliX/OculiX, Eggplant (surfaces où le DOM est inaccessible)
  - Accessibility : axe-core, Pa11y
- Pourquoi peu d'équipes mettent ça en place réellement (compétences, budget, temps)
- Comment OculiX peut s'intégrer à cette pyramide (via API Maven Central, via MCP server pour AI agents, via Selenium Grid)
- Pas de recommandation finale

**Longueur** : ~500 mots

---

### IX. Conclusion — Nommer le problème pour pouvoir le discuter

**Intention** : synthèse finale. Rappeler les 4 classes de flakes. Laisser le lecteur tirer ses conclusions.

**À rédiger** :
- Rappel des 4 classes de flakes
- Aucun outil ne les résout toutes simultanément
- Invitation à mesurer sur sa propre codebase plutôt qu'à faire confiance au marketing (y compris le sien)
- Position personnelle assumée : mainteneur d'OculiX, mais l'outil n'est qu'une expérimentation dans un écosystème plus vaste

**Longueur** : ~400 mots

---

### Sources

Liste complète des URL citées dans l'article, classées par thème :
- Issues GitHub officielles
- Documentations frameworks
- Articles techniques signés
- Sources de pricing (G2, Vendr, PeerSpot)
- Repo OculiX et wiki

---

## Directives transversales pour Claude Code Web

### Ce qu'il doit faire

- **Citer du code réel** quand il discute OculiX : signatures de méthodes, constantes, noms de classes, numéros d'issues précis. Pas de pseudo-code.
- **Montrer des commits de référence** quand pertinent (ex. "le passage à OpenCV 4.10 a été réalisé dans le commit abc123, qui touche 47 fichiers")
- **Documenter ce qui ne marche pas encore** sans l'atténuer. La section VI.4 doit être aussi sérieuse que les autres.
- **Rester dans le ton engineering blog**. Pas de superlatifs, pas de "révolutionnaire", pas de "game-changer". Du factuel.
- **Sourcer les ordres de grandeur SikuliX/communauté** si possible (Sourceforge download count, StackOverflow tag stats, contributions GitHub).

### Ce qu'il ne doit pas faire

- Pas de tableau comparatif "OculiX vs Applitools vs Eggplant" frontal. Les chiffres de prix doivent apparaître de façon neutre dans le corps du texte, pas en tableau de vente.
- Pas de roadmap futuriste. Ce qui est mentionné doit pointer vers une issue GitHub publique existante.
- Pas de CTA ("essayez OculiX", "contactez-moi"). L'article n'appelle pas à l'action commerciale.
- Pas de mention du SaaS QA OPS LAB sauf en passant dans la section VIII comme exemple d'intégration management.
- Pas de mention du livre *Scrum est mort*, de la newsletter, de la Clean QA Academy dans le corps. Uniquement dans la bio auteur en signature.

### Structure de la bio finale

> *Julien Mer, QA Architect indépendant basé à Bourgoin-Jallieu (France). 20 ans d'expérience en test automation dans la défense, la biotech, l'aerospace et le retail. Mainteneur du fork OculiX (github.com/oculix-org/Oculix) et consultant freelance. Cet article est publié sous licence Creative Commons BY-SA 4.0.*

---

## Vérifications finales avant publication

- [ ] Toutes les URL citées retournent un 200 (au moins au moment de la rédaction)
- [ ] Tous les numéros d'issues GitHub pointent vers des issues réelles et ouvertes (ou fermées avec explication)
- [ ] Aucun chiffre donné sans source
- [ ] Aucune phrase promotionnelle non justifiée par un fait technique
- [ ] Passage de lecture par une personne tierce pour vérifier qu'elle ne perçoit pas l'article comme un pitch déguisé
- [ ] Passage de lecture par un grammar checker FR
- [ ] Les noms de concurrents (Playwright, Applitools, Eggplant, Cypress, Percy) sont toujours cités avec respect, via leurs sources officielles
