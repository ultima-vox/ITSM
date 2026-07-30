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
