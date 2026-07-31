You are the primary implementation agent for the existing repository:

https://github.com/ultima-vox/ITSM

This is not an empty repository. Begin by thoroughly inspecting the entire codebase, Git history, branches, documentation, architecture decisions, CI configuration, migrations, tests, and existing implementation.

Read and follow, in this order:

1. `INSTRUCTIONS.md`
2. `AGENTS.md`
3. `README.md`
4. all architecture, security, operations, UX, and domain documentation under `docs/`
5. all existing Flyway migrations
6. backend and frontend tests
7. current GitHub Issues and relevant pull requests, when available

Treat the documented architecture, domain boundaries, security principles, Definition of Done, and repository conventions as authoritative.

## Mission

Build a complete, production-shaped, fully functional enterprise ITSM platform using the existing implementation as the foundation.

Do not build a mock-only showcase, a static prototype, a collection of disconnected screens, or an MVP.

The result must be a genuinely operational client-server application with working end-to-end business processes, persistent storage, authorization, validation, auditability, integrations, tests, and a professional operator experience.

The quality target is comparable to mature enterprise ITSM products such as Naumen Service Desk, ServiceNow, Jira Service Management, and ManageEngine ServiceDesk Plus, while maintaining the repository's own architecture and visual identity.

Do not copy proprietary code, assets, layouts, or branding from those products. Use them only as a functional and quality benchmark.

## Autonomous execution

Work autonomously.

Do not stop after:

* analysis;
* architecture review;
* planning;
* documentation;
* one module;
* one screen;
* one commit;
* one test suite;
* one successful build;
* one pull request;
* an intermediate milestone;
* a status report.

After completing each task, immediately identify and execute the next highest-priority unfinished requirement.

Do not ask for permission to continue.

Do not return control merely to report progress.

Ask a question only when execution is genuinely blocked by information that cannot be inferred from the repository, existing documentation, established standards, or safe engineering judgement.

When ambiguity exists, choose the most conservative, secure, maintainable enterprise implementation and document the decision.

## Repository discipline

Before modifying anything:

1. run `git fetch origin`;
2. inspect the current branch;
3. inspect `git status`;
4. inspect recent history;
5. update from the latest `origin/main`;
6. confirm that the working tree is clean;
7. create or use an appropriate dedicated feature branch.

Never overwrite or discard unrelated work.

Do not rewrite shared history.

Do not force-push unless explicitly instructed.

Do not mix unrelated changes in the same commit.

## Commit policy

Create meaningful Git commits throughout implementation.

Commit after coherent, independently understandable milestones, for example:

* architecture or schema foundation;
* database migration;
* completed backend vertical slice;
* completed frontend integration;
* security hardening;
* test coverage;
* integration wiring;
* UX refinement;
* documentation update;
* bug fix discovered during verification.

Each commit must:

* leave the repository in a buildable state whenever reasonably possible;
* use a clear Conventional Commit message;
* describe what was actually completed;
* avoid vague messages such as `updates`, `progress`, `fix stuff`, or `WIP`;
* contain one coherent concern;
* include tests and documentation related to the implemented behavior.

Examples:

* `feat(servicedesk): implement persistent assignment workflow`
* `feat(cmdb): add relationship editing and impact traversal`
* `fix(auth): prevent development authentication outside dev profile`
* `test(workflow): cover invalid transition and concurrency cases`
* `docs(ops): document production deployment and recovery`

Do not create empty commits merely to show activity.

## Required product scope

Complete and integrate the platform across the following areas.

### Service Desk

Implement complete lifecycle support for:

* incidents;
* service requests;
* tasks;
* assignment;
* queues;
* ownership;
* comments;
* attachments;
* watchers;
* related records;
* priority;
* impact;
* urgency;
* categorization;
* statuses;
* transitions;
* resolution;
* reopening;
* duplicate handling;
* escalation;
* activity history;
* audit history;
* bulk operations;
* search;
* filtering;
* sorting;
* pagination;
* saved views where architecturally appropriate.

All lifecycle rules must be enforced on the backend, not only in the frontend.

### Service Catalog

Implement:

* catalog categories;
* localized catalog items;
* metadata-driven request forms;
* validation;
* approval chains;
* fulfillment workflows;
* service ownership;
* SLA assignment;
* request creation;
* request status tracking;
* versioned catalog definitions;
* publication states.

