[← OculiX Runner overview](README.md) · Previous: [Best practices](best-practices.md) · Next: [Docker image →](docker-image.md)

# API reference for OculiX Runner

## General form

```
<METHOD> http://<runner>:8765/<route>
X-Api-Key: <key>
Content-Type: application/json
```

Every response is JSON except the script content (`text/x-python`), the streamed log
(`text/plain`) and artifact downloads (their own type). Ids are integers. Timestamps are ISO-8601
UTC with milliseconds.

## Authentication

Every route except `GET /health` requires `X-Api-Key`. Scopes are hierarchical:

| Scope | Grants |
|---|---|
| `read` | every `GET` |
| `run` | `read`, plus scripts, suites, runs and abort |
| `admin` | `run`, plus projects, targets, keys and audit |

## Errors

| Status | Meaning | Body |
|---|---|---|
| 400 | missing or invalid field | `{"error":"give either script_id or code"}` |
| 401 | no key or unknown key | `{"error":"X-Api-Key header required"}` |
| 403 | key lacks the scope | `{"error":"key 'ci-reader' lacks scope run"}` |
| 404 | unknown id | `{"error":"run not found"}` |
| 409 | conflicts with the current state | `{"error":"run is already passed"}` |
| 410 | artifact file gone from disk | `{"error":"artifact file is gone: script.py"}` |

## Routes

### Health and runtime

| Route | Scope | Arguments | Returns |
|---|---|---|---|
| `GET /health` | none | | `status`, `engine` (`starting`, `ready`, `failed`), `running_run_id`, `queued` |
| `GET /version` | read | | `service`, `oculix_version`, `oculix_jar`, `jar_sha256`, `java`, `engine` |
| `GET /engine/events` | read | `limit` (query, default 50) | engine events, newest first |

### Projects

| Route | Scope | Arguments | Returns |
|---|---|---|---|
| `POST /projects` | admin | `code` (required, `[A-Za-z0-9_-]{1,32}`, unique), `name` (required), `description` | the project |
| `GET /projects` | read | | all projects |
| `GET /projects/{id}` | read | | the project |

### Targets

| Route | Scope | Arguments | Returns |
|---|---|---|---|
| `POST /projects/{id}/targets` | admin | `name` (required, unique in the project), `kind` (`vnc` default, or `local`), `host` (required for `vnc`), `port`, `display`, `stage`, `secret_ref` | the target |
| `GET /projects/{id}/targets` | read | | targets of the project |
| `GET /targets/{id}` | read | | the target, with `last_check_at` and `last_check_result` |
| `PUT /targets/{id}` | admin | any of the fields above, plus `status` | the target |
| `GET /targets/{id}/check` | run | | `ok`, `result`: TCP connection to `host:port`, 3 s timeout |

### Scripts

| Route | Scope | Arguments | Returns |
|---|---|---|---|
| `POST /projects/{id}/scripts` | run | JSON: `name` (required, unique), `content` (required), `external_ref` | script metadata |
| `POST /projects/{id}/scripts/raw` | run | query `name` (required), `external_ref`; body = the `.py` | script metadata |
| `GET /projects/{id}/scripts` | read | | metadata of the project's scripts |
| `GET /scripts/{id}` | read | | metadata: `sha256`, `version`, dates |
| `GET /scripts/{id}/content` | read | | the text, `text/x-python` |
| `PUT /scripts/{id}` | run | JSON: `name`, `content`, `external_ref` | metadata; `version` increments when the content changed |
| `PUT /scripts/{id}/content` | run | body = the `.py` | metadata |

### Runs

| Route | Scope | Arguments | Returns |
|---|---|---|---|
| `POST /runs` | run | `project_id` or `project_code` (one required); `script_id` or `code` (one required); `target_id`; `params` (object); `timeout_ms`; `name` | the run, `status` `queued` |
| `GET /runs` | read | query `project_id`, `status`, `limit` (default 50) | runs, newest first |
| `GET /runs/{id}` | read | | the run, plus `line_count` and `steps` |
| `GET /runs/{id}/log` | read | query `after` (sequence number, default 0), `wait` (seconds, max 60) | `status`, `finished`, `next`, `lines[{seq, at, line}]` |
| `GET /runs/{id}/log/stream` | read | | plain text until the run ends, closed by `--- run <id> <status> (<ms> ms)` |
| `GET /runs/{id}/steps` | read | | steps declared by the script |
| `GET /runs/{id}/artifacts` | read | | files of the run |
| `GET /artifacts/{id}` | read | | the file, with its content type |
| `POST /runs/{id}/abort` | run | | `outcome`: `removed from queue` or `abort requested`; 409 if finished |

