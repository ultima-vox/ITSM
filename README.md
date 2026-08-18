# Vox ITSM

Enterprise ITSM/ESM platform under active development.

The project is being built as a **Java 25 / Spring Boot modular monolith** with a **React + TypeScript** web client, PostgreSQL as the transactional source of truth, and optional/derived infrastructure around Redis, RabbitMQ, OpenSearch, MinIO and Keycloak.

The target is a production-grade service-management platform, not an MVP. **The repository is not production-ready yet.**

## Current status

**Status date:** 2026-08-18

The platform has moved beyond a UI prototype and now contains substantial backend and frontend functionality, but full live-stack operation is still being completed.

| Area | Current state |
| --- | --- |
| Architecture / modular-monolith foundation | Mature foundation; still being hardened |
| Frontend operator experience | Broad functional coverage; some flows still depend on mock/dev behavior |
| Service Desk | Main workflows implemented; production-depth backend work continues |
| Change / CAB | Functional UI and backend foundations; deeper server-side rules being completed |
| Problem Management | Functional foundations; backend lifecycle gates being hardened |
| CMDB | Functional UI plus backend foundations; relationship/impact depth is still evolving |
| Knowledge | Functional foundations with create/read/helpfulness flows |
| Service Catalog | Foundation implemented; not yet complete as a production catalog engine |
| SLA / Workflow / Metadata / Automation | Core engines exist; integration depth and operational hardening remain |
| Notifications | Transitioning from development/in-memory behavior to PostgreSQL-backed runtime |
| Search | JDBC fallback and OpenSearch integration exist; full production verification remains |
| Attachments | S3/MinIO path exists; malware/quarantine workflow still requires production-grade verification |
| Authentication / authorization | OIDC/RBAC foundations exist; security hardening is ongoing |
| Reports | Functional reporting surfaces exist; backend truth coverage is still expanding |
| CI | Unit/build/mock E2E are green; full Compose/live integration is not yet a mandatory passing gate |
| Production readiness | **Not ready** |

A reasonable current characterization is **pre-production alpha / integration-stage platform**.

## What is already implemented

### Backend

The backend is a Spring Boot modular monolith under `backend/` with modules and platform capabilities for:

- Service Desk work items;
- Change Management;
- Problem Management;
- CMDB;
- Asset Management;
- Knowledge Base;
- Service Catalog;
- metadata/object definitions;
- dynamic forms;
- workflow engine;
- automation rules;
- RBAC / authorization;
- SLA;
- audit trail;
- transactional outbox;
- notifications;
- search abstraction with OpenSearch support;
- attachment storage abstraction with S3-compatible support;
- AI Gateway / Copilot boundary;
- OpenAPI endpoints;
- health/actuator endpoints.

PostgreSQL is the intended authoritative system of record. Flyway is used for schema evolution.

### Frontend

The web client under `frontend/` is a React + TypeScript operator application with:

- Russian default localization;
- English and German locale resources;
- dark, light and high-contrast themes;
- responsive operator shell;
- Service Desk queues and work-item surfaces;
- CMDB views and graph-oriented UI;
- Assets;
- Problems;
- Changes / CAB;
- Knowledge;
- Reports;
- Notifications;
- administrative surfaces for metadata, workflow, SLA, RBAC, automation and audit;
- bulk operations;
- deep links;
- keyboard/accessibility work;
- mock mode and live API mode.

**Important:** the existence of a frontend screen does not imply that every operation behind it is already production-complete on the backend.

## Work currently pending merge

The major open development branch is tracked by **PR #19 — `production depth wave15`**.

It contains production-depth work including:

- PostgreSQL-backed notifications;
- read/unread and retention behavior;
- live audit API;
- work-item watchers;
- related work-item links;
- CI links;
- escalation and reopen flows;
- CMDB multi-hop impact analysis;
- CMDB relation editing and orphan detection;
- Problem resolution gates;
- Change schedule conflict detection;
- CAB votes API;
- PostgreSQL-backed workload reports;
- production safety guard for insecure development authentication;
- stronger attachment content-signature checks;
- additional Flyway migrations and RBAC permissions.

Until that PR is merged, these changes must not be described as capabilities of `main`.

## Current blocker: complete Docker runtime

The largest integration blocker is tracked in **Issue #20 — `Unify ITSM into a complete Docker Compose runtime stack`**.

The repository currently contains infrastructure services in `docker-compose.yml`, but the normal runtime is not yet a single self-contained Compose application containing backend, frontend and all required infrastructure.

The desired runtime is:

```text
vox-itsm
├── backend
├── frontend
├── postgres
├── rabbitmq
├── redis
├── opensearch
├── minio
├── minio-init
└── keycloak
```

Internal container-to-container communication must use Compose DNS names, for example:

```text
PostgreSQL  jdbc:postgresql://postgres:5432/itsm
RabbitMQ    rabbitmq:5672
Redis       redis:6379
OpenSearch  http://opensearch:9200
MinIO       http://minio:9000
```

`host.docker.internal:*` must not be the normal service-to-service path inside the Compose stack.

The target startup command is:

```bash
docker compose up -d --build
```

This is **not yet the verified standard full-platform startup path**.

## Infrastructure currently described in Compose

The repository currently defines infrastructure services for:

| Service | Purpose |
| --- | --- |
| PostgreSQL 17 | Transactional system of record |
| Redis | Cache / transient coordination |
| RabbitMQ | Durable asynchronous events |
| OpenSearch | Search projections / full-text search |
| MinIO | S3-compatible attachment storage |
| Keycloak | OIDC identity provider for local/integration testing |

These services are part of the intended platform architecture, but their presence in `docker-compose.yml` does not by itself prove end-to-end platform readiness.

## Running the project today

### Infrastructure-only development mode

From the repository root:

```bash
docker compose up -d
```

This starts the infrastructure currently defined in the compose file.

Then backend/frontend may be run according to the development documentation under `docs/ops/`.

### Important warning

Do not assume that separately built `vox-itsm-backend-check` and `vox-itsm-frontend-check` containers represent a complete ITSM deployment. They are verification/build containers, not the target production-shaped runtime.

If the backend fails with an error similar to:

```text
Connection to host.docker.internal:15432 refused
SQL State: 08001
```

that is a deployment/configuration mismatch, not a valid full-stack state. The normal Compose runtime must use the PostgreSQL service directly on `postgres:5432`.

## Authentication modes

The project supports OIDC/Keycloak-based authentication and a development profile.

The `dev` profile deliberately disables normal JWT enforcement for local development and must never be treated as a production configuration.

Production-like profiles are expected to fail fast when insecure development authentication is enabled.

## Testing

The repository contains multiple levels of automated testing, including backend unit/domain tests and frontend Playwright smoke coverage.

Current CI can successfully verify:

- backend tests;
- frontend type checking;
- frontend build;
- mock-mode Playwright smoke tests;
- script syntax checks.

This is **not sufficient for release readiness**.

The project still requires a mandatory non-skipped live-stack integration gate covering:

- clean PostgreSQL startup;
- full Flyway migration chain from an empty database;
- backend readiness;
- frontend live mode;
- Keycloak/OIDC;
- RabbitMQ;
- Redis when enabled;
- OpenSearch;
- MinIO and bucket initialization;
- notifications;
- audit;
- attachments;
- Service Desk lifecycle operations;
- CMDB relationships/impact;
- Problem/Change business gates;
- persistence after restart.

Full Compose/live integration jobs are expected to run on **self-hosted GitHub Actions runners** with Docker Engine and Docker Compose v2 available.

## Production-readiness rules

The project must not be called production-ready until all of the following are true:

- a clean environment starts with one documented command;
- PostgreSQL is created automatically and becomes healthy;
- the complete Flyway chain succeeds from an empty database;
- backend and frontend become healthy without restart loops;
- live frontend mode works without mock fallbacks for mandatory workflows;
- OIDC authentication and authorization are verified end to end;
- mandatory domain rules are enforced on the backend;
- no production-critical in-memory stores remain;
- attachment quarantine/scanning behavior is production-grade;
- search works with correct authorization boundaries;
- audit/outbox consistency is verified;
- data survives service restarts;
- full Compose integration CI is non-skipped and green;
- no unresolved critical/high security findings remain;
- browser-based acceptance testing has been completed;
- documentation matches the actual deployment.

## Architecture principles

The project follows these core rules:

- modular monolith first;
- PostgreSQL as transactional source of truth;
- server-side authorization;
- API-first contracts;
- metadata-driven platform capabilities;
- event-driven integration where appropriate;
- derived stores must not silently become authoritative;
- no hidden production fallback to mock/in-memory behavior;
- secure defaults;
- auditable mutations;
- Russian-first UX with internationalization built in;
- accessibility and operator efficiency as product requirements.

## Repository structure

```text
ITSM/
├── backend/                 # Java / Spring Boot modular monolith
├── frontend/                # React / TypeScript web application
├── docs/                    # Architecture, product, operations, security, UX
├── infra/                   # Infrastructure configuration such as Keycloak
├── scripts/                 # Operational / verification scripts where present
├── docker-compose.yml       # Current infrastructure compose definition
├── AGENTS.md                # Agent collaboration rules
├── INSTRUCTIONS.md          # Project implementation instructions
└── README.md
```

The exact structure may evolve through deliberate architecture decisions.

## Key documentation

Start with:

- `INSTRUCTIONS.md` — implementation requirements and Definition of Done;
- `AGENTS.md` — agent/project collaboration rules;
- `docs/architecture/` — architecture decisions and boundaries;
- `docs/security/` — security requirements;
- `docs/ops/` — local/integration operation and troubleshooting;
- `docs/ux/` — UX/design review material.

## Development priority

The immediate priority is **not adding more superficial feature surface**. The current priority is to convert the existing breadth into a reliably deployable, persistent, secure and testable platform.

Highest-priority work:

1. finish the complete Compose runtime (Issue #20);
2. remove stale host-based integration assumptions;
3. make full-stack live CI mandatory on self-hosted runners;
4. merge production-depth work only after live integration verification;
5. complete backend persistence and lifecycle enforcement where UI is ahead of server behavior;
6. perform browser acceptance and security hardening;
7. only then move toward release qualification.

## Target

The goal remains a complete enterprise ITSM/ESM platform comparable in operational depth to established enterprise service-management products while using an original implementation and architecture.

Quality is measured by working end-to-end processes, maintainable architecture, secure authorization, persistence, observability, testability, coherent UX and reliable deployment — not by the number of screens or mock scenarios implemented.