### CMDB

Implement:

* configuration item classes;
* attributes;
* inheritance where supported by the architecture;
* CI creation and editing;
* CI lifecycle;
* relationships;
* relationship validation;
* dependency graph;
* impact analysis;
* orphan detection;
* search;
* filtering;
* history;
* audit;
* import-ready interfaces;
* linkage to incidents, problems, changes, services, and assets.

The graphical CMDB must use real backend data when live mode is enabled.

### Asset Management

Implement:

* asset lifecycle;
* inventory data;
* ownership;
* assignment;
* locations;
* financial fields;
* status;
* warranty;
* supplier;
* linkage to CIs;
* history;
* search;
* filtering;
* bulk operations;
* audit.

Keep Asset and Configuration Item concepts distinct.

### Problem Management

Implement:

* problem lifecycle;
* root cause analysis;
* known errors;
* workarounds;
* linked incidents;
* linked changes;
* impact;
* priority;
* ownership;
* resolution gates;
* audit;
* search;
* filtering;
* bulk operations.

Do not permit resolution without the required root-cause and resolution data.

### Change Management

Implement:

* standard, normal, and emergency changes;
* risk assessment;
* impact assessment;
* implementation plan;
* rollback plan;
* test plan;
* scheduling;
* CAB review;
* CAB votes;
* approval and rejection;
* conflict detection;
* change calendar;
* linked CIs;
* linked incidents and problems;
* implementation;
* review;
* closure;
* audit;
* bulk-operation safety rules.

Single-item and bulk transitions must enforce identical backend business constraints.

### Knowledge Management

Implement:

* articles;
* drafts;
* review;
* publication;
* versioning;
* localization;
* categories;
* tags;
* search;
* related articles;
* feedback;
* helpfulness score;
* contribution flow;
* permissions;
* audit;
* linkage to incidents, requests, problems, and catalog items.

### SLA

Implement a persistent, testable SLA runtime supporting:

* policies;
* business calendars;
* holidays;
* time zones;
* response deadlines;
* resolution deadlines;
* pause;
* resume;
* breach;
* escalation;
* recalculation;
* event history;
* multiple clocks where required;
* assignment based on service, priority, customer, and request type.

SLA behavior must be deterministic and covered by edge-case tests.

### Workflow Engine

Implement and harden:

* versioned definitions;
* states;
* transitions;
* guards;
* permissions;
* required fields;
* actions;
* instances;
* persistence;
* optimistic concurrency;
* transition history;
* rollback-safe transactions;
* integration with audit, outbox, notifications, SLA, and automation.

### Metadata and Form Engines

Implement:

* object definitions;
* attribute definitions;
* relationships;
* validation;
* versioning;
* publication;
* dynamic forms;
* conditional fields;
* required fields;
* localized labels;
* frontend rendering;
* backend validation;
* admin interfaces.

Never trust frontend metadata validation alone.

### Automation

Implement:

* event triggers;
* conditions;
* allowlisted actions;
* rule priority;
* retries;
* idempotency;
* execution log;
* failure handling;
* enable/disable;
* versioning;
* safe expression evaluation;
* audit.

Automation must never allow arbitrary code execution.

### Notifications

Replace temporary in-memory or logging-only behavior with a persistent notification subsystem.

Implement:

* PostgreSQL persistence;
* user targeting;
* roles or groups where appropriate;
* unread/read state;
* pagination;
* retention;
* deduplication;
* event source;
* links to affected records;
* in-app delivery;
* extensible email or webhook adapters;
* permission filtering;
* tests.

Temporary in-memory storage may remain only as an explicitly isolated development implementation.

### Search

Implement and verify:

* OpenSearch indexing;
* index bootstrap;
* mapping management;
* indexing from domain events;
* reindexing;
* retry;
* fallback behavior;
* permission-aware search;
* pagination;
* filtering;
* highlighting where appropriate;
* health checks;
* operational documentation.

Never silently return incomplete or unauthorized search results.

### Attachments

Implement:

* MinIO or S3-compatible storage;
* metadata persistence;
* upload;
* download;
* unlink;
* ownership;
* authorization;
* size limits;
* content-type validation;
* safe filenames;
* quarantine;
* asynchronous malware scanning;
* scan status;
* blocked download until clean;
* audit;
* cleanup;
* tests.

