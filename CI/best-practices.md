[← OculiX Runner overview](README.md) · Previous: [Get started](get-started.md) · Next: [API reference →](api-reference.md)

# Best practices for scalable and secure testing with OculiX Runner

## Scenario

A team starts with a handful of scripts run from the IDE. A year later there are three hundred
checks against two mainframe environments and forty tills, they must run every night and after
every deployment, and nobody can sit in front of an IDE at 3 a.m. The runner is the answer to that
year; this page is how to size it, split the work and keep it safe.

## The core facts to design around

| Fact | Consequence |
|---|---|
| One runner executes **one script at a time** | parallelism = several runner containers, not threads |
| The engine is warm | script start costs under a second; the target's own delays dominate |
| A warm JVM uses 300 to 500 MB | ten runners fit in 8 GB, plus the targets |
| The queue is the base | a restart keeps queued runs; a run caught mid-flight becomes `error` |
| Targets are rows | a runner can address many targets; a target can be shared by several runners if it accepts several VNC clients |
| No agent on the target | the runner needs the VNC port and nothing else, air-gapped networks included |

## Isolation and security

- **One container, one engine, no phone-home.** The runner never calls out; it works in restricted
  or air-gapped zones. Its only outbound connections are the VNC sessions your scripts open.
- **TLS in front.** The API is plain HTTP inside the network. Put a reverse proxy with a certificate
  in front, Caddy, Traefik or nginx, before exposing it beyond the Docker network. Keys travel in a
  header; without TLS they travel in clear.
- **Least scope per key.** `read` for dashboards and reporting, `run` for pipelines, `admin` for the
  person who creates projects and targets. One key per consumer, named after it, so the audit
  journal says who did what. Revoke with `DELETE /keys/{id}`; a key cannot revoke itself.
- **Secrets by reference.** A VNC password is an environment variable of the container, named in
  the target's `secret_ref`, resolved into `TARGET["password"]` at run time. Never write a password
  in a script or a parameter: parameters are stored with the run.
- **Read-only where possible.** Mount `jars/` and the compose file read-only; only `/workdir` needs
  to be writable.
- **Back up the volume.** `runner.db` and `artifacts/` are the complete history. Copy them together,
  with the service stopped or with `sqlite3 .backup`.

## Sizing

| Runners | Memory | Use |
|---|---|---|
| 1 | 2 GB | a team, nightly suites, a few targets |
| 3 to 5 | 8 to 12 GB | one runner per environment or target family, parallel nightly runs |
| 10+ | 24 GB and more | a farm; one runner per rack of tills or per mainframe partition |

Give each container `mem_limit` and `cpus`; 2 GB and 2 CPU per runner is comfortable. Do not
rely on the container's restart policy to recover from a host that ran out of memory: size the host
so it does not.

## Partition the work

- **One project per system under test**, its targets inside. Codes are short and stable: they name
  the artifacts folder.
- **Suites of 10 to 20 items.** A suite stops at the first failure unless an item has
  `continue_on_failure`; smaller suites retry faster and localize failures. Chain them from the CI
  job rather than building one suite of two hundred scripts.
- **One runner per target family.** Mainframe scripts wait on 3270 latencies, till scripts on
  receipts printing: mixing them in one queue makes the fast ones wait for the slow ones. Separate
  runners, separate queues, the same base schema.
- **Stagger starts.** When several runners target the same VNC server, start their suites a few
  seconds apart; a VNC server handling ten simultaneous connections is slower than ten spread over a
  minute.

## Timeouts and control

- **Always set `timeout_ms`** on runs launched from CI, or `RUNNER_DEFAULT_TIMEOUT_MS` on the
  service. A script waiting for a screen that never comes must not hold the queue.
- **Abort is immediate** for a queued run and takes about a second for a running one:
  `POST /runs/{id}/abort`, `POST /suite-runs/{id}/abort`.
- **Retry with `retry_of`** on a suite run to keep the link between attempts.
- **Verdict = status**, never the HTTP code. `200` means the request was accepted; `passed` means
  the test passed. A step declared `FAIL` does not fail the run: raise, assert or exit non-zero.

## Scripts that behave in a queue

- Open the VNC session in the script, close it in `finally`. The runner does not close what a script
  leaves open.
- Declare steps with `step()`: they are what a reviewer reads first, and what a video shows.
- Use `PARAMS` for what changes between runs, users, amounts, environments, instead of copies of
  the script.
- Reference images by absolute path from a shared folder mounted in the container, the way one names
  selectors; never by bare file name.
- Do not print secrets. Every line printed is stored.

## Observability

- `GET /health` for liveness: `engine` must be `ready`, `queued` says how deep the queue is.
- `GET /engine/events` for the engine's life: start, natives, readiness, failures.
- `GET /audit` for who did what, `GET /runs?status=failed` for what to look at first.
- The log of a run is complete and timestamped; export it with `GET /runs/{id}/log` and keep it
  with the job's artifacts.

---

[← OculiX Runner overview](README.md) · Previous: [Get started](get-started.md) · Next: [API reference →](api-reference.md)
