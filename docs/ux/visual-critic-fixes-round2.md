# Visual Critic Round 2 — Fixes Applied

**Date:** 2026-07-30  
**Source:** `docs/ux/visual-critic-round2.md`  
**Frontend:** `G:\ITSM\frontend`  
**Build:** `npm run build` — **PASS** (tsc + vite)

---

## Must-close blockers

### C1 Typography floor residue — **DONE**

Raised remaining **8–10px** operator-readable type in `styles/global.css` to semantic tokens:

| Role | Floor |
|------|-------|
| Meta / labels / chips / headers | `var(--text-meta)` → **11px** |
| Body / card descriptions | `var(--text-sm)` → **12px** where previously micro body |
| Avatar sm/md initials | **11px** |
| Metric labels/details, panel hints, catalog/category/KB/CMDB/detail `side-dl` / timeline / SLA cards / map footer | **≥11px** |

**Allowed decorative chrome at 10px (pure chrome only):**
- `.brand em`, `.eyebrow`
- `kbd` / search shortcut chrome / command-palette footer kbd / grid keyboard hint kbd

No remaining 8–9px operator content.

---

### C2 Accessible tables — **DONE**

**Overview work queue**
- Headers moved **inside** the same `role="table"` as body rows (`OverviewPage.tsx`).
- Structure: `role="table"` → header `rowgroup` + body `rowgroup`; headers are **not** `aria-hidden`.
- `aria-rowcount` set on the table.

**Mobile AT hole**
- Removed `aria-hidden` from `.wi-row__meta-line` in `WorkItemRow.tsx` and `OperatorGrid.tsx`.
- At ≤768: desktop column cells (`role="cell"` except ticket/check) use `display: none` (leave a11y tree).
- `.wi-row__meta-line` is the mobile source of truth for priority / SLA / updated and remains exposed to AT.
- Non-grid overview rows use single-column card areas (`ticket` / `meta`).

---

### Demo chrome honesty — **DONE**

**Reports**
- Replaced `sidebar__fake` with a real `NavLink` to `/reports`.
- Added `pages/Reports/ReportsPage.tsx` + route in `app/router.tsx`.
- Placeholder metrics from live mock store (active / resolved / breached / CSAT) + SLA jump links into Queues.
- Honest copy: live mock data, charts later — not a dead button.
- i18n: `reports.*` in `ru.json`, `en.json`, `de.json`.

**Copilot**
- Verified functional (not dead chrome): suggestion actions navigate Queues with filters + toast; ask field opens Command Palette; brief uses live SLA counts.

---

### Shared mock mutability — **DONE**

New in-memory store + listeners:

| File | Role |
|------|------|
| `mock/store.ts` | Mutable work items + activities; assign / priority / escalate / resolve / create; `subscribeWorkItems` |
| `hooks/useWorkItemsSync.ts` | Soft-reloads `useAsync` consumers on store notify |
| `api/workItems.ts` | Reads/writes store in mock mode; bulk + detail mutation APIs |
| `hooks/useAsync.ts` | Soft reload keeps prior data (no skeleton flash on store sync) |

**Wired consumers**
- `OperatorGrid` bulk assign/priority → `bulkAssignWorkItems` / `bulkSetPriority` (shared store).
- `WorkItemDetail` Assign / Escalate / Resolve → store mutations; assignee/status/priority/tags reflect store after reload.
- Overview / Queues / My Work / Reports resync lists when store mutates.

List and detail stay consistent after assign/priority/resolve.

---

## Build

```text
npm run build  →  tsc --noEmit && vite build  →  PASS
```

---

## Files touched (summary)

- `src/styles/global.css` — C1 type floor; C2 mobile cell hide; Reports styles
- `src/pages/Overview/OverviewPage.tsx` — table semantics + store sync
- `src/pages/Queues/QueuesPage.tsx` — store sync
- `src/pages/MyWork/MyWorkPage.tsx` — store sync
- `src/pages/WorkItemDetail/WorkItemDetailPage.tsx` — store-backed actions
- `src/pages/Reports/ReportsPage.tsx` — **new**
- `src/components/data-display/OperatorGrid.tsx` — bulk → store; meta-line a11y
- `src/components/data-display/WorkItemRow.tsx` — meta-line a11y
- `src/components/layout/Sidebar.tsx` — Reports real link
- `src/app/router.tsx` — `/reports`
- `src/mock/store.ts` — **new**
- `src/api/workItems.ts` — store-backed API
- `src/hooks/useAsync.ts` — soft reload
- `src/hooks/useWorkItemsSync.ts` — **new**
- `src/i18n/locales/{ru,en,de}.json` — `reports.*`

---

## Residual (not in this round’s must-close)

Per R2 backlog P1/P2, still open for a future AAA push:

- Saved views / column picker / real assignment-group model
- Secondary module drill-down (Assets/Problems/Changes detail)
- Self-host Manrope, contrast CI, density theme
- CMDB map remains illustrative CSS graph

**Round 2 must-close set (C1 residue, C2 full, demo chrome, shared mock mutability, build) is closed.**
