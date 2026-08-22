# Vox ITSM

Enterprise ITSM/ESM platform under active development.

**Product benchmark:**

> **Vox ITSM = ServiceNow depth + JSM usability + Naumen enterprise practicality.**

The objective is not to clone any proprietary product. Vox ITSM must use an original architecture and UX while matching or exceeding the useful qualities of those reference points:

- **ServiceNow depth** — platform core, CMDB, workflow, automation, metadata, service catalog, governance, reporting, extensibility, integrations and enterprise administration;
- **Jira Service Management usability** — fast operator workflows, strong queues and triage, keyboard productivity, clean information hierarchy, collaboration and low-friction daily work;
- **Naumen enterprise practicality** — configurable enterprise processes, strong localization, realistic corporate deployment, directory/integration support and practical administration.

The target is a **fully operational production-grade enterprise ITSM/ESM platform**, not an MVP, demo or ticket tracker.

**The repository is not production-ready yet.**

## Current status

**Status date:** 2026-08-22

The backend/platform foundation already contains substantial live functionality, but the current frontend must be treated as a **POC / design and interaction reference**, not as the final production UI.

Existing useful frontend ideas and components may be retained, but no current screen, component or interaction is a compatibility constraint if it prevents the target product quality.

A reasonable current characterization is:

- backend/platform: advanced foundation under production hardening;
- frontend: POC with broad functional coverage;
- full product: pre-production / integration-stage;
- production readiness: not yet achieved.

## Target product structure

Vox ITSM is being developed as three distinct user experiences sharing one design system and platform core:

```text
Vox ITSM
├── Operator Workspace
│   └── dense, fast, keyboard-oriented, configurable
│
├── User Portal
│   └── simple, friendly, branded, mobile-friendly
│
└── Administration Studio
    └── powerful, safe, discoverable, no-code where practical
```

### Operator Workspace

The main Service Desk workspace must optimize daily operator productivity:

- queues and saved views;
- fast triage;
- keyboard-first workflows;
- split-pane/list/detail modes;
- configurable columns and filters;
- SLA prioritization;
- activity timeline;
- related CI / Problem / Change / Knowledge context;
- bulk actions;
- live backend truth only.

### User Portal

The requester-facing portal must behave like a modern digital product rather than an internal IT form:

- service catalog;
- knowledge search;
- request tracking;
- approvals;
- comments and attachments;
- mobile/responsive UX;
- organization branding;
- localization;
- business-friendly language.

### Administration Studio

Platform administration must not require editing SQL, source code or repository files for normal operations.

The target includes live administration of:

- users / identity mappings / roles;
- services and catalog;
- metadata / objects / fields;
- forms;
- workflows;
- SLA / OLA / calendars;
- queues and assignment rules;
- automation;
- notifications;
- dictionaries;
- CMDB classes and relations;
- integrations;
- branding and localization;
- system/security settings;
- audit and configuration history.

## Architecture

The platform is a **Java 25 / Spring Boot modular monolith** with a **React + TypeScript** web client.

Core architectural rules:

- modular monolith first;
- PostgreSQL as transactional source of truth;
- server-side authorization and business-rule enforcement;
- API-first contracts;
- metadata-driven platform capabilities;
- event-driven integration where appropriate;
- derived stores must not silently become authoritative;
- no hidden production fallback to mock/in-memory behavior;
- secure defaults;
- auditable mutations;
- Russian-first UX with full i18n architecture;
- accessibility and operator efficiency as first-class requirements.

### Supporting infrastructure

| Service | Purpose |
| --- | --- |
| PostgreSQL 17 | Transactional source of truth |
| Redis | Cache / transient coordination |
| RabbitMQ | Durable asynchronous events |
| OpenSearch | Search projections / full-text search |
| MinIO | S3-compatible attachment storage |
| Keycloak | OIDC identity and federation boundary |

## Implemented platform foundation

The backend already contains substantial foundations for:

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
- PostgreSQL-backed notifications;
- search abstraction with OpenSearch support;
- attachment storage abstraction with S3-compatible support;
- AI Gateway / Copilot boundary;
- OpenAPI;
- health/actuator endpoints.

PR #19 (`production depth wave15`) is merged into `main` and added, among other things:

- PostgreSQL-backed notifications;
- live audit API;
- Service Desk watchers and related links;
- work-item ↔ CI links;
- escalation and reopen behavior;
- CMDB relation editing, orphan detection and multi-hop impact;
- Problem resolution gates;
- Change schedule conflict detection;
- CAB votes API;
- PostgreSQL-backed workload reports;
- Knowledge live write/publish paths;
- Asset and CMDB create paths;
- live read APIs for SLA / automation / workflow / RBAC administration;
- production safety guard against insecure dev authentication;
- stronger attachment content-signature checks;
- expanded Flyway migrations and permissions.

These capabilities are part of `main`, but release acceptance requires full live-stack verification.

## Current major gaps

The largest remaining gaps are not a lack of screens. They are productization, integration and production proof:

