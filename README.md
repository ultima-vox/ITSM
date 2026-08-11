# ITSM Platform

Enterprise-grade, metadata-driven ITSM/ESM platform designed to compete with the strongest modern IT service management systems in architecture, usability, extensibility, visual quality, reliability, and operational depth.

## Vision

Build a complete production-grade ITSM platform at the level of leading modern enterprise ITSM products, with particular attention to the functional depth associated with current Naumen-class platforms, while using an original architecture, interface, workflows, domain model, and implementation.

This is not an MVP, prototype, demo, or ticket tracker. The target is a full-scale extensible service-management platform suitable for real enterprise operation.

The platform must be:

- functionally deep;
- visually polished and consistent;
- fast and responsive;
- secure by design;
- accessible;
- observable and auditable;
- API-first;
- metadata-driven;
- modular without becoming operationally fragmented;
- ready for long-term development and extension.

Every major implementation decision should follow established engineering and UX best practices unless a documented project-specific reason requires otherwise.

## Product principles

### Quality bar

Treat UX, UI, information architecture, workflows, code quality, security, performance, accessibility, documentation, tests, and operability as first-class product requirements.

Do not accept a feature merely because it technically works. A feature is complete only when it is coherent with the rest of the platform, intuitive to use, resilient under failure, testable, documented, and visually consistent.

For visual and interaction work, use iterative review. Implementations should be reviewed critically against high-quality contemporary enterprise products and refined until there are no material usability, consistency, accessibility, or visual-quality defects.

### Primary language and internationalization

The primary interface language is **Russian**.

Internationalization must be part of the architecture from the beginning rather than added later.

Requirements:

- Russian is the default locale.
- English must be fully supported.
- Additional interface languages must be addable without changing application code.
- Users must be able to switch their interface language independently.
- User-facing strings must not be hard-coded in application logic or UI components.
- Locale-aware dates, time, numbers, plural forms, currencies, sorting, formatting, validation messages, notifications, templates, and search behavior must be supported.
- Administrators should be able to manage translations for metadata-driven objects, fields, forms, workflows, catalog items, knowledge content, and other configurable entities.

## Architecture

The initial architecture is a **modular monolith** with strict module boundaries, internal domain events, stable contracts, and a migration path for extracting individual services only where operational or scaling requirements justify it.

Do not introduce distributed-system complexity without a measurable reason.

### Core technology stack

**Backend**

- Java 25
- Spring Boot
- Modular-monolith architecture
- REST APIs
- OpenAPI
- Event-driven internal APIs

**Frontend**

- TypeScript
- React
- Responsive web application
- Design-system driven component architecture

**Data and infrastructure**

- PostgreSQL — transactional system of record
- Redis — caching, short-lived coordination, rate limiting and related transient workloads
- RabbitMQ — asynchronous messaging and integration events where durable messaging is required
- OpenSearch — full-text search and search-oriented projections
- Keycloak / OIDC — authentication, federation and identity integration
- S3-compatible object storage — attachments and binary objects

Infrastructure components are architectural dependencies, not excuses for tight coupling. The platform must degrade predictably when optional infrastructure is temporarily unavailable.

## Metadata-driven platform core

Domain features must be built on reusable platform capabilities rather than independent hard-coded subsystems.

The platform core consists of the following engines.

### Object Engine

Defines configurable business objects, attributes, relations, constraints, lifecycle metadata, indexing and presentation rules.

Examples of objects include:

- Incident
- Service Request
- Problem
- Change
- Task
- Service
- Configuration Item
- Asset
- Knowledge Article
- Organization
- User

Object definitions must allow extension without modifying core product code wherever practical.

### Workflow Engine

Provides explicit state machines and governed transitions.

A transition may contain:

- permissions;
- conditions;
- validation;
- required fields;
- synchronous actions;
- asynchronous actions;
- notifications;
- automation triggers;
- SLA effects;
- audit records.

Workflow behavior must be inspectable and deterministic.

### Form Engine

