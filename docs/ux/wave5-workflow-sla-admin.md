# Wave 5 — Workflow + SLA admin surfaces

**Date:** 2026-07-31  
**Scope:** `frontend/src/pages/Admin/WorkflowPage`, `frontend/src/pages/Admin/SlaPage`, mock stores, nav, i18n, CSS  
**Build:** `npm run build` **PASS**

---

## Goal

Add Naumen-class **metadata depth** admin surfaces for platform engines that already exist on the backend (`WorkflowDefinition`, `SlaPolicy`, `WorkingCalendar`), matching the craft of Metadata + Automation admin:

| Surface | Route | Focus |
|---------|-------|--------|
| **Workflow** | `/admin/workflow` | Definitions (work-item, change, problem), state chips, transition table, active version toggle |
| **SLA** | `/admin/sla` | Policies (response/resolution by priority), editable hour targets, working calendar display |

---

## 1. Workflow admin (`/admin/workflow`)

**Files:** `pages/Admin/WorkflowPage.tsx`, `mock/workflow.ts`

1. **List** mock definitions seeded like backend `WorkflowDefinition`:
   - `work-item` v1 (active) — full lifecycle from V10 SQL seed
   - `work-item` v2 (inactive draft) — adds TRIAGED for version toggle demo
   - `change` v1 — DRAFT → CAB/SCHEDULED → COMPLETED
   - `problem` v1 — NEW → RESOLVED with `root_cause` required
2. **Detail:**
   - State chips (initial state marked)
   - Transition table: key, from→to, required fields, permissions
3. **Session store:** toggle active version (one active per `objectKey`)
4. **Empty / error:** list empty, select empty, zero states/transitions, `ErrorState` + retry
5. **Nav:** Sidebar Management + Command palette; lazy route

---

## 2. SLA admin (`/admin/sla`)

**Files:** `pages/Admin/SlaPage.tsx`, `mock/sla.ts`

1. **List** policies:
   - `work-item.response` / `work-item.resolution` (priority conditions)
   - `change.implementation` (risk conditions)
2. **Edit targets** as **hours** (numbers) in session store; **Save** → success toast
3. **Working calendar** panel: Mon–Fri 09:00–18:00 `Europe/Moscow` (`default-business`)
4. Pause states chips, dirty footer, empty/error states
5. **Nav:** Sidebar + Command palette; lazy route

---

## 3. Design & shared

| Piece | Change |
|-------|--------|
| Layout | Master–detail grid (same pattern as Metadata/Automation) |
| CSS | `workflow-admin-*`, `sla-admin-*` in `styles/global.css` |
| Types | `WorkflowDefinition`, `SlaPolicy`, `WorkingCalendarMock` in `types/index.ts` |
| i18n | `nav.workflow` / `nav.sla` + `workflowAdmin.*` / `slaAdmin.*` in **en / ru / de** |
| Shell | `AppShell` crumb map; `Sidebar` secondaryNav; `CommandPalette` nav items |

---

## Self-scores (honest)

| Surface | Target | After (self) | Notes |
|---------|-------:|-------------:|-------|
| **Workflow admin** | 7.5+ | **7.8** | Full transition matrix + multi-version active toggle; still mock-only, no graph editor |
| **SLA admin** | 7.5+ | **7.7** | Editable hours + calendar panel + toast; no live clock preview / holiday editor |

**Criteria met**

- [x] `/admin/workflow` list + detail (states, transitions, permissions, required fields)  
- [x] Seed shaped like backend `WorkflowDefinition`  
- [x] Session store active version toggle  
- [x] `/admin/sla` list + hour targets + save toast  
- [x] Working calendar Mon–Fri 9–18 Moscow mock  
- [x] Sidebar + command palette under Management  
- [x] i18n ru/en/de  
- [x] Empty/error states, lazy routes  
- [x] `npm run build` passes  
- [x] This doc  

---

## Out of scope (next)

- Live API for workflow/SLA CRUD  
- Visual state-machine graph editor  
- Holiday / multi-calendar editor  
- Linking work-item detail transitions to admin definition viewer  
