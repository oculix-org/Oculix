[← OculiX Runner overview](README.md) · Next: [Best practices →](best-practices.md)

# Get started with OculiX Runner

## Requirements

| Item | Detail |
|---|---|
| Docker with Compose | Linux host, WSL 2 or Docker Desktop; the image is `linux/amd64` |
| The runner image | Delivered with your license, see [Docker image](docker-image.md) |
| Resources | 2 CPU and 2 GB of memory per runner instance; the warm JVM uses 300 to 500 MB |
| A VNC target | Optional for the first run; required to drive anything |
| On the calling side | `curl`, and `jq` to read ids from responses |

## Deploy

A minimal `docker-compose.yml`:

```yaml
services:
  oculix-runner:
    image: <the image you were given>
    container_name: oculix-runner
    environment:
      RUNNER_HTTP_PORT: "8765"
      RUNNER_BOOTSTRAP_KEY: ${RUNNER_BOOTSTRAP_KEY:-}
    ports:
      - "8765:8765"
    volumes:
      - runner-data:/workdir
    mem_limit: 2g
    cpus: 2.0

volumes:
  runner-data:
```

```bash
export RUNNER_BOOTSTRAP_KEY='orx_replace_with_your_own_key'
docker compose up -d oculix-runner
curl -fsS http://localhost:8765/health
```

The API answers at once; the engine takes 15 to 20 s to load OculiX. `/health` reports
`"engine":"starting"` then `"engine":"ready"`. Runs submitted before that wait in the queue.

The bootstrap key seeds the base **only when it holds no key**. Without it, an admin key is
generated at first start and printed once in the container log. Only the SHA-256 of a key is
stored; keep the clear value on the calling side.

## First run, without a target

```bash
B='http://localhost:8765'
K="X-Api-Key: ${RUNNER_BOOTSTRAP_KEY}"

PROJECT_ID=$(curl -fsS -X POST "$B/projects" -H "$K" -H 'Content-Type: application/json' \
  -d '{"code":"quickstart","name":"Quick start"}' | jq -er '.id')

RUN_ID=$(jq -n --argjson project_id "$PROJECT_ID" \
  '{project_id: $project_id, name: "First run",
    params: {message: "Hello from OculiX Runner"},
    code: "step(\"Greeting\", \"START\")\nprint(PARAMS[\"message\"])\nassert PARAMS[\"message\"]\nstep(\"Greeting\", \"PASS\")\n"}' \
  | curl -fsS -X POST "$B/runs" -H "$K" -H 'Content-Type: application/json' --data-binary @- \
  | jq -er '.id')

curl -fsSN -H "$K" "$B/runs/$RUN_ID/log/stream"
```

The stream prints every line as the script emits it and closes with the verdict:

```
[debug] Runner: runscript: running script: /workdir/runs/run_1.sikuli/run_1.py
@@STEP|Greeting|START|
Hello from OculiX Runner
@@STEP|Greeting|PASS|
--- run 1 passed (612 ms)
```

## First run against a VNC target

Register the target as the runner sees it, from inside the container network, on the VNC port,
not a noVNC browser port:

```bash
TARGET_ID=$(curl -fsS -X POST "$B/projects/$PROJECT_ID/targets" -H "$K" -H 'Content-Type: application/json' \
  -d '{"name":"desktop","kind":"vnc","host":"your-vnc-host","port":5900,"stage":"INT"}' | jq -er '.id')
curl -fsS -H "$K" "$B/targets/$TARGET_ID/check"
```

`check` opens a TCP connection and stores the result on the target; it does not authenticate.

Save a script, `capture-check.py`. It is ordinary OculiX Jython; the runner prepends a header
that provides `TARGET`, `PARAMS`, `RUN` and `step()`:

```python
from org.sikuli.vnc import VNCScreen

step("VNC capture", "START")
vnc = VNCScreen.start(TARGET["host"], TARGET["port"], 10, 0)
try:
    if vnc is None or not vnc.isRunning():
        raise RuntimeError("VNC connection failed")
    image = vnc.capture().getImage()
    assert image.getWidth() > 0 and image.getHeight() > 0, "Empty capture"
    print("Captured %s x %s" % (image.getWidth(), image.getHeight()))
    step("VNC capture", "PASS")
except Exception as error:
    step("VNC capture", "FAIL", str(error))
    raise
finally:
    if vnc is not None:
        vnc.stop()
```

Upload it as is and run it:

```bash
SCRIPT_ID=$(curl -fsS -X POST "$B/projects/$PROJECT_ID/scripts/raw?name=capture-check" \
  -H "$K" --data-binary @capture-check.py | jq -er '.id')

RUN_ID=$(jq -n --argjson p "$PROJECT_ID" --argjson s "$SCRIPT_ID" --argjson t "$TARGET_ID" \
  '{project_id: $p, script_id: $s, target_id: $t, timeout_ms: 60000}' \
  | curl -fsS -X POST "$B/runs" -H "$K" -H 'Content-Type: application/json' --data-binary @- \
  | jq -er '.id')

curl -fsSN -H "$K" "$B/runs/$RUN_ID/log/stream"
```

For an authenticated VNC server, set `secret_ref` on the target to the name of an environment
variable of the runner container; the header resolves it into `TARGET["password"]`. Passwords never
enter the base.

## Reading the verdict

The run's status is the verdict. A step declared `FAIL` does not fail the run by itself: an
exception, an `assert`, or a non-zero `sys.exit()` does.

| Status | Meaning | Exit code of `oculix-run.sh` |
|---|---|---|
| `passed` | the script returned exit code 0 | 0 |
| `failed` | exception or non-zero exit; `error_line` is the line in your script | 1 |
| `timeout` | `timeout_ms` elapsed, the script was interrupted | 2 |
| `aborted` | cancelled through the API | 3 |
| `error` | the service itself failed, or was restarted mid-run | 4 |
| `skipped` | an earlier run of the suite failed | 5 |

`oculix-run.sh` is the helper script of the [CI recipes](ci-recipes.md): it submits, waits and
exits with the code above, so a job fails when the test fails.

```bash
curl -fsS -H "$K" "$B/runs/$RUN_ID" | jq '{status, exit_code, error, error_line, duration_ms, steps}'
```

## What is kept

Everything lives in `/workdir`, on the `runner-data` volume:

| Path | Content |
|---|---|
| `runner.db` | projects, targets, scripts, suites, runs, log lines, steps, artifacts, keys, audit |
| `runs/run_<id>.sikuli/` | the composed script, header included, and `run.json` |
| `artifacts/<project>/<run>/script.py` | the exact text that executed, kept even if the stored script changes later |

Back up `runner.db` and `artifacts/` together.

---

[← OculiX Runner overview](README.md) · Next: [Best practices →](best-practices.md)