Provides metadata-driven forms, sections, field visibility, validation, dependencies, layouts, role-specific views and localized labels.

Forms must support both end-user simplicity and operator/admin depth without duplicating domain logic.

### Automation Engine

Provides declarative automation based on events, conditions and actions.

Conceptually:

```text
WHEN <event>
IF   <conditions>
THEN <actions>
```

Normal administration must not require writing code. Sandboxed scripting may exist as an advanced escape hatch, but must not become the default customization mechanism.

### Permission / RBAC Engine

Centralized authorization supporting roles, groups, scopes, ownership, object-level and field-level permissions, delegation, separation of duties and policy-aware workflow transitions.

Authorization must be enforced server-side regardless of frontend visibility.

### SLA Engine

Supports service-level targets, working calendars, priorities, pause conditions, escalations, warnings, breaches, recalculation rules and auditable timing history.

### Event Engine

Meaningful domain changes emit structured events.

Examples:

```text
incident.created
incident.assigned
incident.priority.changed
incident.resolved
asset.owner.changed
sla.warning
sla.breached
```

Events form the integration and automation backbone of the platform.

### Notification Engine

Centralized templating and delivery for platform notifications through supported channels.

Notification content must support localization, templates, user preferences, delivery policy and auditability.

### Search Engine

Provides unified search across service-management data, metadata, knowledge and permitted content while respecting authorization boundaries.

### Audit Engine

Records security-relevant and business-relevant changes in a tamper-resistant, queryable audit trail.

Audit data must answer who changed what, when, from which value to which value, and through which action or integration.

## Functional modules

Platform capabilities are exposed through cohesive functional modules.

### Service Desk

Core operational workspace for incidents, requests, tasks, queues, assignment, collaboration, communications, SLA tracking and resolution.

### Service Catalog

User-oriented catalog of services and requests with dynamic forms, approvals, fulfillment workflows, eligibility rules, localization and automation.

Users should interact with understandable business services rather than internal ITSM taxonomy whenever possible.

### CMDB

Configuration-management model for configuration items and their relationships.

CMDB must support dependency analysis and service context rather than functioning as an isolated inventory table.

Example relationship chain:

```text
Employee
  -> uses -> Workstation
  -> connected_to -> Switch
  -> located_in -> Building

Application
  -> depends_on -> Database
  -> hosted_on -> Server
```

### Asset Management

Lifecycle management for hardware, software and other managed assets, including ownership, assignment, status, procurement references, inventory data and relationships to CMDB entities.

### Knowledge Base

Versioned knowledge with structured content, permissions, localization, review lifecycle, search, feedback and links to incidents, requests, problems and services.

### Problem Management

Root-cause analysis, known errors, workarounds, relationships to incidents and controlled problem lifecycle.

### Change Management

Governed change lifecycle with risk, impact, approvals, planning, implementation, rollback, scheduling, related CIs and auditability.

## AI architecture

AI functionality must be isolated behind an **AI Gateway / Copilot layer** rather than embedded directly into critical domain logic.

Potential capabilities include:

- request classification;
- triage suggestions;
- summarization;
- similar-ticket discovery;
- knowledge retrieval;
- response drafting;
- operator assistance;
- change-risk assistance;
- trend and anomaly analysis.

AI must operate under explicit authorization and data-access boundaries.

AI output is advisory by default. Autonomous changes to sensitive or operationally significant data require explicit policy, validation and audit controls.

Model providers must remain replaceable.

## UX and design system

The platform must have a coherent design system rather than page-specific styling.

The design system should define:

- typography;
- spacing;
- color tokens;
- elevation;
- iconography;
- responsive behavior;
- density modes;
- interaction states;
- forms;
- tables;
- navigation;
- dashboards;
- notifications;
- accessibility behavior;
- loading, empty, error and degraded states.

The interface must remain usable in dense operator workflows without becoming visually overloaded.

End-user portals and operator workspaces may have different information density, but must belong to one coherent product language.

Accessibility is a product requirement, not a later compliance pass.

## Security principles

Security must be designed into every layer.