A filename allowlist or stub scanner is not sufficient for production completion.

### Authentication and Authorization

Use secure OIDC Authorization Code Flow with PKCE.

Implement and verify:

* Keycloak integration;
* token validation;
* issuer and audience checks;
* role mapping;
* permission mapping;
* refresh handling;
* logout;
* session expiry;
* backend-enforced permissions;
* object-level permissions;
* field-level permissions where required;
* permission tests;
* prevention of IDOR;
* production-safe CORS;
* production-safe CSRF posture;
* secure headers.

Development authentication must be impossible to activate accidentally in production.

The application must fail fast when insecure development authentication is enabled under a production-like profile.

### Audit and Outbox

Ensure all important mutations produce:

* immutable audit events;
* actor identity;
* timestamp;
* entity type;
* entity ID;
* previous state where appropriate;
* new state where appropriate;
* correlation ID;
* request ID;
* source;
* transactional outbox events.

Verify transactional consistency between domain writes, audit records, and outbox events.

Implement safe retries and idempotent consumers.

### Reports and dashboards

Implement reports based on real backend data:

* workload;
* queue health;
* SLA performance;
* breached items;
* incident trends;
* request trends;
* MTTR;
* backlog;
* aging;
* problem trends;
* change success rate;
* asset and CMDB statistics;
* filtering;
* export;
* permission enforcement.

Do not label mock, synthesized, or estimated data as live data.

### AI Gateway and Copilot

Keep AI functionality isolated behind a policy-controlled gateway.

Implement:

* explicit allowed use cases;
* prompt construction boundaries;
* sensitive-data filtering;
* audit;
* timeout;
* failure handling;
* provider abstraction;
* rate limiting;
* user permission checks;
* human confirmation for consequential actions;
* protection against prompt injection from tickets, attachments, and knowledge articles.

AI must never directly perform privileged actions without backend authorization and explicit policy checks.

## Frontend quality

Build a cohesive enterprise operator interface.

Required qualities:

* Russian as the default locale;
* complete English and German localization for implemented features;
* responsive layouts;
* keyboard navigation;
* accessible focus states;
* semantic controls;
* screen-reader support;
* high-contrast mode;
* dark mode;
* light mode;
* compact and comfortable density options;
* loading states;
* error states;
* empty states;
* optimistic updates only where safe;
* honest integration status;
* no fake success;
* no dead buttons;
* no placeholder screens for completed modules;
* no unexplained technical IDs where human-readable labels exist;
* consistent table, drawer, modal, form, badge, filter, and bulk-action patterns.

Do not optimize merely for attractive screenshots.

The system must remain usable with realistic amounts of data and long content.

Avoid oversized page components. Extract coherent domain hooks, components, API adapters, state management, and reusable UI primitives.

## Backend quality

Use clear modular-monolith boundaries.

Required qualities:

* domain logic outside controllers;
* thin controllers;
* transaction boundaries in application services;
* repositories behind clear abstractions;
* proper validation;
* typed errors;
* consistent API error responses;
* pagination;
* optimistic locking where concurrent updates matter;
* idempotency for retried requests;
* structured logging;
* correlation IDs;
* health and readiness endpoints;
* no hidden fallback to unsafe or incomplete behavior;
* no production dependency on in-memory stores.

Use PostgreSQL as the source of truth.

## Database rules

Use Flyway for every schema change.

Never edit an already-released migration unless repository history proves it has not been shared or executed.

New migrations must:

* be forward-only;
* have clear names;
* preserve existing data;
* add appropriate indexes;
* add foreign keys;
* add uniqueness constraints;
* avoid unsafe nullable transitions;
* include migration tests where possible.

Verify the entire migration chain from an empty PostgreSQL database.

## Integration requirements

The full stack must work with the repository's compose setup:

* PostgreSQL;
* Keycloak;
* RabbitMQ;
* Redis;
* OpenSearch;
* MinIO;
* backend;
* frontend where configured.

Verify:

* startup;
* readiness;
* authentication;
* database migration;
* API access;
* event publication;
* search indexing;
* attachment storage;
* cache behavior;
* failure behavior;
* restart persistence.

