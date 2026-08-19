# Vox ITSM

Enterprise ITSM/ESM platform under active development.

The project is being built as a **Java 25 / Spring Boot modular monolith** with a **React + TypeScript** web client, PostgreSQL as the transactional source of truth, and Redis, RabbitMQ, OpenSearch, MinIO and Keycloak as supporting infrastructure.

The target is a production-grade service-management platform, not an MVP. **The repository is not production-ready yet.**

## Current status

**Status date:** 2026-08-19

The platform has moved beyond a UI prototype and now contains substantial live backend and frontend functionality. PR #19 (`production depth wave15`) is merged into `main`, but the full runtime and live-mode release path are still incomplete.

A reasonable current characterization is **pre-production alpha / integration-stage platform**.

### Readiness estimate

| Area | Current state | Approx. readiness |
| --- | --- | ---: |
| Architecture / modular-monolith foundation | Mature foundation; still being hardened | 85–90% |
| Frontend operator UX | Broad functional coverage; some flows remain mock/dev or partially live | 75–85% |
| Service Desk | Main lifecycle plus watchers/links/escalation/reopen/audit integrations implemented | 65–75% |
| Change / CAB | Conflicts and CAB vote APIs exist; deeper lifecycle verification remains | 55–65% |
| Problem Management | Resolution gates and core lifecycle foundations exist | 55–65% |
| CMDB | Live create/relations/orphans/impact foundations exist | 55–65% |
| Asset Management | Create/assign/status foundations exist; full lifecycle depth remains | 50–60% |
| Knowledge | Live create/update/publish foundations exist | 55–65% |
| Service Catalog | Foundation implemented; production workflow/approval depth incomplete | 35–45% |
| SLA / Workflow / Metadata / Automation / RBAC admin | Core engines exist; several admin surfaces are still read-only or incomplete live mode | 45–55% |
| Notifications | PostgreSQL-backed implementation merged; full live/restart/authorization verification pending | 60–70% |
| Search | JDBC fallback + OpenSearch integration exist; production projection/recovery verification remains | 45–55% |
| Attachments | S3/MinIO path and signature hardening exist; production quarantine/scanner pipeline incomplete | 40–50% |
| Authentication / authorization | OIDC/RBAC foundations and production safety guard exist; full permission matrix pending | 50–60% |
| Reports | PostgreSQL workload reporting exists; broader truth/authorization verification remains | 50–60% |
| CI / full-stack verification | Unit/build/mock E2E exist; mandatory live Compose self-hosted gate is missing | 30–40% |
| Production readiness | **Not ready** | **40–50%** |

Overall functional/platform readiness is approximately **60–65%**, while production readiness is lower because deployment, integration, security and release evidence are not yet complete.

## Recent production-depth milestone

PR #19 is merged into `main` and added significant server-side depth:

- PostgreSQL-backed notifications with read/unread, dedupe and retention;
- live audit API;
- Service Desk watchers;
- related work-item links;
- work-item ↔ CI links;
- escalation and reopen behavior;
- CMDB relations, orphan detection and multi-hop impact;
- Problem resolution gates;
- Change schedule conflict detection;
- CAB votes API;
- PostgreSQL-backed workload reports;
- Knowledge live write/publish paths;
- Asset and CMDB create paths;
- SLA / automation / workflow / RBAC live read APIs;
- production safety guard against insecure dev authentication under production-like profiles;
- stronger attachment content-signature checks;
- expanded Flyway migrations and permissions.

These capabilities are now part of `main`, but they are not considered release-qualified until verified through the full live stack.

## Main blockers and release gates

### Issue #20 — complete Docker Compose runtime

Issue #20 is the immediate **P0 blocker**.

The repository currently has infrastructure in `docker-compose.yml`, but backend and frontend are not yet part of one verified self-contained Compose runtime.

Target runtime:

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

Internal service traffic must use Compose DNS names:

```text
PostgreSQL  jdbc:postgresql://postgres:5432/itsm
RabbitMQ    rabbitmq:5672
Redis       redis:6379
OpenSearch  http://opensearch:9200
MinIO       http://minio:9000
```

`host.docker.internal:*` must not be the normal container-to-container path.

The target startup command is:

```bash
docker compose up -d --build
```

Issue #20 must not be closed until the complete runtime, clean Flyway migration, live smoke, persistence-after-restart and self-hosted CI evidence are all verified.

### Issue #21 — platform completion / production-readiness gate

Issue #21 is the umbrella release gate for the whole platform.

It tracks mandatory completion and verification for:

- runtime/deployment;
- database migration and backup/restore;
- OIDC/RBAC and permission matrix;
- Service Desk end-to-end lifecycle;
- Service Catalog;
- CMDB;
- Assets;
- Problems;
- Changes/CAB;
- Knowledge;
- SLA;
- Workflow/Metadata/Form/Automation/RBAC administration;
- Notifications;
- OpenSearch;
- attachments and production malware scanning;
- audit/outbox/RabbitMQ failure handling;
- reports;
- AI Gateway security;
- frontend live-mode completeness;
- self-hosted full-stack CI;
- reliability/observability;
- adversarial security review;
- release documentation and evidence.

The platform must not be declared complete or production-ready until Issue #21 closure criteria are satisfied.

## What is implemented

### Backend