Minimum principles:

- OIDC-based authentication;
- MFA support through the identity provider;
- least privilege;
- server-side authorization;
- RBAC and scoped permissions;
- protection against IDOR/BOLA;
- CSRF/XSS/SSRF/SQL-injection controls;
- secure file handling;
- malware-scanning integration points;
- secret isolation;
- secure defaults;
- immutable or protected audit records;
- rate limiting and abuse controls;
- dependency and container vulnerability scanning;
- explicit trust boundaries for integrations and AI;
- no credentials or secrets committed to the repository.

Security-sensitive functionality requires dedicated review and negative testing.

## API and integration principles

The system is API-first.

All important product operations must have stable server-side contracts usable by future clients and integrations.

This allows the web UI to remain one client among several possible clients, including future Windows, Android and iOS applications.

Integration mechanisms include:

- REST/OpenAPI;
- outbound events;
- webhooks where appropriate;
- durable asynchronous messaging where required;
- identity federation;
- import/export interfaces.

Expected integration domains include AD/LDAP, monitoring, mail, telephony, business applications, inventory systems, security systems and collaboration tools.

## Engineering principles

### Modular monolith first

Start with a modular monolith.

A module must have explicit ownership of its domain model and persistence boundaries. Cross-module interaction should use public application contracts and domain events rather than arbitrary repository or table access.

Microservices are not a project goal. A module may be extracted only when there is a concrete scalability, isolation, ownership or deployment requirement.

### Data ownership

PostgreSQL is the transactional source of truth.

OpenSearch, Redis and other derived stores must be reconstructable from authoritative data or events wherever practical.

### Reliability

Design explicitly for:

- retries;
- idempotency;
- duplicate messages;
- partial failures;
- timeouts;
- unavailable dependencies;
- transactional consistency boundaries;
- eventual consistency where deliberately chosen.

### Observability

The system must provide structured logs, metrics, traces, health checks and meaningful operational diagnostics.

A production failure must be diagnosable without attaching a debugger to the server.

### Testing

The project requires multiple testing levels:

- unit tests;
- module/integration tests;
- database migration tests;
- contract/API tests;
- security tests;
- accessibility tests;
- end-to-end tests;
- performance tests for critical workflows;
- visual regression tests for key UI surfaces.

Critical business behavior must not depend solely on end-to-end testing.

## Multi-agent development model

Development may use specialized AI agents in parallel, but parallelism must not create uncontrolled concurrent ownership of the same implementation surface.

Use agents according to clear responsibilities, for example:

- architecture and domain modeling;
- backend implementation;
- frontend implementation;
- UX/design-system review;
- security review;
- testing and QA;
- performance review;
- documentation;
- critical independent review.

For each significant feature:

1. establish requirements and acceptance criteria;
2. assign one implementation owner for each write scope;
3. allow specialist agents to research or review in parallel;
4. implement the smallest coherent vertical slice;
5. test functional and non-functional behavior;
6. perform independent code, security and UX review;
7. fix material findings;
8. repeat review until acceptance criteria are satisfied.

Review agents should be intentionally critical. "Works on my machine" and subjective enthusiasm are not acceptance criteria.

Do not use blind side-by-side comparison with proprietary products as a substitute for measurable quality criteria. Competitive products may be studied for publicly observable patterns, but implementation must remain original and should be evaluated against explicit requirements, usability heuristics, accessibility, performance targets and project design standards.

## Definition of Done

A feature is not done until, where applicable:

- acceptance criteria are satisfied;
- architecture boundaries are respected;
- authorization is enforced server-side;
- migrations are safe;
- APIs are documented;
- Russian and English localization are complete;
- accessibility has been checked;
- failure and empty states are implemented;
- tests pass;
- audit requirements are satisfied;
- observability is sufficient;
- relevant security review is complete;
- UI matches the design system;
- documentation is updated;
- no known critical or high-severity defect remains.

## Initial repository direction

The repository should evolve toward a structure similar to:

```text
ITSM/
├── backend/
├── frontend/
├── docs/
│   ├── architecture/
│   ├── adr/
│   ├── product/
│   ├── security/
│   └── ux/
├── deploy/
├── tools/
├── .agents/
│   └── skills/
├── AGENTS.md
└── README.md
```

The exact structure may evolve through Architecture Decision Records, but architectural changes must be deliberate and documented.

## Target

The objective is a complete enterprise ITSM/ESM platform whose quality is evident not from claims of being "AAA", but from measurable characteristics: coherent UX, strong information architecture, reliable workflows, secure authorization, extensibility, localization, performance, accessibility, observability, test coverage and maintainable engineering.

Build for production from the beginning.

## Implemented foundation

This repository now contains a production-oriented modular-monolith foundation:

- **`frontend/`** — Vite + React + TypeScript operator workspace (AAA UI shell): design-system tokens, Russian-default / English locale switcher, queues, work-item surfaces, accessible controls.
- **`backend/`** — Java 25 / Spring Boot modular monolith (`ru.ultimavox.itsm`): platform engines (metadata, workflow, forms, automation, RBAC, SLA, audit, outbox, search, AI gateway), Service Desk operator API, Change / Problem / CMDB / Asset / Knowledge / Catalog modules, versioned Flyway migrations, OpenAPI groups, OIDC JWT security (Keycloak), optional **dev** + **compose** profiles.
- **`docker-compose.yml`** — PostgreSQL, Redis (AOF), RabbitMQ, OpenSearch, MinIO (+ bucket init), Keycloak realm import.
- **`docs/`** — architecture, ADRs, product notes, security, UX quality gates, [compose integrations](docs/ops/compose-integrations.md).
- **Production images** — non-root backend and nginx frontend Dockerfiles; see [production deployment](docs/ops/production-deployment.md).

### Current maturity

| Layer | Status |
| --- | --- |
| Platform engines (Object / Workflow / Form / Automation / RBAC / SLA / Audit / Outbox / Search / AI gateway) | Implemented (V10+ seed + services) |
| Service Desk (work items, assign, transition, comments, activity, stats) | Implemented (V11 operator model + API) |
| Business modules (Change, Problem, CMDB, Asset, Knowledge, Catalog) | Implemented (V12 extensions + seed demo data) |
| Frontend operator shell | Live `/api/v1` by default; explicit `VITE_USE_MOCK=true` demo mode |
| Redis distributed cache | Wired via `compose` profile (`CachePort` + fallback); locale prefs cached |
| OpenSearch full-text | Wired via `compose` profile; index bootstrap; work-item projections; JDBC fallback when URL empty |
| Attachments / MinIO | S3 port + compose MinIO bucket; local metadata mode by default |

### How to run — infrastructure (docker-compose)

```bash
# From repository root
docker compose up -d
```

Services (defaults):

| Service | Port(s) | Notes |
| --- | --- | --- |
| PostgreSQL | `5432` | DB/user/password: `itsm` / `itsm` / `itsm` |
| Redis | `6379` | AOF persistence; enable in app with profile `compose` |
| RabbitMQ | `5672`, UI `15672` | Outbox relay publishes to `itsm.events` |
| OpenSearch | `9200` | Security plugin disabled; enable with `OPENSEARCH_URL` / profile `compose` |
| MinIO | `9000`, console `9001` | Bucket `itsm-attachments` via `minio-init`; `minioadmin` / `minioadmin` |
| Keycloak | `8081` → container `8080` | Admin `admin` / `admin`; realm **`itsm`** imported from `infra/keycloak/`; issuer `http://localhost:8081/realms/itsm` |

Realm demo users, clients, and token curl examples: [`infra/keycloak/README.md`](infra/keycloak/README.md).

Stop: `docker compose down` (add `-v` to wipe volumes).

### How to run — backend

Requirements: **Java 25**, Postgres (and optionally full compose stack). Gradle wrapper is included.

