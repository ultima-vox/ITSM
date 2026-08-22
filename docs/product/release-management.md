# Release and deployment management

A release groups the changes that ship together into one deployment window, and carries the
build, test and go/no-go gates that decide whether that window may open. It is the ITIL
release practice as implemented by the `releasemanagement` module.

## Why the module exists

Change management approves a single change. It does not answer "what ships tonight, and is all
of it ready?". Without a release record, that question is answered in a spreadsheet, and a
deployment can start while one of its changes is still awaiting CAB approval.

## Lifecycle

```text
PLANNING → BUILD → TESTING → GO_NO_GO → DEPLOYING → DEPLOYED → CLOSED
                                             ↓          ↓
                                       ROLLED_BACK ← ────
```

`PLANNING`, `BUILD`, `TESTING` and `GO_NO_GO` may also be cancelled. `ROLLED_BACK` returns to
`PLANNING` for a retry, or closes.

### Gates

The gates live in the `Release` aggregate, not in the controller, so no API path can skip them:

| Transition | Gate |
| --- | --- |
| `BUILD → TESTING` | a deployment plan **and** a rollback plan are recorded |
| `TESTING → GO_NO_GO` | a test summary is recorded |
| `GO_NO_GO → DEPLOYING` | the go decision is `GO` |
| `→ DEPLOYING` | every linked change is approved (`APPROVED`, `SCHEDULED`, `IMPLEMENTING`, `REVIEW` or `CLOSED`) |

`DEPLOYING` stamps `actual_start`; `DEPLOYED` and `ROLLED_BACK` stamp `actual_end`. Once a
release reaches `DEPLOYING`, its plans and its content are frozen.

`WorkflowPolicyGateway` still runs on every transition, so an installation can add approval
steps or field conditions on top of the gates — it can never remove them.

## Content

`release_change` links a release to change requests. The release module never queries
`change_request` directly: it reads the public `ChangeCatalogQuery` contract published by
`changemanagement`, which keeps the module boundary that `ModularityTest` verifies.

`GET /api/v1/releases/{id}/changes` returns each linked change with a `deployable` flag plus a
`blocking` count, so the operator screen can show exactly what holds the release back.

## API

| Method | Path | Permission |
| --- | --- | --- |
| `GET` | `/api/v1/releases` | `release.read` |
| `GET` | `/api/v1/releases/{id}` | `release.read` |
| `GET` | `/api/v1/releases/{id}/transitions` | `release.read` |
| `GET` | `/api/v1/releases/{id}/changes` | `release.read` |
| `GET` | `/api/v1/releases/conflicts?start&end` | `release.read` |
| `POST` | `/api/v1/releases` | `release.write` |
| `PATCH` | `/api/v1/releases/{id}` | `release.write` |
| `POST` | `/api/v1/releases/{id}/transitions` | `release.write` |
| `POST` | `/api/v1/releases/{id}/changes` | `release.write` |
| `DELETE` | `/api/v1/releases/{id}/changes/{changeId}` | `release.write` |
| `POST` | `/api/v1/releases/{id}/go-decision` | `release.approve` |

`release.read` is granted to `ADMIN`, `SERVICE_DESK_AGENT`, `SERVICE_DESK_MANAGER` and
`CHANGE_MANAGER`; `release.write` and `release.approve` to `ADMIN` and `CHANGE_MANAGER`.

Writes take `expectedVersion` and answer `409 Conflict` on a stale write, like every other
mutable aggregate in the platform.

## Data

`V78__release_management.sql` adds `release_record` (org-scoped, versioned, `release_number_seq`
producing `REL-000000` numbers), `release_change`, and the three permissions.

## Operator screen

`/releases` lists releases with status, type and window filters. Selecting one opens the detail
view: plans, the linked changes with their blocking state, the go/no-go buttons while the
release is under review, and the transitions the workflow policy currently allows.
