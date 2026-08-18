# vox ITSM

Enterprise-grade ITSM/ESM platform — modular monolith with metadata-driven workflow engine, real-time collaboration, and full ITIL lifecycle support.

## System Requirements

### Minimum (Development)

| Component | Requirement |
|-----------|-------------|
| **OS** | Linux, macOS, or Windows (WSL2) |
| **CPU** | 4 cores |
| **RAM** | 8 GB (Docker stack ~4 GB, backend ~1 GB, frontend ~512 MB) |
| **Disk** | 20 GB free (Docker images ~10 GB) |
| **Docker** | 24.0+ with Compose v2 |
| **Java** | 25+ (backend compilation; not needed if using Docker builds) |
| **Node.js** | 22+ (frontend build) |
| **npm** | 10+ |

### Recommended (Production)

| Component | Requirement |
|-----------|-------------|
| **CPU** | 8+ cores |
| **RAM** | 16 GB+ |
| **Disk** | 50 GB SSD |
| **Database** | PostgreSQL 17 |
| **Cache** | Redis 7 |
| **Search** | OpenSearch 2.19+ |
| **Message Broker** | RabbitMQ 4 |
| **Object Storage** | S3-compatible (MinIO, AWS S3, etc.) |
| **Identity Provider** | Keycloak 26+ or any OIDC provider |

### Optional

| Component | Purpose |
|-----------|---------|
| **Ollama / LM Studio** | AI copilot (summarization, suggestions) |
| **OpenSearch** | Full-text search (falls back to JDBC without it) |
| **Redis** | Distributed cache, locale preferences, rate limiting |
| **RabbitMQ** | Async messaging, outbox event relay |

## Quick Start

```bash
# Clone and run the installer
git clone https://github.com/ultima-vox/ITSM.git
cd ITSM
./setup.sh
```

The installer will:
1. Check prerequisites (Docker, Java, Node.js)
2. Start all Docker infrastructure (PostgreSQL, Redis, RabbitMQ, OpenSearch, MinIO, Keycloak)
3. Wait for services to become healthy
4. Build the frontend

### Start dev servers

```bash
./setup.sh --dev
```

Or manually:

```bash
# Terminal 1 — Backend
cd backend
set -a; source .env.compose; set +a
./gradlew bootRun

# Terminal 2 — Frontend
cd frontend
cp .env.example .env
npm run dev
```

Open **http://localhost:5173**

### Run smoke tests

