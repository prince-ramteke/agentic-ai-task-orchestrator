# Deployment
## Agentic AI Task Orchestrator

> Full deployment is planned (M12). The compose stack does not exist yet. The stack must start with one command from a clean clone.

> **Milestone 4 status:** adds the **Spring AI / Ollama** layer. New optional env vars:
> `OLLAMA_TEMPERATURE` (default 0.2) and `OLLAMA_TIMEOUT_SECONDS` (default 60); existing
> `LLM_PROVIDER`/`OLLAMA_BASE_URL`/`OLLAMA_MODEL` now take effect (default model `llama3.2`). **Ollama
> is optional to boot:** with `pull-model-strategy: never` the app starts even when Ollama is stopped;
> AI calls then return `503 LLM_UNAVAILABLE`. No model is pulled automatically. The normal
> `./mvnw verify` does **not** require Ollama (the live IT is gated: `-Dllm.live.ollama=true`).
>
> **Milestone 3 status:** no new runtime env vars. The Task/Customer APIs run on the same PostgreSQL
> + security configuration as M2 (Flyway now also applies `V3`/`V4`). One tooling note: the
> Testcontainers integration tests (`*IT`) require a **running Docker engine**; without Docker,
> `./mvnw verify` still passes (those tests skip via `disabledWithoutDocker`). CI must provide Docker
> to actually verify the PostgreSQL integration suite. On Docker Engine 29 the build pins the Docker
> API version to `1.44` (see `docs/TESTING.md` / ADR-0008).

> **Milestone 2 status (VERIFIED 2026-08-21):** the backend now requires **PostgreSQL** and
> security env vars to run. Required env: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`,
> `JWT_SECRET` (≥ 32 chars), optionally `JWT_EXPIRATION_SECONDS` (default 3600) and
> `CORS_ALLOWED_ORIGINS` (default `http://localhost:5173`). Flyway applies the schema at startup.
>
> ```bash
> cd backend
> # with a local PostgreSQL running and env exported (see .env.example):
> ./mvnw spring-boot:run
> ```
>
> Then: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/me` (Bearer),
> `GET /swagger-ui.html` (Authorize with the JWT). CI runs `./mvnw verify` on every push/PR.
>
> **Verification note:** the automated suite (incl. real socket-level HTTP via `RANDOM_PORT`) runs
> against **H2 in PostgreSQL mode** with the same Flyway migrations, so it needs no Docker/Postgres
> (ADR-0005). A live socket run of the packaged jar against a real PostgreSQL was **not** performed
> in this build environment (no Docker daemon and no local PostgreSQL available); the production jar
> correctly refuses to start without a real datasource. A multi-stage, non-root `backend/Dockerfile`
> exists (image build still unverified — no Docker host).
>
> **M1 (superseded):** M1 ran with zero external infrastructure; M2 introduces the datastore.

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
| `LLM_PROVIDER` | Active provider — **M4: `ollama` only** (`openai` is future/not wired) |
| `OLLAMA_BASE_URL` / `OLLAMA_MODEL` | Local model runtime (M4; default model `llama3.2`, must be pulled) |
| `OLLAMA_TEMPERATURE` / `OLLAMA_TIMEOUT_SECONDS` | M4: sampling temperature (default 0.2) and connect+read timeout (default 60s) |
| `LLM_FALLBACK_ENABLED` / `OPENAI_API_KEY` | Cloud fallback (off by default; **not wired in M4**; privacy-reviewed) |
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

## Milestone 6 — Agent environment variables

The agent bounds are env-tunable (defaults shown; see `.env.example`): `AGENT_MAX_ITERATIONS=8`, `AGENT_MAX_TOOL_CALLS=10`, `AGENT_TIMEOUT_SECONDS=60`, `AGENT_LOOP_THRESHOLD=2`, `AGENT_MAX_OBSERVATION_CHARS=2000`, `AGENT_MAX_ARRAY_ITEMS=20`. `AGENT_MAX_RETRIES` is reserved for M8 (step-retry enforcement) and is not yet honored. The agent adds no new infrastructure (no Redis/queue/datastore in M6).
