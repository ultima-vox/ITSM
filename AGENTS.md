# AGENTS.md

## Agent role

You are the primary implementation agent for this repository. Read and follow INSTRUCTIONS.md, README.md, and all docs under `docs/` before making changes.

## Execution rules

1. Work autonomously. Do not stop after one module, commit, or milestone.
2. After completing each task, immediately identify and execute the next highest-priority unfinished requirement.
3. Verify TypeScript compilation (`npx tsc --noEmit`), build (`npm run build`), and tests (`npx vitest run src`) after every frontend change.
4. Verify Java compilation after backend changes (`./gradlew classes` or `./gradlew test` if Java 25 is available).
5. Commit after coherent milestones with Conventional Commit messages.
6. Never mix unrelated changes in the same commit.
7. Never force-push or rewrite shared history.

## Repository structure

```
backend/src/main/java/ru/ultimavox/itsm/
  servicedesk/          — incident, request, task lifecycle
  problemmanagement/    — problem lifecycle
  changemanagement/     — change lifecycle
  cmdb/                 — configuration items, relationships
  assetmanagement/      — asset lifecycle
  knowledgebase/        — articles, voting
  servicecatalog/       — catalog items, fulfillment
  reporting/            — workload and SLA reports
  platform/             — shared infrastructure (RBAC, audit, SLA, workflow, etc.)

frontend/src/
  api/                  — API layer (mock/live branching)
  api/mappers/          — backend→frontend type mapping
  pages/                — routed page components
  components/           — shared UI
  hooks/                — shared hooks
  mock/                 — in-memory dev store
  types/                — TypeScript types
  i18n/locales/         — ru (default), en, de

backend/src/main/resources/db/migration/  — Flyway SQL (V1–V75)
infra/                  — Keycloak realm
docker-compose.yml      — full local stack (infra + backend + frontend)
```

## Key architectural decisions

- **Modular monolith**: modules communicate via Spring beans, not HTTP. Domain events via outbox pattern.
- **Deny-by-default RBAC**: every controller method calls `access.require()`. Permission checkers are composable.
- **Optimistic locking**: version fields on all mutable entities. `OptimisticLockingFailureException` → 409 Conflict.
- **Dual-mode frontend**: `isMockMode()` branches at API layer. Mock data never leaks to live mode. Live mode calls real REST endpoints.
- **User resolution**: `user_profile` table for display names. Frontend caches profiles in `resolveUsers()`.
- **Build**: Gradle wrapper in `backend/` (`./gradlew`). No Maven.
- **Locales**: Russian default; English and German catalogs. Do not claim languages that have no message files.

## Priority ordering

1. Security: fix access control, IDOR, injection, XSS
2. Live-mode correctness: every frontend function must work against the real backend
3. Deployable Compose runtime: one `docker compose up -d --build` path
4. Backend business logic: domain rules enforced server-side, not only in frontend
5. Data integrity: migrations, FKs, indexes, constraints
6. Test coverage: critical paths, edge cases, permission matrix
7. UX: loading states, error states, empty states, responsive layouts
8. Documentation: architecture docs, operational docs — must match the actual system

## Do not

- Use mock implementations as production implementations
- Add comments unless asked
- Mix unrelated changes in one commit
- Skip TypeScript/build verification
- Assume libraries are available — check existing imports first
- Overwrite or discard unrelated work
- Claim production readiness, extra locales, or live integrations that are not verified