```bash
cd backend

# Production-like local run (JWT required; Keycloak must be up for issuer metadata)
./gradlew bootRun

# Local demo when OIDC is down — NEVER use in production
./gradlew bootRun --args='--spring.profiles.active=dev'

# Full stack: Redis + OpenSearch + MinIO (after docker compose up -d)
./gradlew bootRun --args='--spring.profiles.active=dev,compose'
# See backend/.env.compose and docs/ops/compose-integrations.md
```

Useful URLs:

- Health: `http://localhost:8080/actuator/health` (redisCache / opensearch when compose on)
- Integrations: `GET /api/v1/platform/integrations`
- Search: `GET /api/v1/search?q=…`
- OpenAPI UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI groups: Service Desk, Change & Problem, CMDB & Assets, Knowledge & Catalog, Platform

Environment overrides (optional):

| Variable | Default |
| --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/itsm` |
| `DATABASE_USER` / `DATABASE_PASSWORD` | `itsm` / `itsm` |
| `OIDC_ISSUER_URI` | `http://localhost:8081/realms/itsm` |
| `RABBITMQ_HOST` / `PORT` / `USER` / `PASSWORD` | `localhost` / `5672` / `guest` / `guest` |
| `ITSM_CORS_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` |
| `ITSM_REDIS_ENABLED` | `false` (true under profile `compose`) |
| `OPENSEARCH_URL` | empty → JDBC search (`http://localhost:9200` under `compose`) |
| `ITSM_STORAGE_TYPE` | `local` (`s3` under `compose`) |

**CORS:** backend allows the Vite origin `http://localhost:5173` (and `127.0.0.1`) so the SPA can call APIs with credentials/JWT.

**Security (default / non-dev):** all `/api/**` routes require a valid OIDC JWT. Public: `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`. Fine-grained permissions are enforced via `AccessControl` + RBAC seeds.

**Security (profile `dev` only):** JWT resource-server auto-config is disabled; a synthetic principal `dev-local` is injected. Clear console warning is logged. Do not enable in production.

### How to run — frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. Default UI language is **Russian**; use the header language control for **English** (stored per browser profile). Point the SPA at the backend via the frontend API client base URL (see `frontend/src/api/`).

### Module map and API base paths

| Module | Package | API base path |
| --- | --- | --- |
| Service Desk | `servicedesk` | `/api/v1/work-items` |
| Change Management | `changemanagement` | `/api/v1/changes` |
| Problem Management | `problemmanagement` | `/api/v1/problems` |
| CMDB | `cmdb` | `/api/v1/cmdb` |
| Asset Management | `assetmanagement` | `/api/v1/assets` |
| Knowledge Base | `knowledgebase` | `/api/v1/knowledge` |
| Service Catalog | `servicecatalog` | `/api/v1/catalog` |
| Object / Form metadata | `platform.metadata` / `platform.forms` | `/api/v1/metadata/objects`, `/api/v1/metadata/forms` |
| Locale preference | `platform.localization` | `/api/v1/me/locale` |
| AI Copilot | `platform.ai` | `/api/v1/ai/copilot` |

Platform engines live under `ru.ultimavox.itsm.platform.*` (workflow, SLA, automation, authorization, audit, outbox, search, cache port).

Flyway migrations: `backend/src/main/resources/db/migration/`; current schema ends at `V66__working_calendar_catalog.sql`.

### Locale management

- **Product default:** Russian (`ru`); English (`en`) fully supported; German (`de`) listed as supported interface locale in the backend preference service.
- **Backend:** `GET/PUT /api/v1/me/locale` — durable, organization-scoped per-subject preference.
- **Metadata i18n:** `translation` table + labels embedded in object/form/workflow seed JSON (RU/EN).
- **Frontend:** `frontend/src/i18n` message catalogs; header switcher persists user choice locally.

### Run tests (backend)

```bash
cd backend
./gradlew test
```

Unit tests cover domain aggregates, workflow engine, service-desk use cases (including WorkflowEngine ObjectProvider integration), cache fallback, JWT authority mapping, and related platform services. Full Spring context / DB integration tests are optional and require Postgres.
