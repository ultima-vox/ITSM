# Wave 7 — Bind mock workflow admin to problem & change drawers

**Date:** 2026-07-31  
**Scope:** `ProblemsPage`, `ChangesPage`, `lib/workflowRuntime.ts`, i18n, CSS  
**Build:** `npm run build` **PASS** (tsc + vite)

---

## Goal

Connect the session-scoped **Workflow admin** (`/admin/workflow`) definitions for `objectKey` **problem** and **change** to module **detail drawers**, mirroring work-item binding: next-state actions from the active definition (required fields, labels, policy gates), with hard-coded fallback when no definition is active.

---

## Behaviour

| Concern | Problem | Change |
|---------|---------|--------|
| **Source** | Active def `objectKey === 'problem'` | Active def `objectKey === 'change'` |
| **Status map** | UI `new/in_progress/waiting/resolved/closed/cancelled` ↔ `NEW/IN_PROGRESS/PENDING/RESOLVED/CLOSED/CANCELLED` | UI `draft/cab_review/scheduled/in_progress/completed/cancelled` ↔ `DRAFT/CAB_REVIEW/SCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED` (also accepts `IMPLEMENTING`/`CLOSED`/`REJECTED` from API enums) |
| **Outgoing edges** | Filter definition transitions where `from` equals mapped current state | Same |
| **Required fields** | e.g. `assignee_id` on start, `root_cause` on resolve — drafts in drawer count via `fieldOverrides` | e.g. `implementation_plan`, `window_start`/`window_end`, `assignee_id` — plan drafts via `fieldOverrides` |
| **Policy gates** | Store still validates resolve root cause | CAB/plan/backout gates via `changeRuntimePolicyBlock` (disable + tooltip); normal draft → schedule blocked |
| **Inactive workflow** | `HARD_CODED_PROBLEM_TRANSITIONS` | `HARD_CODED_CHANGE_TRANSITIONS` (+ same policy gates) |
| **State chip** | Chip next to status: definition label + raw state key | Same |
| **Live toggle** | Subscribe to workflow store; admin changes refresh drawer transitions | Same |

---

## Files

| Path | Change |
|------|--------|
| `frontend/src/lib/workflowRuntime.ts` | Extended — problem/change maps, field presence, policy block, `getProblemRuntimeTransitions` / `getChangeRuntimeTransitions` |
| `frontend/src/pages/Problems/ProblemsPage.tsx` | Workflow chip + runtime action bar (fallback when inactive) |
| `frontend/src/pages/Changes/ChangesPage.tsx` | Workflow chip + runtime action bar (fallback when inactive) |
| `frontend/src/styles/global.css` | `.module-workflow__head` / `__meta` for drawer action header |
| `frontend/src/i18n/locales/{en,ru,de}.json` | `problems.workflow*`, `problems.transition.*`, `problems.fields.*`, `changes.workflow*`, … |
| `docs/ux/wave7-problem-change-workflow.md` | This doc |

---

## Hard-coded fallback matrices

### Problem

```
new         → in_progress, cancelled
in_progress → waiting, resolved, cancelled
waiting     → in_progress, resolved, cancelled
resolved    → closed, in_progress
closed      → ∅
cancelled   → ∅
```

### Change

```
draft       → cab_review, scheduled, cancelled
cab_review  → scheduled, draft, cancelled
scheduled   → in_progress, cancelled
in_progress → completed, cancelled
completed   → ∅
cancelled   → ∅
```

---

## Self-check

- [x] Available next states from active problem / change workflow  
- [x] UI ↔ workflow state mapping (PENDING ↔ waiting; COMPLETED ↔ completed)  
- [x] Illegal / incomplete transitions disabled + tooltip (missing required fields / policy)  
- [x] Fallback when workflow inactive  
- [x] Current workflow state chip from definition labels  
- [x] Drawer plan/RCA drafts feed required-field checks  
- [x] i18n en / ru / de  
- [x] `npm run build` passes  
- [x] This doc  

---

## Out of scope

- Permission checks against current user grants on problem/change drawers (API accepts `permissions` opt; UI does not pass yet)  
- Live backend workflow API  
- Bulk bar driven by workflow definitions  
- Activating/deactivating sibling problem/change versions (single seed version each)  
