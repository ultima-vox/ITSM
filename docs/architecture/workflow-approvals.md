# Workflow parallel approvals

Transitions may declare approval metadata:

```json
{"approval":{"mode":"QUORUM","voterRoles":["CHANGE_MANAGER"],"quorum":2}}
```

Supported deterministic modes:

- `ANY`: first approval passes; request rejects only when every voter rejects;
- `ALL`: every snapshotted voter must approve; first rejection rejects;
- `QUORUM`: configured approvals pass; request rejects when pending votes can no longer reach quorum.

Request creation snapshots directly assigned tenant principals in declared roles and
excludes requester for separation of duties. Later directory changes cannot alter that
vote set. Each voter has one immutable decision. Rejected requests remain history and
a new request creates next attempt; concurrent duplicate requests converge on one open
attempt.

Transition execution requires approved request for exact workflow instance ID,
definition version, transition key, and optimistic source instance version. Successful
transition consumes approval in same transaction; it cannot authorize another state
change. Requests and votes are tenant-scoped, audited, and written to transactional
outbox. APIs and Workflow admin UI support request, status, vote, and history.