```bash
./scripts/smoke-setup.sh
```

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Frontend (React + TypeScript + Vite)                   │
│  Dual-mode: mock (demo) / live API                      │
├─────────────────────────────────────────────────────────┤
│  Backend (Java 25 + Spring Boot 3.5)                    │
│  Modular monolith — 8 domain modules                    │
│  ┌──────────┬──────────┬──────────┬──────────┐          │
│  │Service   │Change    │Problem   │CMDB/     │          │
│  │Desk      │Mgmt      │Mgmt      │Asset     │          │
│  ├──────────┼──────────┼──────────┼──────────┤          │
│  │Knowledge │Catalog   │Reporting │Platform  │          │
│  │Base      │          │          │Engines   │          │
│  └──────────┴──────────┴──────────┴──────────┘          │
│  Platform Engines: Workflow, RBAC, SLA, Automation,     │
│  Forms, Audit, Search, Notification, Event, AI Gateway  │
├─────────────────────────────────────────────────────────┤
│  Infrastructure                                         │
│  PostgreSQL · Redis · RabbitMQ · OpenSearch · MinIO     │
│  Keycloak (OIDC) · Flyway migrations                    │
└─────────────────────────────────────────────────────────┘
```

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 25, Spring Boot 3.5.14, Spring Modulith, Flyway |
| Frontend | React 18, TypeScript 5.6, Vite 8.2, React Router 7 |
| Database | PostgreSQL 17, Flyway (74 migrations) |
| Cache | Redis 7 (AOF persistence) |
| Messaging | RabbitMQ 4 (outbox pattern) |
| Search | OpenSearch 2.19 (JDBC fallback) |
| Auth | Keycloak 26 / OIDC (Authorization Code + PKCE) |
| Storage | S3-compatible (MinIO for local dev) |
| Observability | Micrometer + OpenTelemetry + Prometheus + Grafana |
| CI/CD | GitHub Actions, self-hosted runners |
| Deployment | Docker Compose, Kubernetes (Kustomize) |

## Features

### Functional Modules

| Module | Description |
|--------|-------------|
| **Service Desk** | Incidents, service requests, tasks, queues, assignment, SLA tracking, bulk operations |
| **Change Management** | CAB reviews, risk assessment, scheduling, conflict detection, approvals |
| **Problem Management** | Root cause analysis, known errors, workarounds, linked incidents |
| **CMDB** | Configuration items, relationships, impact analysis, service mapping |
| **Asset Management** | Hardware/software lifecycle, procurement, assignment, retirement |
| **Knowledge Base** | Articles, categories, helpfulness voting, linked tickets |
| **Service Catalog** | Request forms, approvals, fulfillment workflows |
| **Reporting** | SLA compliance, workload, trends, operator metrics |

### Platform Engines

| Engine | Description |
|--------|-------------|
| **Workflow** | State machines with permissions, conditions, timers, approvals |
| **RBAC** | Roles, permissions, field-level access, delegation, separation of duties |
| **SLA** | Response/resolution targets, working calendars, pause, breach detection |
| **Automation** | Event-driven rules (WHEN/IF/THEN), no-code configuration |
| **Forms** | Metadata-driven layouts, validation, role-specific views |
| **Audit** | Tamper-resistant event trail, actor resolution, entity tracking |
| **Search** | Global full-text across all entities, faceted filtering |
| **Notification** | SSE real-time push, templated delivery, channel routing |
| **AI Gateway** | Copilot summarization, suggestions (Ollama/LM Studio) |

### UI Capabilities

- 10 interface languages (ru, en, de, fr, es, it, ja, zh, ko, ar)
- Dark / light / high-contrast themes
- Compact / comfortable density modes
- Responsive design
- Real-time SSE updates
- Global search with keyboard navigation
- Accessible (WCAG-oriented controls)

## Configuration

### Backend Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profiles |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/itsm` | PostgreSQL JDBC URL |
| `DATABASE_USER` / `DATABASE_PASSWORD` | `itsm` / `itsm` | Database credentials |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis connection |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | RabbitMQ connection |
| `OPENSEARCH_URL` | (empty) | OpenSearch cluster URL |
| `ITSM_STORAGE_TYPE` | `local` | Storage backend (`local` / `s3`) |
| `S3_ENDPOINT` | — | S3 endpoint URL |
| `S3_BUCKET` | — | S3 bucket name |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | — | S3 credentials |
| `OIDC_ISSUER_URI` | `http://localhost:8081/realms/itsm` | OIDC issuer |
| `ITSM_CORS_ORIGINS` | `http://localhost:5173` | Allowed CORS origins |
| `ITSM_REDIS_ENABLED` | `false` | Enable Redis cache |

### Frontend Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `VITE_USE_MOCK` | `false` | Enable mock/demo mode |
| `VITE_API_BASE` | `/api/v1` | Backend API base path |
| `VITE_OIDC_ENABLED` | `false` | Enable OIDC login UI |
| `VITE_OIDC_ISSUER` | `http://localhost:8081/realms/itsm` | OIDC issuer URL |
| `VITE_OIDC_CLIENT_ID` | `itsm-spa` | OIDC client ID |
| `VITE_WORKSPACE_NAME` | `ITSM` | Workspace display name |

## Development

### Project Structure

