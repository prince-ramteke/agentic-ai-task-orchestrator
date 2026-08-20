# Deployment
## Agentic AI Task Orchestrator

> Full deployment is planned (M12). The compose stack does not exist yet. The stack must start with one command from a clean clone.

> **Milestone 1 status (VERIFIED 2026-08-21):** the `backend/` module runs standalone with **zero external infrastructure** (no DB/Redis needed yet — see ADR-0002).
>
> ```bash
> cd backend
> ./mvnw spring-boot:run        # or: ./mvnw clean package && java -jar target/agentic-ai-task-orchestrator-0.0.1-SNAPSHOT.jar
> ```
>
> Then: `GET http://localhost:8080/actuator/health`, `GET /api/v1/health`, `GET /swagger-ui.html`. A multi-stage, non-root `backend/Dockerfile` exists (authored and reviewed; **image build not yet verified** — the Docker daemon was not running in the M1 build environment, so `docker build` is deferred to when a Docker host is available). CI runs `./mvnw verify` on every push/PR via `.github/workflows/ci.yml`. **M1 introduces no required environment variables** — the app boots with defaults.

## 1. Target topology (planned)

```
docker-compose:
  postgres     (durable data)
  redis        (ephemeral state / cache)
  ollama       (local LLM runtime)
  backend      (Spring Boot app)
  frontend     (React, from M13)
  prometheus   (metrics)
  grafana      (dashboards)
```

## 2. One-command startup (planned)

```bash
cp .env.example .env    # then fill in local values
docker-compose up --build
```

- `depends_on` + healthchecks so the backend waits for healthy Postgres and Redis.
- No required manual step may break one-command startup.

## 3. Environment variables

All config from env vars; only `.env.example` is committed. Every variable below must stay documented here and in `.env.example`.

| Variable | Purpose |
|---|---|
| `ACTIVE_PROFILE` | `dev` \| `docker` \| `test` |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | PostgreSQL connection |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis connection |
| `JWT_SECRET` / `JWT_EXPIRATION_MINUTES` | Auth signing + TTL |
| `LLM_PROVIDER` | `ollama` \| `openai` |
| `OLLAMA_BASE_URL` / `OLLAMA_MODEL` | Local model runtime |
| `LLM_FALLBACK_ENABLED` / `OPENAI_API_KEY` | Cloud fallback (off by default; privacy-reviewed) |
| `AGENT_MAX_TOOL_CALLS` / `AGENT_TIMEOUT_SECONDS` / `AGENT_MAX_RETRIES` | Guardrail bounds |
| `METRICS_ENABLED` | Toggle metrics export |

## 4. Profiles

- `dev` — local, no Docker (run Postgres/Redis/Ollama locally or via a partial compose).
- `docker` — full compose stack.
- `test` — Testcontainers-provisioned infra.

Never hardcode hostnames; resolve via profile config.

## 5. Docker images (planned)

- Multi-stage builds; run the backend as a **non-root** user on a JRE base image.
- Small, reproducible images; no secrets baked in.

## 6. Database migrations

Flyway runs on startup and applies forward-only migrations to a fresh or existing database (`DATABASE.md`). Migrations must apply cleanly on a clean clone.

## 7. Health, ordering, and observability

Healthchecks for Postgres, Redis, and the app; backend starts after dependencies are healthy; Prometheus scrapes metrics; Grafana loads dashboards.

## 8. Production concerns (future, ADR-gated)

Secrets management (a real secret store, not `.env`), backups for Postgres, migration strategy under load, TLS/ingress, resource limits, and monitoring/alerting. None are implemented; each is an ADR when the system is hosted.

## 9. CI/CD

GitHub Actions builds and tests on every PR (backend `mvn verify`, frontend build/test). Branch protection requires green checks. Never merge with red CI (`.claude/rules/git.md`).
