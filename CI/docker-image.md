[← OculiX Runner overview](README.md) · Previous: [API reference](api-reference.md) · Next: [CI recipes →](ci-recipes.md)

# OculiX Runner Docker image

## What the image contains

| Layer | Content |
|---|---|
| Base | Eclipse Temurin JRE 17 on Ubuntu jammy, `linux/amd64` |
| Display | Xvfb, xauth and the X libraries OculiX's natives expect; one virtual display for the life of the service |
| OculiX | the OculiX IDE jar for Linux, `/opt/oculix/oculix.jar`, SHA-256 verified at build |
| Service | `/opt/oculix/oculix-runner-service.jar`, the HTTP API and engine |
| Entrypoint | starts Xvfb, then `exec`s the JVM as PID 1, so `docker stop` is a clean shutdown |

Size: about 690 MB. No Python, no browser, no OCR engine other than the one OculiX embeds.

The image is delivered with your license; it is not on a public registry.

## Run it

With Compose, see [Get started](get-started.md). With `docker run`:

```bash
docker run -d --name oculix-runner \
  -p 8765:8765 \
  -v runner-data:/workdir \
  -e RUNNER_BOOTSTRAP_KEY=orx_replace_with_your_own_key \
  --memory 2g --cpus 2 \
  <the image you were given>
```

The container is `healthy` once `/health` reports `"engine":"ready"`, 15 to 20 s after start on a
warm host, up to a minute the first time while the OculiX natives are extracted.

## Environment variables

| Variable | Default | Role |
|---|---|---|
| `RUNNER_HTTP_PORT` | `8765` | HTTP port inside the container |
| `RUNNER_DATA_DIR` | `/workdir` | base, composed scripts, artifacts |
| `OCULIX_JAR` | `/opt/oculix/oculix.jar` | the jar the service loads |
| `RUNNER_DEBUG_LEVEL` | `3` | OculiX debug level, as `-d` on the command line |
| `RUNNER_DEFAULT_TIMEOUT_MS` | `0` | timeout applied to runs that do not set `timeout_ms`; `0` = none |
| `RUNNER_BOOTSTRAP_KEY` | generated | admin key created at first start when the base has no key |
| `RUNNER_DISPLAY_NUM` | `99` | Xvfb display number |
| `JAVA_OPTS` | empty | extra JVM options, `-Xmx` for instance |
| any name used as `secret_ref` | | VNC passwords, resolved by the script header, never stored |

## Volumes and ports

| Mount or port | Role |
|---|---|
| `/workdir` | everything the runner keeps: `runner.db`, `runs/`, `artifacts/`. Mount a named volume or a host folder; back it up |
| `8765/tcp` | the API |

Nothing else is written outside `/workdir` and `/tmp`.

## The OculiX jar

The service and OculiX are two jars on one classpath; OculiX is not modified. The image pins one
OculiX release and checks its SHA-256 at build. To run another jar, a release candidate or a build
of your own, mount it over the pinned one:

```bash
docker run ... -v /path/to/oculixide-x.y.z-linux.jar:/opt/oculix/oculix.jar:ro <image>
```

`GET /version` reports the version and SHA-256 of the jar actually loaded, and every run records
them.

## Behaviour to know

- **One run at a time per container.** The Jython interpreter inside OculiX is a singleton; the
  service serializes runs. Parallelism is several containers.
- **Restart.** Queued runs stay queued. A run that was `running` when the container stopped is
  marked `error` at the next start; the script is not resumed.
- **Timezone.** The service stamps in UTC. MVS or any target keeps its own clock; the log shows both.
- **User.** The service runs as root inside the container; files on a host-mounted `/workdir` belong
  to root. Prefer a named volume.
- **Architecture.** `linux/amd64` only: OculiX's Linux natives, OpenCV and Tesseract, are built for
  x86-64.

## Compose with a reverse proxy

Expose the API beyond the Docker network only through TLS. A Caddy service in front, the runner
without a published port:

```yaml
services:
  proxy:
    image: caddy:2
    ports: ["443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy-data:/data
  oculix-runner:
    image: <the image you were given>
    expose: ["8765"]
    environment:
      RUNNER_BOOTSTRAP_KEY: ${RUNNER_BOOTSTRAP_KEY:-}
    volumes:
      - runner-data:/workdir
volumes:
  runner-data:
  caddy-data:
```

```
runner.example.internal {
    reverse_proxy oculix-runner:8765
}
```

---

[← OculiX Runner overview](README.md) · Previous: [API reference](api-reference.md) · Next: [CI recipes →](ci-recipes.md)