The Spring Boot modular monolith under `backend/` contains modules and platform capabilities for:

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
- persistent notifications;
- search abstraction with OpenSearch support;
- attachment storage abstraction with S3-compatible support;
- AI Gateway / Copilot boundary;
- OpenAPI;
- health/actuator endpoints.

PostgreSQL is the intended authoritative system of record. Flyway is used for schema evolution.

### Frontend

The React + TypeScript client under `frontend/` provides:

- Russian default localization;
- English and German locale resources;
- dark, light and high-contrast themes;
- responsive operator shell;
- Service Desk queues and work-item surfaces;
- CMDB graph-oriented UI;
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
- mock and live API modes.

**Important:** a visible frontend control or page is not evidence that the full backend behavior is release-complete. Live mode remains the source of truth for acceptance.

## Infrastructure

The repository currently defines infrastructure services for:

| Service | Purpose |
| --- | --- |
| PostgreSQL 17 | Transactional source of truth |
| Redis | Cache / transient coordination |
| RabbitMQ | Durable asynchronous events |
| OpenSearch | Search projections / full-text search |
| MinIO | S3-compatible attachment storage |
| Keycloak | OIDC identity provider for local/integration testing |

Their presence in Compose does **not** by itself prove full-platform readiness. Issue #20 covers completion of the application runtime around them.

## Running the project today

### Infrastructure-only development mode

From the repository root:

```bash
docker compose up -d
```

At present this starts the infrastructure defined in the compose file. Backend/frontend development may then be run according to `docs/ops/`.

Do not treat separately built `vox-itsm-backend-check` and `vox-itsm-frontend-check` containers as a complete deployment.

If backend fails with:

```text
Connection to host.docker.internal:15432 refused
SQL State: 08001
```

this indicates stale deployment configuration. The target Compose runtime must connect to PostgreSQL via `postgres:5432`.

## Authentication modes

The project supports OIDC/Keycloak-based authentication plus a development profile.

The `dev` profile deliberately relaxes normal JWT enforcement for local development and must never be used as a production configuration. A production safety guard exists to fail fast for unsafe profile combinations.

## CI and testing

Current CI verifies:

- backend tests;
- frontend type checking;
- frontend build;
- Playwright smoke in **mock mode**;
- shell/script syntax.

The current Compose smoke job is still insufficient for release qualification because it is manual-only, runs on `ubuntu-latest`, and starts infrastructure rather than the complete backend+frontend stack.

Release qualification requires a **mandatory live full-stack job on self-hosted GitHub Actions agents** with Docker Engine and Docker Compose v2.

The self-hosted gate must verify at least:

- clean PostgreSQL startup;
- full Flyway chain from an empty database;
- backend and frontend health;
- Keycloak/OIDC;
- RabbitMQ;
- Redis when enabled;
- OpenSearch;
- MinIO and bucket initialization;
- live Service Desk workflow;
- notifications and audit;
- attachments;
- persistence after restart;
- safe cleanup and failure artifacts.

## Production-readiness rules

The project must not be called production-ready until all of the following are true:

- the entire platform starts with one documented command;
- PostgreSQL is created automatically and becomes healthy;
- the full Flyway chain succeeds on a clean database and supported upgrade path;
- backend and frontend become healthy without restart loops;
- `VITE_USE_MOCK=false` works for every release-critical flow;
- OIDC authentication and authorization are verified end to end;
- mandatory domain rules are enforced server-side;
- production-critical persistence does not depend on in-memory stores;
- attachment quarantine and malware scanning are production-grade;
- OpenSearch behavior and recovery are verified;
- audit/outbox transactional consistency is verified;
- data survives service restarts;
- full Compose integration CI is non-skipped, self-hosted and green;
- Critical/High security findings are resolved;
- browser acceptance, accessibility and major degraded/error states are verified;
- backup/restore and upgrade procedures are documented and tested;
- README and release documentation match the actual system.

## Architecture principles

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
├── backend/
├── frontend/
├── docs/
├── infra/
├── scripts/
├── docker-compose.yml
├── AGENTS.md
├── INSTRUCTIONS.md
└── README.md
```

## Key documentation

Start with:

- `INSTRUCTIONS.md` — implementation requirements and Definition of Done;
- `AGENTS.md` — collaboration rules;
- `docs/architecture/` — architecture decisions and boundaries;
- `docs/security/` — security requirements;
- `docs/ops/` — local/integration operation and troubleshooting;
- `docs/ux/` — UX/design review material;
- Issue #20 — full Docker runtime blocker;
- Issue #21 — platform completion and production-readiness gate.

## Development priority

The immediate priority is **not adding more superficial feature surface**. Convert the existing breadth into a deployable, persistent, secure and verifiably complete platform.

Priority order:

1. complete Issue #20 — unified full-stack Compose runtime;
2. make live full-stack CI mandatory on self-hosted runners;
3. execute the Issue #21 end-to-end release scenario;
4. close backend/live-mode gaps where the UI is ahead of server behavior;
5. complete admin write paths for the metadata-driven platform where required by release scope;
6. harden attachments, search, outbox, authorization and observability;
7. perform adversarial security and browser acceptance testing;
8. validate backup/restore, upgrade and restart persistence;
9. only then qualify a release as production-ready.

## Target

The goal remains a complete enterprise ITSM/ESM platform comparable in operational depth to established service-management products while using an original implementation and architecture.

Quality is measured by working end-to-end processes, maintainable architecture, secure authorization, persistence, observability, testability, coherent UX and reliable deployment — not by the number of screens or mock scenarios implemented.
