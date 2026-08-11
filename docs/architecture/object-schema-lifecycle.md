# Object schema lifecycle

Object metadata is tenant-scoped and versioned. Administrators create complete immutable drafts;
the server assigns the next version under a PostgreSQL advisory transaction lock. Draft creation
never changes runtime behavior. Publication atomically deactivates the previous tenant version and
activates the selected draft. Default platform schemas remain fallback-only and are never mutated
by tenant administrators.

Required controls:

- lowercase stable keys and unique attribute/relation keys;
- non-blank Russian and English labels for objects, attributes and relations;
- bounded schema, relation, label and enum sizes;
- relations target an active schema or the schema itself;
- publication cannot remove an existing attribute, change its type, make it newly required, or
  remove published enum values;
- tenant isolation on every read/write and immutable version history;
- draft/publication audit entries and transactional outbox events.

Breaking evolution requires a future explicit data-migration workflow; normal publication fails
closed instead of silently invalidating committed records.