- full one-command Docker runtime;
- mandatory self-hosted live CI;
- production identity and AD/LDAP federation;
- production frontend architecture and design system;
- complete live Service Desk operator flow;
- polished User Portal and Service Catalog;
- full no-code Administration Studio;
- CMDB/Asset enterprise depth;
- advanced Problem/Change/CAB/Knowledge governance;
- integration framework and production connectors;
- white-label/rebranding;
- reporting/observability/backup/performance;
- security/HA/release qualification.

## Master roadmap

The authoritative product roadmap is **Issue #23**:

**`[MASTER ROADMAP] Vox ITSM — ServiceNow depth + JSM usability + Naumen enterprise practicality`**

Later issues must not be started merely because they are open. The execution order and dependency gates in #23 are authoritative.

## Mandatory execution order

### Wave A — platform foundation

1. **#20 — Stage 0: Runtime Foundation**
   - complete Docker Compose runtime;
   - backend + frontend + infrastructure in one project;
   - Compose DNS instead of `host.docker.internal`;
   - health/readiness;
   - clean Flyway install;
   - persistence verification;
   - mandatory live CI on self-hosted GitHub Actions runners.

2. After #20 is sufficiently stable, run in parallel:
   - **#22 — Stage 1: Production Identity**;
   - **#34 — Stage 1.5: Frontend Product Foundation**.

### Wave B — core product

3. **#24 — Stage 2: Service Desk Core**.

4. Once #24 is structurally stable, run in parallel:
   - **#25 — Stage 3: User Portal + Service Catalog**;
   - **#26 — Stage 4: Administration Studio**.

### Wave C — enterprise ITSM depth

5. After the admin/configuration foundation is stable, run in parallel:
   - **#27 — Stage 5: CMDB + Asset Management**;
   - **#28 — Stage 6: Advanced ITSM — Problem, Change/CAB, Knowledge**.

### Wave D — ecosystem and productization

6. **#29 — Stage 7: Enterprise Integration Hub**.

7. **#30 — Stage 8: Branding + Productization** may overlap late Stage 7 work when scopes do not conflict.

### Wave E — operations and intelligence

8. **#31 — Stage 9: Analytics + Operations**.

9. **#32 — Stage 10: AI Copilot + Intelligent Automation** only after deterministic platform behavior is trustworthy. AI does not block the first production release unless explicitly included in release scope.

### Wave F — production qualification

10. **#33 — Stage 11: Security + HA + Release Qualification**.

11. **#21 — final production-readiness gate** is closed last.

## Stage issues

| Issue | Stage | Purpose |
| --- | --- | --- |
| #20 | 0 | Unified full-stack Docker runtime and self-hosted live CI |
| #22 | 1 | AD/LDAP federation, SSO, MFA, production Keycloak, enterprise RBAC |
| #34 | 1.5 | Production frontend architecture, design system and shared UX foundation |
| #24 | 2 | Production Service Desk operator workspace and ticket lifecycle |
| #25 | 3 | User Portal and Service Catalog |
| #26 | 4 | No-code Administration Studio |
| #27 | 5 | CMDB and Asset Management enterprise depth |
| #28 | 6 | Problem, Change/CAB, Knowledge and governance depth |
| #29 | 7 | Integration Hub — AD, 1C, Bitrix24, email, REST/webhooks, monitoring and future connectors |
| #30 | 8 | White-label, rebranding, localization and organization boundaries |
| #31 | 9 | Reporting, analytics, observability, backup/restore and performance |
| #32 | 10 | Governed AI Copilot and intelligent automation |
| #33 | 11 | Security, HA, DR and final release qualification |
| #21 | Final gate | Production-readiness acceptance and evidence |
| #23 | Master | Product benchmark, execution order and roadmap |

## Enterprise identity target

Authentication must use Keycloak/OIDC as the identity boundary.

Target architecture:

```text
Active Directory / LDAP / Entra ID / external IdP
                      ↓
                   Keycloak
                      ↓ OIDC / PKCE
              frontend / backend
                      ↓
                  ITSM RBAC
```

Required production capabilities include:

- AD/LDAP federation over LDAPS;
- deterministic user and group mapping;
- AD group → ITSM role mapping;
- MFA for privileged roles;
- strict JWT issuer/audience/expiry validation;
- production Keycloak with persistent storage;
- no demo credentials or fixed secrets;
- break-glass administrator procedure;
- audited identity/role changes.

## Enterprise integration target

Vox ITSM must not accumulate hard-coded point-to-point integrations.

Stage #29 builds a first-class Integration Hub with stable connector contracts, retries, idempotency, mapping, execution history, diagnostics and secure secret handling.

Mandatory integration directions include:

- Active Directory / LDAP;
- 1C;
- Bitrix24;
- inbound/outbound email;
- generic REST and webhooks;
- monitoring/event sources such as Zabbix/Prometheus-class systems;
- extensibility for Teams, Telegram, Slack, telephony and other enterprise systems.

