# Docker Compose Runtime

The root `compose.yaml` starts the local execution stack:

```text
PostgreSQL <- API <-> Redis <-> Runner
```

## Network Boundary

- PostgreSQL and Redis are attached only to the internal `vfqc-internal` network.
- Runner has no published host port.
- API listens on `0.0.0.0:8080` inside its container so runner can reach it by service DNS.
- API is published to the host only on `127.0.0.1:${VFQC_API_PORT}`.
- API and runner also use `vfqc-egress` because target/provider execution may require outbound network access.

Binding API to `127.0.0.1` inside the container would prevent runner-to-API communication. The localhost restriction therefore belongs on the Compose host-port mapping.

## Start

Create the Compose environment file from the checked-in template, replace every
placeholder secret, then start the stack:

```bash
cp .env.docker.example .env
docker compose up --build
```

The API readiness endpoint is:

```text
http://127.0.0.1:8080/actuator/health/readiness
```

PostgreSQL and Redis intentionally have no host endpoints. Inspect them through `docker compose exec` when needed.

## Images

- API uses a multi-stage Maven build, Spring Boot layered JAR extraction, a JRE-only Alpine runtime, and a numeric non-root user.
- Runner uses a multi-stage Node.js build, prunes dev dependencies, copies only production dependencies and compiled source, and runs as the built-in non-root `node` user.
- Secrets are required at Compose interpolation time and are never copied into either image.
