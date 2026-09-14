[← OculiX Runner overview](README.md) · Previous: [Docker image](docker-image.md)

# CI recipes for OculiX Runner

Every recipe does the same three things: submit, wait, turn the status into the job's exit code.
They share one helper script.

## `oculix-run.sh`

```bash
#!/usr/bin/env bash
# Usage: oculix-run.sh <script_id> <target_id> [timeout_ms]
# Env:   RUNNER_URL, RUNNER_KEY, PROJECT_CODE
# Exit:  0 passed, 1 failed, 2 timeout, 3 aborted, 4 error, 5 skipped, 6 could not submit
set -euo pipefail
B="${RUNNER_URL:?}"; K="X-Api-Key: ${RUNNER_KEY:?}"
SCRIPT_ID="$1"; TARGET_ID="$2"; TIMEOUT="${3:-600000}"

RUN_ID=$(jq -n --arg code "${PROJECT_CODE:?}" --argjson s "$SCRIPT_ID" --argjson t "$TARGET_ID" --argjson to "$TIMEOUT" \
  '{project_code: $code, script_id: $s, target_id: $t, timeout_ms: $to}' \
  | curl -fsS -X POST "$B/runs" -H "$K" -H 'Content-Type: application/json' --data-binary @- \
  | jq -er '.id') || exit 6
echo "run $RUN_ID submitted"

curl -fsSN -H "$K" "$B/runs/$RUN_ID/log/stream"

STATUS=$(curl -fsS -H "$K" "$B/runs/$RUN_ID" | jq -er '.status')
case "$STATUS" in
  passed)  exit 0 ;;
  failed)  exit 1 ;;
  timeout) exit 2 ;;
  aborted) exit 3 ;;
  error)   exit 4 ;;
  skipped) exit 5 ;;
  *)       echo "unexpected status: $STATUS"; exit 4 ;;
esac
```

For a suite, replace the submission with `POST /suites/{id}/run`, poll `GET /suite-runs/{id}` until
its `status` leaves `queued` and `running`, then map it the same way; the per-run logs are in
`runs[]`.

## GitHub Actions

```yaml
name: visual-checks
on:
  workflow_dispatch:
  schedule:
    - cron: "0 3 * * *"

jobs:
  tso-logon:
    runs-on: [self-hosted, oculix]        # a runner that reaches the OculiX Runner
    env:
      RUNNER_URL: http://oculix-runner:8765
      RUNNER_KEY: ${{ secrets.OCULIX_RUNNER_KEY }}
      PROJECT_CODE: lab
    steps:
      - uses: actions/checkout@v4
      - name: Upload the script as a new version
        run: |
          curl -fsS -X PUT "$RUNNER_URL/scripts/3/content" \
            -H "X-Api-Key: $RUNNER_KEY" --data-binary @scripts/tk5-type.py
      - name: Run it
        run: bash ci/oculix-run.sh 3 1 120000
      - name: Keep the log
        if: always()
        run: |
          curl -fsS -H "X-Api-Key: $RUNNER_KEY" "$RUNNER_URL/runs?project_id=1&limit=1" \
            | jq -r '.[0].id' | xargs -I{} curl -fsS -H "X-Api-Key: $RUNNER_KEY" \
            "$RUNNER_URL/runs/{}/log" > run-log.json
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: oculix-run-log, path: run-log.json }
```

## GitLab CI

```yaml
visual-checks:
  stage: test
  image: curlimages/curl:latest
  tags: [oculix]
  variables:
    RUNNER_URL: "http://oculix-runner:8765"
    PROJECT_CODE: "lab"
  before_script:
    - apk add --no-cache bash jq
  script:
    - curl -fsS -X PUT "$RUNNER_URL/scripts/3/content" -H "X-Api-Key: $RUNNER_KEY" --data-binary @scripts/tk5-type.py
    - bash ci/oculix-run.sh 3 1 120000
  artifacts:
    when: always
    paths: [run-log.json]
```

`RUNNER_KEY` is a masked CI variable.

## Jenkins

```groovy
pipeline {
  agent { label 'oculix' }
  environment {
    RUNNER_URL   = 'http://oculix-runner:8765'
    RUNNER_KEY   = credentials('oculix-runner-key')
    PROJECT_CODE = 'lab'
  }
  stages {
    stage('Upload') {
      steps { sh 'curl -fsS -X PUT "$RUNNER_URL/scripts/3/content" -H "X-Api-Key: $RUNNER_KEY" --data-binary @scripts/tk5-type.py' }
    }
    stage('Run') {
      steps { sh 'bash ci/oculix-run.sh 3 1 120000' }
    }
  }
  post {
    always {
      sh 'curl -fsS -H "X-Api-Key: $RUNNER_KEY" "$RUNNER_URL/runs?project_id=1&limit=1" | jq -r ".[0].id" | xargs -I{} curl -fsS -H "X-Api-Key: $RUNNER_KEY" "$RUNNER_URL/runs/{}/log" > run-log.json'
      archiveArtifacts artifacts: 'run-log.json'
    }
  }
}
```

## Notes that save an afternoon

- The job must reach the runner **and** the runner must reach the target. The CI agent does not
  need to see the target at all.
- Upload the script from the repository at each job with `PUT /scripts/{id}/content`: the runner
  keeps the version history by hash, the repository keeps the text.
- Set `timeout_ms`. A CI job that waits forever is worse than one that fails.
- Keep `run-log.json`: it is complete, timestamped, and it is what a reviewer will ask for.
- Use one key per pipeline, scope `run`, named after it; the audit journal will thank you.

---

[← OculiX Runner overview](README.md) · Previous: [Docker image](docker-image.md)
