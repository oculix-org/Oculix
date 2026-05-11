<!--
  Thanks for opening a PR! 🦎
  This template mirrors the checklist documented in CONTRIBUTING.md.
  Delete this comment block before submitting (or don't — we don't mind).
  Sections marked (optional) can be skipped if not applicable.
-->

## Linked issue

<!-- Closes #NNN — or explain why there isn't one. Closed/stale issues don't count. -->

Closes #

## Root cause

<!-- One or two sentences. "Fixes the bug" is not a root cause. -->



## What changed

<!-- High level — what files, what shape of change. Enough for a reviewer to know where to look first. -->



## How you tested it

<!-- OS, Java version, manual steps, test output. If you couldn't test something (e.g. needs Mac and you're on Linux), say so explicitly. -->

- OS:
- Java:
- Manual steps:
- Test command:

## Regressions considered

<!-- What else you checked wasn't broken by the change. Bonus: link the test or run that proves it. -->



## Screenshots (optional)

<!-- Only for visual / IDE / UX changes. Drag & drop images directly into the editor. -->



---

### Checklist

- [ ] PR scope is **one concern**. No drive-by reformatting / renames / unrelated cleanups.
- [ ] Commit messages are short, imperative, no `feat:`/`fix:`/`chore:` prefixes (we reference tickets in the PR body, not the subject).
- [ ] I built locally (`mvn clean install -DskipTests`) and ran the tests for the modules I touched.
- [ ] If I touched the IDE, I launched it and exercised the feature manually — a compile-green PR that crashes on open wastes everyone's time.
- [ ] If this is AI-assisted, I read and understood every line I'm submitting and can explain why this approach.
- [ ] No new runtime dependency added (or, if so, justified in the issue first).
- [ ] No CI / build / release workflow change (or, if so, agreed in the issue first).

<!--
  Quality bar reminder (from CONTRIBUTING.md):
  > We'd rather merge one careful PR a month than triage ten half-baked ones.
  Push one careful PR. Then push another.
-->
