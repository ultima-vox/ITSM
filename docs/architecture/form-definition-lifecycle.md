# Form definition lifecycle

Form definitions are tenant-scoped, immutable versions. Admins create drafts through
`POST /api/v1/metadata/forms/drafts`, inspect history, then publish an exact version.
Publication takes a PostgreSQL advisory lock and switches active version atomically.

Draft validation requires active object schema, unique sections and fields, RU/EN section
labels, known attribute keys, bounded layouts, and CEL-only conditional metadata. Runtime
rendering reads active tenant definition with `default` fallback. Every create/publish action
is written to audit trail and transactional outbox. Re-publishing active version is idempotent.

Forms remain presentation metadata. Backend object validation, field authorization, and
workflow rules stay authoritative; clients must never treat visibility or read-only hints as
security controls.