Run statuses: `queued`, `running`, `passed`, `failed`, `timeout`, `aborted`, `error`, `skipped`.
Fields worth reading on a finished run: `exit_code`, `error`, `error_line` (line in your script),
`duration_ms`, `oculix_version`, `jar_sha256`, `script_sha256`.

### Suites

| Route | Scope | Arguments | Returns |
|---|---|---|---|
| `POST /projects/{id}/suites` | run | `name` (required, unique), `description`, `items[]`: `script_id` (required), `target_id`, `params`, `continue_on_failure` | the suite with its items |
| `GET /projects/{id}/suites` | read | | suites of the project |
| `GET /suites/{id}` | read | | the suite with its items |
| `PUT /suites/{id}/items` | run | `items[]` as above, replaces all | the suite |
| `POST /suites/{id}/run` | run | `branch`, `commit_sha`, `retry_of` | the suite run with one queued run per item |
| `GET /projects/{id}/suite-runs` | read | `limit` | suite runs, newest first |
| `GET /suite-runs/{id}` | read | | the suite run with its runs |
| `POST /suite-runs/{id}/abort` | run | | aborts queued runs and the running one |

Suite run statuses: `queued`, `running`, `passed`, `failed`, `aborted`. When a run does not pass
and its item has no `continue_on_failure`, the remaining runs become `skipped`.

### Keys and audit

| Route | Scope | Arguments | Returns |
|---|---|---|---|
| `POST /keys` | admin | `name` (required, unique), `scopes` (comma-separated, default `read`) | the key, with `key` in clear **once** |
| `GET /keys` | admin | | keys without their secret |
| `DELETE /keys/{id}` | admin | | revoked; a key cannot revoke itself |
| `GET /audit` | admin | `limit` (default 100) | actions, newest first: `actor`, `action`, `entity`, `entity_id`, `detail` |

## Following a run

Two ways, for two consumers:

- **A terminal or a CI log**: `GET /runs/{id}/log/stream`, with `curl -N`. Lines arrive as they are
  produced; the last line is the verdict.
- **A program**: `GET /runs/{id}/log?after=<next>&wait=30` in a loop. Each answer carries `next`, to
  pass back, and `finished`; the call returns as soon as new lines exist or after `wait` seconds.

```bash
NEXT=0
until [ "$FINISHED" = true ]; do
  R=$(curl -fsS -H "$K" "$B/runs/$RUN_ID/log?after=$NEXT&wait=30")
  echo "$R" | jq -r '.lines[].line'
  NEXT=$(echo "$R" | jq -r '.next'); FINISHED=$(echo "$R" | jq -r '.finished')
done
```

## What the script receives

The runner prepends a header to every script. It imports `sikuli` and defines:

| Name | Content |
|---|---|
| `RUN` | `id`, `name`, `params`, `target` of the run |
| `PARAMS` | the object passed as `params`, or `{}` |
| `TARGET` | `name`, `kind`, `host`, `port`, `display`, `secret_ref`, and `password` resolved from the environment variable named by `secret_ref` |
| `step(label, status="PASS", detail="")` | declares a step: `START`, `PASS`, `FAIL`, `SKIP`, `INFO`; a `START` followed by `PASS` or `FAIL` with the same label closes it; still open at the end becomes `INCOMPLETE` |

`TARGET_VNC_HOST` and `TARGET_VNC_PORT` are also set in `os.environ` for scripts written before the
runner. Error line numbers reported by the API are those of your script, header excluded.

---

[← OculiX Runner overview](README.md) · Previous: [Best practices](best-practices.md) · Next: [Docker image →](docker-image.md)
