# Bulk lifecycle transition contract

Problem and Change bulk transition APIs accept 1–100 UUIDs and one target state. Authorization is
checked for every record. Each command still uses the same aggregate transition invariants, audit,
and outbox path as a single transition; bulk is not a bypass or direct SQL update.

Response contains aggregate success count plus one result per requested ID. Missing records and
invalid transitions are explicit per-item failures, allowing UI to report partial completion
truthfully. An authorization failure rejects the request instead of leaking which IDs exist.
