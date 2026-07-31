# Wave 6 — Bind mock workflow admin to work-item runtime transitions

**Date:** 2026-07-31  
**Scope:** `WorkItemDetailPage`, `lib/workflowRuntime.ts`, `mock/workflow.ts`, i18n, CSS  
**Build:** `npm run build` **PASS** (tsc + vite)

---

## Goal

Connect the session-scoped **Workflow admin** (`/admin/workflow`) definitions to **Work item detail** so next-state actions are driven by the active `work-item` workflow (required fields, labels), with a hard-coded fallback when no definition is active.

---

## Behaviour

| Concern | Implementation |
|---------|----------------|
| **Source** | Active definition for `objectKey === 'work-item'` from `mock/workflow.ts` |
| **Status map** | UI `new/in_progress/waiting/resolved/closed/cancelled` ↔ workflow `NEW/IN_PROGRESS/PENDING/RESOLVED/CLOSED/CANCELLED` |
| **Outgoing edges** | Filter definition transitions where `from` equals mapped current state |
| **Required fields** | Missing fields disable the transition; tooltip lists human labels |
| **Resolve notes** | `resolution_notes` alone does not disable Resolve — modal still collects notes |
| **Unsupported targets** | e.g. `TRIAGED` (v2 draft) — shown disabled with unsupported-state tooltip |
| **Inactive workflow** | Fall back to hard-coded matrix (aligned with backend `WorkItem.allowedTargets`) |
| **State chip** | Chip next to status: definition label + raw state key; title shows definition name/version |
| **Live toggle** | Subscribe to workflow store; activating v2 in admin updates detail transitions immediately |

---

## Files

| Path | Change |
|------|--------|
| `frontend/src/lib/workflowRuntime.ts` | **New** — map helpers, hard-coded matrix, `getWorkItemRuntimeTransitions` |
| `frontend/src/mock/workflow.ts` | `getActiveWorkflowDefinition(objectKey)` |
| `frontend/src/pages/WorkItemDetail/WorkItemDetailPage.tsx` | Workflow chip + transition action bar |
| `frontend/src/styles/global.css` | `.chip--workflow`, `.work-item-workflow*` |
| `frontend/src/i18n/locales/{en,ru,de}.json` | `workItem.workflow*`, `workflowState.*`, `transition.*`, `actions.to_*` |

---

## Hard-coded fallback matrix

```
new         → in_progress, cancelled
in_progress → waiting, resolved, cancelled
waiting     → in_progress, resolved, cancelled
resolved    → closed, in_progress
closed      → ∅
cancelled   → ∅
```

---

## Self-check

- [x] Available next states from active work-item workflow  
- [x] UI ↔ workflow state mapping (PENDING ↔ waiting)  
- [x] Illegal / incomplete transitions disabled + tooltip (missing required fields)  
- [x] Fallback when workflow inactive  
- [x] Current workflow state chip from definition labels  
- [x] i18n en / ru / de  
- [x] `npm run build` passes  
- [x] This doc  

---

## Out of scope

- Persisting intermediate states without UI mapping (e.g. full TRIAGED lifecycle)  
- Permission checks against current user grants  
- Change / problem detail bound to their workflow definitions  
- Live backend workflow API  