## Testing requirements

Do not treat compilation as verification.

Implement and run:

* backend unit tests;
* application service tests;
* repository integration tests;
* Flyway migration tests;
* Spring security tests;
* permission matrix tests;
* Testcontainers tests for PostgreSQL;
* Testcontainers tests for RabbitMQ where applicable;
* OpenSearch integration tests;
* MinIO integration tests;
* frontend unit tests;
* component tests where useful;
* Playwright end-to-end tests;
* accessibility checks;
* regression tests for every fixed defect.

Critical end-to-end scenarios must include:

1. login through OIDC;
2. create an incident;
3. attach a file;
4. scan and download a clean attachment;
5. block an infected attachment;
6. assign the incident;
7. add a comment;
8. transition the incident;
9. verify SLA;
10. verify audit;
11. verify notification;
12. verify search indexing;
13. link a CI;
14. create a problem from related incidents;
15. create and approve a change;
16. inspect related records;
17. restart services and verify persistence.

## Browser verification

You must verify the application yourself in a real browser.

For every important module:

* open it;
* inspect visual layout;
* exercise the main workflow;
* test loading, empty, error, and populated states;
* verify keyboard navigation;
* verify responsive behavior;
* verify all three themes;
* inspect browser console;
* inspect failed network calls;
* verify that live mode uses real APIs;
* verify that no mock-only behavior is presented as production functionality.

Use screenshots as evidence during visual review.

Do not declare UI work complete solely because the build succeeds.

If the interface looks inconsistent, unfinished, overly generic, mock-like, cluttered, inaccessible, or inferior to mature enterprise ITSM products, continue refining it.

## Performance and reliability

Verify:

* reasonable initial bundle size;
* route-level code splitting;
* no excessive rerenders;
* no infinite subscription loops;
* pagination for large datasets;
* indexed database queries;
* no obvious N+1 access patterns;
* bounded queues;
* retry limits;
* timeout handling;
* graceful degraded states;
* memory-safe notification and event handling;
* application restart persistence.

Run basic load and performance checks on critical APIs.

Do not make unsupported performance claims.

## Security review

Before completion, perform a dedicated adversarial review for:

* broken access control;
* IDOR;
* privilege escalation;
* unsafe development profiles;
* injection;
* XSS;
* insecure deserialization;
* SSRF;
* path traversal;
* malicious file upload;
* unrestricted file download;
* prompt injection;
* secret exposure;
* insecure defaults;
* missing rate limits;
* missing audit;
* tenant or user data leakage;
* unsafe automation actions;
* unauthorized bulk operations.

Fix discovered issues and add regression tests.

## Evidence of completion

Maintain a machine-readable completion file:

`.grok/FULL_BUILD_COMPLETE`

Do not create this file until every documented Definition of Done criterion has genuinely passed.

The file must include:

* completion timestamp;
* final commit SHA;
* tested branch;
* test commands executed;
* test results;
* browser scenarios verified;
* integration services verified;
* known limitations;
* security review status;
* production readiness status.

Do not create the file for partial completion.

Do not create it merely because the iteration limit is approaching.

If anything required remains unfinished, continue working and do not mark the build complete.

## Continuous execution loop

At the beginning of every autonomous run:

1. inspect the repository state;
2. read the latest Git history;
3. inspect open TODOs and incomplete Definition of Done items;
4. run relevant tests;
5. identify the highest-risk unfinished requirement;
6. continue implementation from that point;
7. commit coherent milestones;
8. repeat.

Do not redo completed work unless verification reveals a defect.

Do not spend entire runs writing progress reports or visual-review documents without implementing the required fixes.

Documentation must support implementation, not replace it.

## Final completion standard

The task is complete only when:

* the complete application starts successfully;
* all mandatory infrastructure starts;
* all migrations succeed from a clean database;
* authentication works;
* authorization is enforced;
* core ITSM processes work end to end;
* data survives restarts;
* frontend live mode works against the backend;
* critical workflows are covered by automated tests;
* CI is green;
* browser verification is complete;
* no critical or high-severity security findings remain;
* mock implementations are not used as production implementations;
* documentation reflects the actual system;
* `.grok/FULL_BUILD_COMPLETE` contains truthful verification evidence.

Until all of those conditions are met, continue working.
