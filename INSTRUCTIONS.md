# INSTRUCTIONS.md

## Project overview

Enterprise ITSM/ESM platform — modular monolith, Java 25 / Spring Boot 3.5 / PostgreSQL 17 backend; React 18 / TypeScript 5.6 / Vite 8.2 frontend.

Dual-mode architecture: `VITE_USE_MOCK=true` for development, `VITE_USE_MOCK=false` for live API mode.

Build tool: **Gradle** (`backend/gradlew`). There is no Maven wrapper.

## Architecture

### Backend (modular monolith)

Modules live under `ru.ultimavox.itsm.*`:
- `servicedesk` — incidents, service requests, tasks, assignment, transitions, bulk ops
- `problemmanagement` — problem lifecycle, RCA, known errors, workarounds
- `changemanagement` — standard/normal/emergency changes, CAB, risk assessment
- `cmdb` — configuration items, relationships, impact analysis, dependency graph
- `assetmanagement` — asset lifecycle, inventory, CI linkage
- `knowledgebase` — articles, drafts, publication, voting, localization
- `servicecatalog` — catalog items, request forms, fulfillment
- `reporting` — workload, SLA, and operator metrics
- `platform` — RBAC, audit, SLA, workflow engine, metadata, form engine, search, notifications, automation, AI gateway, outbox, events, user profiles

Each module follows: `domain/` (records, enums, state machines) → `application/` (commands, queries, services) → `api/` (REST controllers, DTOs).

Shared infrastructure: `authorization/` (AccessControl, PermissionChecker chain), `audit/` (AuditTrail, AuditQuery), `event/` (DomainEvent, IntegrationEventOutbox), `workflow/` (WorkflowPolicyGateway).

### Frontend (React SPA)

- `src/api/` — API layer with mock/live branching via `isMockMode()`
- `src/api/mappers/` — backend→frontend type mapping
- `src/pages/` — page components (routed)
- `src/components/` — shared UI components
- `src/hooks/` — shared React hooks
- `src/mock/` — in-memory mock store (dev only, never production)
- `src/types/` — TypeScript interfaces
- `src/i18n/` — Russian (default), English, German catalogs (`locales/ru.json`, `en.json`, `de.json`)

### Database

Flyway migrations in `backend/src/main/resources/db/migration/`. Current chain: V1–V75. Never edit released migrations. Forward-only.

### CI/CD

GitHub Actions with self-hosted Linux runners. Pipeline: compile → frontend build → frontend tests → backend tests → artifact packaging.

## Conventions

### Code style

- Java: records for DTOs, no Lombok, text blocks for SQL, no comments unless asked
- TypeScript: strict mode, no comments unless asked, functional React components
- SQL: Flyway-compatible PostgreSQL, proper indexes and FKs

### Commit style

Conventional Commits: `feat(module): description`, `fix(module): description`, `test(module): description`. One coherent concern per commit.

### API style

REST at `/api/v1/*`. JSON request/response. UUIDs for IDs. Optimistic locking via version fields. Pagination via page/size params. Error responses use Spring's `ResponseStatusException`.

### Security

Deny-by-default RBAC. `access.require(subject, permission, objectType, objectId)` in every controller method. Object-level and field-level permissions. No production development auth.

## Running

```bash
# Full stack (infra + backend + frontend)
docker compose up -d --build
# UI: http://localhost   API: http://localhost:8080

# Infra only (host-run backend/frontend)
docker compose up -d postgres redis rabbitmq opensearch minio minio-init keycloak

# Backend on host
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev,compose'

# Frontend (mock mode)
cd frontend && VITE_USE_MOCK=true npm run dev

# Frontend (live mode)
cd frontend && npm run dev
```

## Testing

```bash
# Frontend
cd frontend && npx vitest run src

# Backend (requires Docker for Testcontainers)
cd backend && ./gradlew test
```