```
ITSM/
├── backend/
│   ├── src/main/java/ru/ultimavox/itsm/
│   │   ├── servicedesk/          # Incidents, requests, tasks
│   │   ├── changemanagement/     # Change lifecycle
│   │   ├── problemmanagement/    # Problem lifecycle
│   │   ├── cmdb/                 # Configuration items
│   │   ├── assetmanagement/      # Asset lifecycle
│   │   ├── knowledgebase/        # Articles, voting
│   │   ├── servicecatalog/       # Catalog items
│   │   ├── reporting/            # Dashboards, metrics
│   │   └── platform/             # Shared engines (RBAC, SLA, workflow, etc.)
│   └── src/main/resources/db/migration/  # Flyway SQL (V1–V74)
├── frontend/
│   └── src/
│       ├── api/                  # API layer (mock/live branching)
│       ├── pages/                # Route components
│       ├── components/           # Shared UI components
│       ├── hooks/                # Shared React hooks
│       ├── i18n/                 # 10 locale message catalogs
│       ├── mock/                 # In-memory demo store
│       └── types/                # TypeScript types
├── docs/
│   ├── architecture/             # Architecture docs
│   ├── security/                 # Security docs
│   ├── ops/                      # Operations docs
│   └── product/                  # Product docs
├── deploy/
│   ├── kubernetes/               # K8s manifests + Kustomize
│   └── observability/            # Grafana dashboards, Prometheus rules
├── infra/
│   └── keycloak/                 # Realm JSON + README
├── docker-compose.yml            # Development infrastructure
├── docker-compose.prod.yml       # Production full-stack
├── setup.sh                      # One-command installer
└── scripts/
    ├── smoke-setup.sh            # Post-install verification
    ├── smoke-api.sh              # API smoke tests
    └── backup-db.ps1             # Database backup
```

### Useful Commands

```bash
# Backend
cd backend
./gradlew bootRun                                    # Start dev server
./gradlew bootRun --args='--spring.profiles.active=dev,compose'  # With full infra
./gradlew test                                        # Run tests
./gradlew classes                                     # Compile only

# Frontend
cd frontend
npm run dev          # Dev server (http://localhost:5173)
npm run build        # Production build
npm run typecheck    # TypeScript check
npm run lint         # ESLint
npx vitest run src   # Unit tests

# Infrastructure
docker compose up -d          # Start all services
docker compose down           # Stop all services
docker compose down -v        # Stop and wipe data
docker compose logs -f        # Follow all logs
```

### API Documentation

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs
- **Health**: http://localhost:8080/actuator/health

### Default Credentials

| Service | Username | Password | URL |
|---------|----------|----------|-----|
| Keycloak Admin | `admin` | `admin` | http://localhost:8081 |
| Keycloak Demo User | `anna` | `anna` | — |
| PostgreSQL | `itsm` | `itsm` | localhost:5432 |
| RabbitMQ | `guest` | `guest` | http://localhost:15672 |
| MinIO | `minioadmin` | `minioadmin` | http://localhost:9001 |

## Deployment

### Docker Compose (Production)

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

### Kubernetes

```bash
cd deploy/kubernetes
# Edit secret.example.yaml → secret.yaml with real credentials
kubectl apply -k .
```

See [`docs/ops/production-deployment.md`](docs/ops/production-deployment.md) for full deployment guide.

## Testing

```bash
# Frontend unit tests
cd frontend && npx vitest run src

# Backend tests (requires Java 25)
cd backend && ./gradlew test

# Smoke tests (after setup.sh)
./scripts/smoke-setup.sh

# CI pipeline
# Runs on push/PR: typecheck, lint, unit tests, build, API contract,
# Playwright E2E, backend tests, security scans, container scans
```

## Documentation

| Topic | Path |
|-------|------|
| Architecture overview | [`docs/architecture/`](docs/architecture/) |
| Architecture Decision Records | [`docs/adr/`](docs/adr/) |
| Security model | [`docs/security/`](docs/security/) |
| Operations guide | [`docs/ops/`](docs/ops/) |
| Product specs | [`docs/product/`](docs/product/) |
| UX quality gates | [`docs/ux/`](docs/ux/) |
| Keycloak setup | [`infra/keycloak/README.md`](infra/keycloak/README.md) |
| Compose integrations | [`docs/ops/compose-integrations.md`](docs/ops/compose-integrations.md) |
| Production deployment | [`docs/ops/production-deployment.md`](docs/ops/production-deployment.md) |

## License

Proprietary — Ultima Vox.