Customer-specific schemas must be configured through mapping rather than embedded into the platform domain model.

## Branding and white-label target

The platform must support rebranding without rebuilding frontend images.

Target configuration includes:

- product/company name;
- logos;
- favicon/app icon;
- primary/secondary/accent colors;
- portal/login hero assets;
- light/dark/high-contrast variants;
- email branding;
- footer/legal/support links;
- localized terminology/content;
- custom domains through supported deployment configuration.

The same central design-token source must feed Operator Workspace, User Portal and Administration Studio.

## Running the project today

The current repository does **not yet** provide the final verified one-command production-shaped runtime.

Infrastructure can currently be started from the repository root with:

```bash
docker compose up -d
```

Do not treat separately built `vox-itsm-backend-check` and `vox-itsm-frontend-check` containers as a complete ITSM deployment.

If backend fails with an error similar to:

```text
Connection to host.docker.internal:15432 refused
SQL State: 08001
```

this indicates stale deployment configuration. The target Compose runtime must use service DNS such as `postgres:5432` for internal communication.

Issue #20 owns completion of the final runtime path:

```bash
docker compose up -d --build
```

## CI and testing

Current CI provides useful fast feedback, including backend tests, frontend type checking/build and mock-mode Playwright smoke.

That is not sufficient for release qualification.

The target release pipeline requires mandatory **live full-stack E2E on self-hosted GitHub Actions runners** with Docker Engine and Docker Compose v2.

The live gate must verify at least:

- clean PostgreSQL startup;
- full Flyway chain;
- backend/frontend health;
- Keycloak/OIDC;
- RabbitMQ;
- Redis where enabled;
- OpenSearch;
- MinIO and bucket initialization;
- live Service Desk lifecycle;
- notifications and audit;
- attachments;
- persistence after restart;
- safe cleanup;
- retained diagnostics/logs on failure.

## Cross-cutting Definition of Done

Every stage must satisfy, where applicable:

- server-side authorization;
- server-side business-rule enforcement;
- PostgreSQL persistence for transactional state;
- safe Flyway migrations;
- auditability;
- no mock/in-memory production-critical path;
- correct loading/empty/error/degraded UI states;
- Russian-first UX with full i18n architecture;
- keyboard accessibility and usable focus behavior;
- realistic live integration tests;
- self-hosted CI where a complete Docker stack is required;
- documentation and troubleshooting;
- critical UX review against the benchmark in #23.

## Production-readiness rules

Vox ITSM must not be described as production-ready until:

- #20 is closed with full runtime evidence;
- required roadmap stages for the release are closed;
- AD/enterprise identity is production-qualified;
- Service Desk and User Portal work end to end in live mode;
- administrators can configure release-critical behavior without source-code/SQL edits;
- production-critical integrations are proven;
- data survives restart and supported failure scenarios;
- backup/restore and upgrade paths are tested;
- Critical/High security findings are resolved;
- performance and capacity assumptions are documented;
- browser/accessibility acceptance passes;
- mandatory self-hosted live CI is green;
- Issue #33 qualification passes;
- Issue #21 is closed against a specific tested release commit.

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

## Key project references

Start with:

- **#23** — master product roadmap and mandatory execution order;
- **#20** — full Docker runtime blocker;
- **#22** — production identity / AD / MFA;
- **#34** — frontend production foundation;
- **#24** — Service Desk Core;
- **#25** — User Portal + Service Catalog;
- **#26** — Administration Studio;
- **#29** — Enterprise Integration Hub;
- **#30** — branding/productization;
- **#33** — final production qualification;
- **#21** — final release gate;
- `INSTRUCTIONS.md` — implementation requirements and Definition of Done;
- `AGENTS.md` — collaboration and ownership rules;
- `docs/architecture/` — architecture decisions;
- `docs/security/` — security requirements;
- `docs/ops/` — runtime/operations documentation;
- `docs/ux/` — UX/design material.

## Development priority

The immediate priority is not adding more superficial screens.

The project must now convert existing breadth into a deployable, persistent, secure, configurable, integrable and pleasant enterprise product.

The first implementation sequence is therefore:

```text
#20
 ↓
#22 + #34
 ↓
#24
 ↓
#25 + #26
 ↓
#27 + #28
 ↓
#29
 ↓
#30
 ↓
#31
 ↓
#33
 ↓
#21
```

`#32` AI/Copilot enters only after the deterministic core is stable enough to trust.

## Final target

Vox ITSM should be judged by working enterprise journeys rather than the number of implemented pages.

The intended result is a platform that is:

- deep enough for enterprise process management;
- faster and friendlier for operators than traditional heavyweight ITSM systems;
- simple and attractive for requesters;
- practical for administrators;
- integrable with corporate systems;
- brandable;
- secure;
- observable;
- recoverable;
- configurable without constant development work.

**Vox ITSM = ServiceNow depth + JSM usability + Naumen enterprise practicality.**
