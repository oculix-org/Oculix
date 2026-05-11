<div align="center">

# Contributing to OculiX

**You spotted something. That's already a contribution.**

[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-2ea44f?style=flat-square&logo=github)](../../pulls)
[![Issues](https://img.shields.io/badge/Issues-bug%20reports%20matter-d73a49?style=flat-square&logo=github)](../../issues)
[![Docs](https://img.shields.io/badge/Docs-clarity%20wins-0969da?style=flat-square&logo=readme)](README.md)
[![Security](https://img.shields.io/badge/Security-48h%20acknowledgement-8250df?style=flat-square&logo=shield)](SECURITY.md)
[![License](https://img.shields.io/badge/License-MIT-1f883d?style=flat-square)](LICENSE)

*Bug reports, focused PRs, docs fixes, wrappers, RC testing.*

</div>

> [!TIP]
> Jump straight to what you need:
> [Report a bug](#reporting-a-bug--what-earns-a-same-day-fix) ·
> [Request a feature](#requesting-a-feature) ·
> [Submit a PR](#submitting-a-pull-request) ·
> [Fork workflow](#fork-workflow) ·
> [Development setup](#development-setup) ·
> [Good first contributions](#good-first-contributions) ·
> [Governance](#governance--maintainers)

OculiX is the active continuation of SikuliX1, which was archived in March 2026 after two decades of community work. A lot of that community is still out there running scripts in production — and a lot of them are discovering OculiX one stack trace at a time.

If you're one of them: welcome. This document exists so you know **what we need, what we promise in return, and how to spend as little time as possible navigating GitHub etiquette**.

---

## What we actually need

Not every contribution is code. Ranked roughly from "takes five minutes" to "takes a weekend":

| You can… | Time | What it unlocks |
|---|---|---|
| Open a bug report with a stack trace and OS/Java version | 5 min | A real fix, often same-day (see #15, #162, #163) |
| Share a script that breaks in OculiX but worked in SikuliX | 10 min | Direct regression reproducer, top-priority |
| Test a release candidate on Linux / macOS / Apple Silicon | 15 min | We only have one maintainer's machine; RCs need eyes |
| Correct something in the README or a module README | 30 min | Docs debt is real — every typo fix is appreciated |
| Triage an old issue (repro on your side, suggest labels, ask for missing info) | 30 min | Huge leverage on signal-to-noise |
| Submit a PR for an issue tagged `good first issue` | 1-3 h | See [#good-first-contributions](#good-first-contributions) below |
| Port a feature from SikuliX1 that didn't make it into OculiX | few hours | Direct lineage continuity |
| Build a wrapper (Python, JS, .NET — see #177/#178/#179) | a week+ | Opens OculiX to a new ecosystem |

---

## Reporting a bug — what earns a same-day fix

Bug reports are the #1 currency of this project. If you give us the following, **we commit to a response within 24h and root cause publicly posted before we close the issue**:

1. **OculiX version** (`oculixide-3.0.x-<os>.jar`) and where you got it (release page, Maven, built from source)
2. **OS + Java version** (`java --version` output is ideal)
3. **The minimum script or action that triggers it** — if you can strip it down to 3 lines that reproduce, even better
4. **The full stack trace**, if you have one, inside a fenced code block
5. **Expected vs actual** behaviour

> **Gold standard examples to copy:**
> - [#162](https://github.com/oculix-org/Oculix/issues/162) — @micves gave us a minimal repro, full stack, pointed at the exact line in `SikulixIDE.java`, and suggested a one-line null guard. Fixed same day.
> - [#163](https://github.com/oculix-org/Oculix/issues/163) — @micves attached before/after screenshots plus the corrupted save output. Root cause found in the first hour.
> - [#15](https://github.com/oculix-org/Oculix/issues/15) — @shaworth gave us `UnsatisfiedLinkError` on Ubuntu 24.04 with full env dump. Apertix `4.10.0-1` shipped the next day.

You don't have to hit that bar. A screenshot and "it crashes when I click this" is already useful. **We'd rather have a rough report than no report.**

Security-sensitive bugs go through [SECURITY.md](SECURITY.md) — same idea, 48h acknowledgement.

---

## Requesting a feature

Use the [feature request template](.github/ISSUE_TEMPLATE/feature_request.md). The template is simple on purpose. If your idea is big enough to warrant an RFC, look at [#10](https://github.com/oculix-org/Oculix/issues/10) (Python 3 runtime replacement) — that's the level of context we love but absolutely don't require.

What we'll push back on:

- "OculiX should do X" without any context on what you're trying to automate
- Features that require a server, a cloud account, or a paid API
- Anything that fundamentally couples the core to a single OS

What we'll fast-track:

- Fixes for environments that are hard to get access to (fresh Ubuntu, Apple Silicon, Android 14, specific kiosk hardware)
- Anything that improves reliability of image matching, OCR, or VNC reconnection
- Friction killers for new users (install, first script, first capture)

---

## Submitting a pull request

> **Heads up — quality bar:** this is not a project where you dump a patch and walk away. We move fast because every PR that lands has been thought through. If you're testing the waters with AI-generated code you haven't read, or pushing a drive-by "cleanup" you haven't verified, the PR will be closed with a pointer back to this section. Not hostile — just honest about where the time goes.
>
> We'd rather merge **one careful PR a month** than triage ten half-baked ones.

### Before you open a PR

**Open the issue first.** Unless it's a typo or a one-line obvious fix, we want to agree on the direction before you spend an evening on code. Closed or stale issues don't count — check that someone isn't already on it (`status:in-progress` label, open PR, recent commits).

**Reproduce the bug yourself.** If you're fixing something, you should be able to trigger it on your own machine *before* your patch and confirm it's gone *after*. A fix you can't reproduce is a guess.

**Read the surrounding code.** OculiX inherits 15 years of SikuliX conventions. Some of them are weird, some of them are load-bearing. If your PR ignores a pattern that's used everywhere else, we'll ask why — and "AI didn't know" isn't an answer.

### Fork workflow

External contributions go through the standard GitHub fork + PR flow:

1. Fork [`oculix-org/Oculix`](https://github.com/oculix-org/Oculix) to your own account
2. Clone your fork, add the upstream remote
3. Branch from `upstream/master`, push to your fork
4. Open the PR against `oculix-org/Oculix:master`

One-time setup:

```bash
git clone https://github.com/YOUR_USERNAME/Oculix.git
cd Oculix
git remote add upstream https://github.com/oculix-org/Oculix.git
```

**Write access** to the main repo (no more forking, direct branches) is granted case-by-case to trusted contributors after a few solid PRs. If you'd like it, mention it in your second or third PR — we'll discuss once we've seen how you work. See [Governance & maintainers](#governance--maintainers) for the full picture.

### Branch from `master`

```bash
git fetch upstream
git checkout -b fix/my-short-description upstream/master
```

Name your branch with a prefix: `fix/`, `feat/`, `docs/`, `refactor/`, `ci/`.

When you're done, push to your fork (`git push -u origin fix/my-short-description`) and open the PR.

### Build locally — don't rely on CI to find out it's broken

```bash
mvn clean install -DskipTests
```

Then run the tests for the module(s) you touched:

```bash
mvn -pl API test
```

If you're touching the IDE, **launch it and exercise the feature manually**. A compile-green PR that crashes on open has happened before and it wastes everyone's time.

```bash
java -jar IDE/target/oculixide-<version>-<os>.jar
```

### Scope: one PR, one concern

If your branch grew beyond the original intent, split it. The #165 closing comment is a good example: 11 commits across 8 separate side bugs, each individually traceable to its own issue. That traceability is worth the extra git hygiene.

**Do not** combine a bug fix with an unrelated refactor. **Do not** rename files "while you're there". **Do not** reformat code that isn't part of the fix. Every out-of-scope change multiplies review time and blocks the fix from shipping.

### Commit style

Short, imperative, no ticket number in the subject (we reference tickets in the PR body). Look at the existing log for examples:

```
Fix JVM zombie process on IDE close
Guard imageExists against null FilenameUtils normalize
Force caret/foreground colors over white editor background
```

No `chore: `, `feat: `, `fix: ` prefixes — the log is clean without them.

### PR description — required checklist

Your PR body must include:

- [ ] **Linked issue** (`Closes #NNN`) — or explain why there isn't one
- [ ] **Root cause** in one or two sentences, not just "fixes the bug"
- [ ] **What changed** at a high level — enough for a reviewer to know where to look first
- [ ] **How you tested it** — OS, Java version, manual steps, test output
- [ ] **Regressions considered** — what else you checked wasn't broken by the change
- [ ] **Screenshots** if the change is visual

PRs missing these will get a templated request for them. Not because we love bureaucracy — because without them a review takes an hour instead of ten minutes, and reviewer hours are the bottleneck of this project.

### What we won't merge

- **AI-generated code the submitter can't explain.** Using AI to help is fine — shipping output you didn't read isn't. If a reviewer asks "why this approach?" and the answer is "the model suggested it", that's a close.
- **Patches that silence a symptom without understanding the cause.** Catching an `NPE` and returning `null` to make the stack trace go away is not a fix.
- **Dependency bumps without a reason.** Bumping a version "because it's newer" adds risk and supply-chain surface for zero concrete benefit. If you're fixing a CVE or unblocking a feature, say so.
- **Whitespace / reformatting PRs** across files you otherwise don't touch.
- **Breaking API changes** in `API/` without a deprecation path — even in a major version.
- **PRs that add a new dependency without discussing it in the issue first.** Each dep is a long-term commitment.
- **PRs that touch the build system, CI, or release workflow** without prior agreement in an issue.

None of this is hostile — it's where we've been burned before or where we know the cost is high. When in doubt, ask in the issue.

---

## Development setup

### Prerequisites

| Tool | Minimum | Notes |
|---|---|---|
| Java | 11+ | Tested on 11, 17, 21 |
| Maven | 3.8+ | |
| OpenCV | — | Provided transitively via [Apertix](https://github.com/julienmerconsulting/Apertix), no manual setup |
| Python 3 | optional | Only needed if you work on PaddleOCR integration |

### Clone and build

```bash
git clone https://github.com/oculix-org/Oculix.git
cd Oculix
mvn clean install -DskipTests
```

The first build pulls Apertix from Maven Central and takes 2-5 minutes. Subsequent builds are fast.

### Git hooks — pair-programming credit (one-time per clone)

OculiX uses a `prepare-commit-msg` hook that auto-appends a `Co-authored-by:` trailer when a commit is recorded under the Claude Code author (`noreply@anthropic.com`). This way contributors who pair with Claude get GitHub credit on the contributor graph alongside the AI.

After cloning, enable the project hooks and declare your identity:

```bash
git config core.hooksPath .githooks
git config oculix.coauthor "Your Name <you@example.com>"
```

- The first line points git at the tracked `.githooks/` directory instead of `.git/hooks/`.
- The second line sets the value the hook will inject. Use the same `Name <email>` you commit under directly.
- If `oculix.coauthor` is unset, the hook is a no-op (safe default).
- The trailer is only added when the recorded author is Claude — your direct commits stay untouched.

If you don't pair with Claude on this repo, you can skip this section entirely — no commit you author yourself will be affected by the hook.

### Running the IDE from source

```bash
java -jar IDE/target/oculixide-3.0.2-<os>.jar
```

Replace `<os>` with `windows`, `linux`, or `macos` depending on your platform.

### Running a single test

```bash
mvn -pl API test -Dtest=RegionTest
```

### Module map

| Module | What lives there |
|---|---|
| `API/` | Core engine — `Screen`, `Region`, `Pattern`, `Match`, `App`, VNC stack, ADB stack, OCR engines |
| `IDE/` | Swing-based IDE — workspace, recorder, syntax highlighting, theming |
| `MCP/` | Model Context Protocol server — exposes OculiX as tools for AI agents |
| `Support/` | Build templates, native code (`WinUtil.dll` sources, `MacUtil.m`), Maven deploy scripts |
| `Additional-Wrappers/` | CDCs for Operix (Python), Operix-JS (Node.js), Operix-NET (C#) |
| `docs/` | Product-level CDCs and design docs |

If your change touches `API/`, expect scrutiny on public API stability — downstream users build on it.

---

## How we label and triage

| Label | Meaning |
|---|---|
| `bug` | Something broken relative to documented behaviour |
| `enhancement` | New feature or clear improvement |
| `tech-debt` | Works, but we'd like to clean it up |
| `code-quality` | Warnings, deprecations, small refactors |
| `ide-ui`, `recorder`, `vnc`, `ocr`, `extensions` | Module scope |
| `infrastructure` | Build, CI, Maven, releases |
| `documentation` | Docs, wiki, READMEs, code comments |
| `branding` | Theming, logos, naming |
| `epic` | Multi-issue body of work, usually with child issues |
| `status:backlog` | Acknowledged, not scheduled yet — PRs welcome |
| `status:up-next` | Targeted for the next minor version |
| `status:in-progress` | Someone is actively on it (check assignee first) |
| `done` | Merged, shipped, or closed by retrospective tracking |
| `retrospective` | Issue created after the fact to document already-shipped work |

---

## Good first contributions

If you're looking for a short first PR, these tend to be tractable — **provided you follow the quality bar in the PR section above**:

- **Any `tech-debt` issue** — most are scoped to a single file and have a clear before/after
- **`code-quality` items** — deprecation warnings, varargs reflection warnings, value-based `synchronize` (see the closed #77–#81 for the pattern)
- **`documentation`** — especially updating any SikuliX-era wiki links that still point at the archived repo
- **`ide-ui` polish** — #92 (name redundancy), #93 (Welcome tab layout), #95 (typography) are all visually scoped
- **Translation** — #88 tracks i18n (French + English) for the Modern Recorder

"First contribution" doesn't mean "first time writing Java". It means "first time contributing to this repo". We'd rather you pick a small issue you can own end-to-end — repro, fix, test, explain — than a large one you'll abandon halfway.

If you'd like a `good first issue` label added to a specific ticket, just ask in the issue — we haven't curated the label yet, it's one of the honest gaps in this repo.

---

## What you can expect from us

This isn't aspirational — it's the pattern we've held so far, and we'd rather set the bar here and be held to it:

- **First response within 24h** on any bug report with a clear repro
- **Root cause posted publicly** before an issue is closed, not "fixed in next release"
- **Credit to the reporter** in the fix commit or closing comment
- **Pre-releases cut on request** when you need a specific fix to verify your use case (see #163 → `v3.0.3-rc1`)
- **Walk-backs when we're wrong** — see the second response on #163. If a maintainer reply misses the point, pushing back is welcome and will be taken seriously

What we **won't** do:

- Pretend a report is invalid just because we can't reproduce it — we'll ask for more info instead
- Merge a breaking API change without a deprecation path, even in a major version
- Close an issue because it's "old" — old issues get picked up, not swept out

---

## Governance & maintainers

OculiX has two maintainers with full trust on the repository, listed in [`.github/CODEOWNERS`](.github/CODEOWNERS). Either's approval is sufficient to merge, subject to branch protection (required CI green, no direct push to `master`).

| Role | Who | Scope |
|---|---|---|
| **Active maintainer** | [@julienmerconsulting](https://github.com/julienmerconsulting) | Day-to-day reviews, releases, issue triage, roadmap |
| **Emeritus / lineage authority** | [@RaiMan](https://github.com/RaiMan) | Original SikuliX author. Advisory, API stability, historical context |

There are no other maintainers today. If that changes, this section is where it'll be updated — and the promoted contributor will appear in `CODEOWNERS` with either repo-wide or module-scoped trust.

**Becoming a maintainer** isn't a formal process. It's a conversation that opens once a contributor has shipped a handful of solid PRs, triaged issues thoughtfully, and shown judgment on what belongs in the project. If that's you: keep pushing careful PRs and the rest happens on its own.

**Disagreements between maintainers** are resolved by discussion in the issue or PR thread, in public. Nothing gets merged by executive decision with the other maintainer objecting. If we can't agree, the status quo wins until the argument is resolved.

---

## Architecture & design decisions

Substantial architectural choices are documented in `docs/` and in the per-module `README.md` files. When you propose a change that touches:

- **Threading / concurrency** in `API/` — read how `VNCScreen` handles parallel sessions via `ThreadLocalSecurityClient` first
- **Native library loading** — `Commons.loadOpenCV()` is the single funnel; don't add `System.loadLibrary` calls
- **IDE theming** — the `ThemeAware` interface introduced in #165 is the way; don't rely on `FlatLaf.updateUI()` alone
- **Script runner registration** — follow the `IRunner` / `AbstractRunner` pattern (see `JythonRunner`, `PowerShellRunner`)

If you're unsure, open an issue and ask — a 5-minute question saves a rewrite.

---

## Code of conduct (the short version)

- Be kind. If you catch yourself typing a sharp comment, close the tab for five minutes.
- Stay technical. Disagreements are about code, not people.
- Credit others. If someone else's report or commit helped, say so.
- Remember the context: most people here are QA engineers doing real work in constrained environments (kiosks, POS, legacy apps). They don't have the time to parse your sarcasm.

We don't have a formal CoC document yet. If a conflict ever needs escalation, contact the maintainer directly via [GitHub profile](https://github.com/julienmerconsulting).

---

## License and copyright

OculiX is MIT-licensed, same as the original SikuliX. By submitting a pull request, you agree that your contribution is licensed under the same terms. You retain copyright on your work — we don't require a CLA.

If your employer has IP claims on what you write on their time, please clear it with them before submitting. This has bitten open-source projects before.

---

## Lineage

This project stands on two decades of work by Raimund Hocke ([@RaiMan](https://github.com/RaiMan)) on SikuliX1, handed over explicitly in #16. If you're reporting a bug you also hit on SikuliX, mentioning the original SikuliX issue number (e.g. `RaiMan/SikuliX1#570`) helps us see the whole history.

---

## One last thing

If you've read this far, thank you. The maintainer is one person, in one timezone, with a day job. The reason this project feels alive isn't hustle — it's that people like you keep pushing bug reports, patches, and awkward questions through the door.

The bar is high on purpose. **That's a feature, not a filter against you.** It's the thing that keeps reviews fast, merges clean, and this project worth contributing to in the first place. Clear it once and you're welcome to clear it again — most of our strongest contributors started with a single careful PR.

Push one more.
