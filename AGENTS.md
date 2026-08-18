# AGENTS.md

## Agent role

You are the primary implementation agent for this repository. Read and follow INSTRUCTIONS.md, README.md, and all docs under `docs/` before making changes.

## Execution rules

1. Work autonomously. Do not stop after one module, commit, or milestone.
2. After completing each task, immediately identify and execute the next highest-priority unfinished requirement.
3. Verify TypeScript compilation (`npx tsc --noEmit`), build (`npm run build`), and tests (`npx vitest run src`) after every frontend change.
4. Verify Java compilation after backend changes (check with `./mvnw compile` if Java 25 is available).
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
  platform/             — shared infrastructure (RBAC, audit, SLA, workflow, etc.)

frontend/src/
  api/                  — API layer (mock/live branching)
  api/mappers/          — backend→frontend type mapping
  pages/                — routed page components
  components/           — shared UI
  hooks/                — shared hooks
  mock/                 — in-memory dev store
  types/                — TypeScript types

backend/src/main/resources/db/migration/  — Flyway SQL migrations
infra/                  — Docker Compose, Keycloak realm
```

## Key architectural decisions

- **Modular monolith**: modules communicate via Spring beans, not HTTP. Domain events via outbox pattern.
- **Deny-by-default RBAC**: every controller method calls `access.require()`. Permission checkers are composable.
- **Optimistic locking**: version fields on all mutable entities. `OptimisticLockingFailureException` → 409 Conflict.
- **Dual-mode frontend**: `isMockMode()` branches at API layer. Mock data never leaks to live mode. Live mode calls real REST endpoints.
- **User resolution**: `user_profile` table for display names. Frontend caches profiles in `resolveUsers()`.

## Priority ordering

1. Security: fix access control, IDOR, injection, XSS
2. Live-mode correctness: every frontend function must work against the real backend
3. Backend business logic: domain rules enforced server-side, not only in frontend
4. Data integrity: migrations, FKs, indexes, constraints
5. Test coverage: critical paths, edge cases, permission matrix
6. UX: loading states, error states, empty states, responsive layouts
7. Documentation: architecture docs, operational docs

## Do not

- Use mock implementations as production implementations
- Add comments unless asked
- Mix unrelated changes in one commit
- Skip TypeScript/build verification
- Assume libraries are available — check existing imports first
- Overwrite or discard unrelated work
