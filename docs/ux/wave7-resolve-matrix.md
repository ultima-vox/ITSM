# Wave 7 — Sticky Resolve matrix + requiredPermissions (S27 / S28)

**Date:** 2026-07-31  
**Scope:** `WorkItemDetailPage`, `lib/workflowRuntime.ts`, `mock/rbac.ts`, i18n  
**Residuals closed:** **S27** (requiredPermissions ignored), **S28** (sticky Resolve bypasses matrix)  
**Build:** `npm run build` **PASS** (tsc + vite)  
**E2E:** `npm run test:e2e` **PASS** (6 smoke tests)

---

## Goal

Make WorkItemDetail sticky primary actions **honest to the active work-item workflow matrix and mock RBAC principal**, closing the dual-path ding from visual-critic round 12.

| Residual | Fix |
|----------|-----|
| **S28** | Sticky **Resolve** only renders when an outgoing edge to `RESOLVED` exists; disabled + tooltip when required fields / permissions fail |
| **S27** | Transition `requiredPermissions` gate enablement against the current mock user’s RBAC role grants |
| Actions | **Assign** / **Escalate** use action-level permission stubs from the RBAC catalog |

---

## Behaviour

### Sticky Resolve (S28)

| State | Sticky Resolve |
|-------|----------------|
| Outgoing matrix edge to `RESOLVED` | **Shown** |
| No such edge (e.g. `NEW`, `PENDING` under v1, `RESOLVED`/`CLOSED`) | **Hidden** |
| Edge exists, required fields missing (except `resolution_notes`) | **Disabled** + tooltip listing fields |
| Edge exists, principal lacks `requiredPermissions` | **Disabled** + tooltip listing permission keys |
| Edge enabled | Opens resolve modal (notes collected there) |

Source of truth: `findResolveTransition(getWorkItemRuntimeTransitions(...))` — same edges as the workflow action bar (no hard-coded `disabled={resolved}` bypass).

### requiredPermissions (S27)

| Concern | Implementation |
|---------|----------------|
| Principal | `currentUser.id` → `mock/rbac` directory user → role → permission keys |
| Default demo | `u-anna` → `SERVICE_DESK_AGENT` (has `work-item.transition` / `assign` / `update`; **no** `work-item.close`) |
| Gate | `missingRequiredPermissions(required, principalPermissions)`; `admin.full` short-circuits |
| Runtime field | `WorkItemRuntimeTransition.missingPermissions` + `enabled === false` when any missing |
| Tooltip | `workItem.workflowMissingPermissions` |
| Live reassign | `subscribeRbac` → re-read grants after `/admin/rbac` role change |

### Assign / Escalate

Not workflow edges; action stubs use catalog keys:

| Action | Required permission(s) |
|--------|------------------------|
| Assign to me | `work-item.assign` |
| Escalate | `work-item.update` |

Missing grants → disabled + same permission tooltip. Existing status guards (already assigned / resolved / already escalated) still apply.

---

## Files

| Path | Change |
|------|--------|
| `frontend/src/lib/workflowRuntime.ts` | `missingPermissions`, `missingRequiredPermissions`, `WORK_ITEM_ACTION_PERMISSIONS`, `findResolveTransition`, permissions opt on `getWorkItemRuntimeTransitions` |
| `frontend/src/mock/rbac.ts` | `getUserPermissions`, `principalHasPermission`, `missingPermissionsFor` |
| `frontend/src/pages/WorkItemDetail/WorkItemDetailPage.tsx` | Sticky Resolve matrix bind; Assign/Escalate permission awareness; RBAC subscribe |
| `frontend/src/i18n/locales/{en,ru,de}.json` | `workItem.workflowMissingPermissions` |

---

## Self-check

- [x] Sticky Resolve hidden when matrix has no RESOLVED edge  
- [x] Sticky Resolve disabled + tooltip on missing required fields  
- [x] Transition `requiredPermissions` enforced via mock RBAC role  
- [x] Assign / Escalate permission stubs  
- [x] i18n en / ru / de  
- [x] `npm run build`  
- [x] E2E smoke  
- [x] This doc  

---

## Out of scope

- Live backend permission evaluation / OIDC claims  
- Wiring `/admin/rbac` assign into `AuthContext` product gates (S29)  
- Change / problem sticky chrome (their runtimes already accept `permissions`)  
- Escalation / assign as first-class workflow transition keys  
}
