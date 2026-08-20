# vox ITSM

Enterprise ITSM/ESM platform — modular monolith under active development.

**Java 25 / Spring Boot 3.5 / Gradle** backend; **React 18 / TypeScript 5.6 / Vite 8.2** frontend; PostgreSQL 17 as the transactional source of truth. Optional integrations: Redis, RabbitMQ, OpenSearch, MinIO, Keycloak.

The target is a production-grade service-management platform, not an MVP. **The repository is not production-ready yet.**

## Current status

**Status date:** 2026-08-19

| Area | Current state |
| --- | --- |
| Architecture | Modular monolith with Flyway V1–V75; still being hardened |
| Frontend | Broad operator coverage; live API mode wired at the API layer |
| Service Desk | Incidents, requests, tasks, assignment, comments, links, SLA clocks |
| Change / CAB | Lifecycle, votes, schedule windows; calendar/CAB depth still evolving |
| Problem Management | Lifecycle, RCA/workaround gates |
| CMDB | CIs, relationships, impact graph |
| Assets | Lifecycle, CI linkage, name/location |
| Knowledge | Articles, publication, helpfulness votes |
| Service Catalog | Items, requests, fulfillment foundations |
| SLA / Workflow / Automation | Persistent engines exist; operational hardening continues |
| Notifications | PostgreSQL-backed store + SSE |
| Search | JDBC fallback and OpenSearch integration |
| Attachments | S3/MinIO + signature scan + optional ClamAV INSTREAM; download blocked until CLEAN. Scan status, detail, and retry job verified (#21) |
| Auth | Keycloak OIDC (PKCE) + deny-by-default RBAC; browser login verified end-to-end against the Compose stack, session survives reload via `prompt=none` restore |
| Reports | Backend workload/SLA reports |
| Locales | **ru (default), en, de** — not ten languages |
| Production readiness | **Not ready** — no high availability, and the rehearsal certificates are self-signed |

A reasonable characterization is **pre-production alpha / integration-stage platform**.

Verified on the status date, on the full Compose stack: `docker compose up -d --build` reaches all
services healthy, `scripts/smoke-compose.sh` passes 13 checks, `./gradlew test` passes, the frontend
typechecks/lints/tests clean, and a real Keycloak browser login loads live data
(`frontend/e2e/oidc-login.spec.ts`).

Every CI job was also reproduced locally: Kustomize renders and validates (14 resources), Prometheus
rules check out, the live contract E2E passes against a `dev` backend, both container images scan
clean, and the k6 baseline runs against the secured stack within its thresholds. A PostgreSQL backup
and restore drill (`scripts/backup-db.ps1` → `scripts/verify-db-backup.ps1`) restored 60 tables into
an isolated database.

## Quick start

One command starts the complete local stack (PostgreSQL, Redis, RabbitMQ, OpenSearch, MinIO, Keycloak, backend, frontend):

```bash
docker compose up -d --build
```

Then open **http://localhost**

| Service | URL | Notes |
| --- | --- | --- |
| Frontend | http://localhost | nginx, live mode, `/api` proxied to backend |
| Backend | http://localhost:8080 | `/actuator/health`, OpenAPI at `/swagger-ui.html` |
| Keycloak | http://localhost:8081 | admin / admin; realm `itsm`; demo user `anna` / `anna` |
| PostgreSQL | localhost:5432 | `itsm` / `itsm` / `itsm` |
| RabbitMQ UI | http://localhost:15672 | guest / guest |
| MinIO console | http://localhost:9001 | minioadmin / minioadmin |
| OpenSearch | http://localhost:9200 | security plugin disabled — local stack only; the production stack requires authentication |

Internal container DNS (never `host.docker.internal` for service-to-service traffic):

```text
PostgreSQL  jdbc:postgresql://postgres:5432/itsm
RabbitMQ    rabbitmq:5672
Redis       redis:6379
OpenSearch  http://opensearch:9200
MinIO       http://minio:9000
Keycloak    http://keycloak:8080
```

Stop: `docker compose down`. Wipe data: `docker compose down -v`.

Verify the runtime after it starts — live smoke, then a clean-install and restart-persistence
check (no mock mode involved):

```bash
./scripts/smoke-compose.sh
./scripts/verify-compose-runtime.sh
```

### Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `port is already allocated` on `up` | Another project or host service holds the port. `docker ps` to find it, or override: `ITSM_BACKEND_PORT=18080 docker compose up -d`. |
| Backend answers on a published port but behaves like a different app | A host process is bound to the same port on `127.0.0.1` and wins over the container publish. Confirm with `docker compose port backend 8080` and query that address. |
| `failed to resolve reference … not found` while pulling | A pinned digest was garbage-collected upstream. Refresh the digest in `docker-compose.yml` for the affected image. |
| Backend restarts or never turns healthy | `docker compose logs backend`. Usual causes: PostgreSQL not healthy yet (it is a `service_healthy` dependency), or profile `prod` refusing to start on an http issuer, localhost CORS origins, or demo secrets. |
| Signed in, but every request answers 401/403 | The access token has no `sub` claim: the Keycloak client is missing the built-in `basic` client scope. `./scripts/smoke-compose.sh` asserts this explicitly. |
| Realm edits do not take effect | `--import-realm` only seeds a realm that does not exist. With a persistent Keycloak database, apply changes through the admin API, or recreate the Keycloak volume in a throwaway environment. |
| RabbitMQ exits at boot with `.erlang.cookie: eacces` | An old container kept a root-owned cookie. `docker compose rm -sf rabbitmq` and start again. |

### Host-run development

Keep infrastructure in Compose; run backend and frontend on the host (avoids port 8080/80 collision with the app containers):

```bash
docker compose up -d postgres redis rabbitmq opensearch minio minio-init keycloak

cd backend
./gradlew bootRun --args='--spring.profiles.active=dev,compose'

cd frontend
cp .env.example .env
npm run dev
```

Open **http://localhost:5173**. Profile `dev` disables JWT enforcement — never use it in production.

### Installer

```bash
./setup.sh              # full Compose stack
./setup.sh --infra-only # infrastructure containers only
./setup.sh --dev        # infra + host backend/frontend
./scripts/smoke-setup.sh
./scripts/smoke-compose.sh
```

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Frontend (React + TypeScript + Vite)                   │
│  Dual-mode: mock (demo) / live API                      │
├─────────────────────────────────────────────────────────┤
│  Backend (Java 25 + Spring Boot 3.5 + Gradle)           │
│  Modular monolith                                       │
│  Service Desk · Change · Problem · CMDB · Asset         │
│  Knowledge · Catalog · Reporting · Platform engines     │
│  Workflow, RBAC, SLA, Automation, Forms, Audit,         │
│  Search, Notification, Event/Outbox, AI Gateway         │
├─────────────────────────────────────────────────────────┤
│  Infrastructure                                         │
│  PostgreSQL · Redis · RabbitMQ · OpenSearch · MinIO     │
│  Keycloak (OIDC) · Flyway V1–V75                        │
└─────────────────────────────────────────────────────────┘
```

### Tech stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 25, Spring Boot 3.5, Spring Modulith, Gradle, Flyway |
| Frontend | React 18, TypeScript 5.6, Vite 8.2, React Router 7 |
| Database | PostgreSQL 17, Flyway (V1–V75) |
| Cache | Redis 7 (optional; AOF) |
| Messaging | RabbitMQ 4 (outbox pattern) |
| Search | OpenSearch 2.19 (JDBC fallback when unset) |
| Auth | Keycloak 26 / OIDC (Authorization Code + PKCE) |
| Storage | S3-compatible (MinIO locally) |
| Observability | Micrometer + OpenTelemetry + Prometheus + Grafana |
| CI/CD | GitHub Actions on self-hosted Linux runners |
| Deployment | Docker Compose, Kubernetes (Kustomize) |

## Features (implemented foundations)

Existence of a screen or API is not a production-completeness claim.

### Functional modules

| Module | Description |
|--------|-------------|
| **Service Desk** | Incidents, service requests, tasks, queues, assignment, SLA clocks, bulk operations |
| **Change Management** | Risk/plan fields, scheduling, CAB votes, conflict detection |
| **Problem Management** | RCA, known errors, workarounds, linked incidents |
| **CMDB** | Configuration items, relationships, impact analysis |
| **Asset Management** | Lifecycle, inventory, CI linkage |
| **Knowledge Base** | Articles, categories, helpfulness voting |
| **Service Catalog** | Request items, fulfillment foundations |
| **Reporting** | Workload and SLA metrics from backend queries |

### Platform engines

| Engine | Description |
|--------|-------------|
| **Workflow** | Versioned state machines, permissions, conditions, timers, approvals |
| **RBAC** | Roles, object/field permissions, delegation |
| **SLA** | Response/resolution clocks, calendars, pause, breach, escalation |
| **Automation** | WHEN/IF/THEN rules, allowlisted actions, retry/quarantine |
| **Forms / metadata** | Object definitions, dynamic forms |
| **Audit** | Append-only trail |
| **Search** | JDBC or OpenSearch, permission-aware |
| **Notification** | Persistent store, SSE, preferences |
| **AI Gateway** | Isolated copilot (Ollama optional; logging stub otherwise) |

### UI capabilities

- 3 interface languages: Russian (default), English, German
- Dark / light / high-contrast themes
- Compact / comfortable density
- Real-time SSE updates
- Global search
- WCAG-oriented controls

## Configuration

### Backend environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `dev` (host) / `compose` (stack) | `dev` disables JWT. `compose` enables Redis, OpenSearch, S3. `prod` requires HTTPS issuer, non-demo secrets |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/itsm` | PostgreSQL JDBC URL |
| `DATABASE_USER` / `DATABASE_PASSWORD` | `itsm` / `itsm` | Database credentials |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | RabbitMQ |
| `OPENSEARCH_URL` | (empty) | OpenSearch URL; empty = JDBC search |
| `OPENSEARCH_USERNAME` / `OPENSEARCH_PASSWORD` | (empty) | Cluster credentials; required when the security plugin is enabled |
| `ITSM_STORAGE_TYPE` | `local` | `local` or `s3` |
| `OIDC_ISSUER_URI` | `http://localhost:8081/realms/itsm` | JWT `iss` claim |
| `OIDC_JWK_SET_URI` | (empty) | Optional internal JWKS URL for containerized backend |
| `ITSM_CORS_ORIGINS` | Vite `5173` | Allowed CORS origins |
| `ITSM_REDIS_ENABLED` | `false` | Enable Redis cache |

### Frontend environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `VITE_USE_MOCK` | `false` | Mock/demo mode |
| `VITE_API_BASE` | `/api/v1` | Backend API base path |
| `VITE_OIDC_ENABLED` | `false` (host) / `true` (image) | OIDC login UI |
| `VITE_OIDC_ISSUER` | `http://localhost:8081/realms/itsm` | OIDC issuer URL |
| `VITE_OIDC_CLIENT_ID` | `itsm-spa` | Public client |
| `VITE_WORKSPACE_NAME` | `ITSM` | Workspace display name |

## Development

```
ITSM/
├── backend/                  # Gradle Spring Boot modular monolith
├── frontend/                 # React SPA
├── docs/                     # architecture, security, ops, product, UX
├── deploy/                   # Kubernetes + observability
├── infra/keycloak/           # Realm JSON
├── docker-compose.yml        # Full local stack
├── docker-compose.prod.yml   # Production-shaped stack (real secrets required for `prod`)
├── setup.sh
└── scripts/                  # smoke, backup
```

```bash
# Backend
cd backend
./gradlew bootRun
./gradlew bootRun --args='--spring.profiles.active=dev,compose'
./gradlew test
./gradlew classes

# Frontend
cd frontend
npm run dev
npm run build
npm run typecheck
npx vitest run src

# Stack
docker compose up -d --build
docker compose ps
docker compose logs -f backend
docker compose down
```

### Default credentials (local only)

| Service | Username | Password | URL |
|---------|----------|----------|-----|
| Keycloak Admin | `admin` | `admin` | http://localhost:8081 |
| Keycloak Demo User | `anna` | `anna` | — |
| PostgreSQL | `itsm` | `itsm` | localhost:5432 |
| RabbitMQ | `guest` | `guest` | http://localhost:15672 |
| MinIO | `minioadmin` | `minioadmin` | http://localhost:9001 |

## Testing

```bash
cd frontend && npx vitest run src
cd backend && ./gradlew test
./scripts/smoke-setup.sh
./scripts/smoke-compose.sh
```

CI (self-hosted Linux): typecheck, lint, unit tests, mock Playwright, backend tests, security scans, and a mandatory Compose full-stack smoke on unique project names.

## Documentation

| Topic | Path |
|-------|------|
| Architecture | [`docs/architecture/`](docs/architecture/) |
| ADRs | [`docs/adr/`](docs/adr/) |
| Security | [`docs/security/`](docs/security/) |
| Operations | [`docs/ops/`](docs/ops/) |
| Product | [`docs/product/`](docs/product/) |
| UX gates | [`docs/ux/`](docs/ux/) |
| Keycloak | [`infra/keycloak/README.md`](infra/keycloak/README.md) |
| Compose integrations | [`docs/ops/compose-integrations.md`](docs/ops/compose-integrations.md) |
| Production deployment | [`docs/ops/production-deployment.md`](docs/ops/production-deployment.md) |

## License

Proprietary — Ultima Vox.
