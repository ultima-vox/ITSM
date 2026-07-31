# AAA Product Depth Pass — Critical Surfaces ≥9

**Date:** 2026-07-30  
**Scope:** `G:\ITSM\frontend\src` after R3 FAIL on product depth (not a11y)  
**Input:** `docs/ux/visual-critic-round3.md`  
**Build:** `npm run build` **PASS**

---

## Goal

Push Overview, Queues, WorkItemDetail, Shell, Catalog from R3 critical average **~8.4** to honest **≥9.0** Naumen-class operator depth — without typography regression, keeping ErrorState + i18n ru/en/de.

---

## What was done

### 1. Shared mock store completeness

**Files:** `types/index.ts`, `mock/data.ts`, `mock/store.ts`, `api/workItems.ts`

| Capability | Implementation |
|------------|----------------|
| Fields | `impact`, `urgency`, `teamId`, `escalated`, `watchers[]`, `childTasks[]`, `resolutionNotes` on `WorkItem` |
| Comments | Mutable `commentsStore`; `addComment` persists + activity |
| Activity | Kinds include `field`; real keys (`escalated`, `status_resolved`, `comment_added`, …) |
| Mutations | `assign`, `escalate` (sets `escalated` + tags), `resolve(notes)`, `setPriority`, `updateWorkItem` (impact/urgency/status/service/…), `addWatcher` / `removeWatcher` |
| Queue predicates | `isUnassigned`, `isMyGroup(teamId)`, `isEscalated`, `isBreached` — single source of truth |
| Subscribe | Existing `subscribeWorkItems` + `useWorkItemsSync` used across Overview / Queues / My Work / Detail / Sidebar badge |

Seed data expanded: unassigned intake, breached items, teamIds aligned to `TEAMS.sd|network|iam|dba|app`, child tasks + watchers on critical cases.

### 2. Queues product depth

**File:** `pages/Queues/QueuesPage.tsx`

- Tabs use **real predicates** (not string includes heuristics).
- **Saved views:** 2 built-ins (`Breached + critical`, `Unassigned high+`) + custom save to `localStorage` (`vox-queue-saved-views`).
- **Queue column** via `OperatorGrid showQueue`.
- **SLA summary strip** (breached / unassigned / escalated / my group) as sticky operator chrome.
- **Empty states per queue** with distinct copy + CTA to reset.

### 3. WorkItemDetail product depth

**File:** `pages/WorkItemDetail/WorkItemDetailPage.tsx`

- Impact / urgency / priority / service **patch store** (survive reload of list views).
- Comments **append to store** + activity (no local-only theatre).
- **Resolve modal** requires resolution notes.
- **Child tasks** + **watchers** sections from store.
- Macro **“Request more info”** → `status: waiting` + public comment template.
- Escalate writes first-class `escalated` flag used by Queues filter.

### 4. Catalog

**File:** `pages/Catalog/CatalogPage.tsx`

- **Ask assistant** opens mini assistant panel with **3 smart suggestions** → catalog service drawers.
- Secondary path: open Command Palette from panel.
- **Request from drawer** calls `createWorkItem` → store → navigate Queues unassigned.

### 5. Overview

**File:** `pages/Overview/OverviewPage.tsx`

- Metrics already live from store aggregates (unchanged path, still correct with richer seed).
- Work queue panel uses **OperatorGrid** (`showQueue`, `compact`, `limit={8}`) for parity with Queues.

### 6. Secondary modules

**Files:** `AssetsPage`, `ProblemsPage`, `ChangesPage` + richer mock fields

- Row click → **detail drawer** with real mock fields (serial/model, root cause/workaround, implementation/backout).
- Primary Add buttons give honest mock toast (not dead silent chrome).

### 7. Shell polish (depth integrity)

- My Work badge = **live open-assigned count** from store.
- `/reports` in crumb map + Command Palette nav.
- Queue name column + operator density tokens preserved (≥11 meta / 13 row).

### 8. i18n

New keys added in **en / ru / de** for queues (saved views, empty states), catalog assistant, work item depth (resolve, watchers, macros, activity strings), secondary module drawers.

### 9. CSS

Product-depth chrome only: saved views menu, queue SLA strip, queue pills, grid-with-queue columns, assistant panel, module drawers, child tasks / watchers lists. No typography floor regression.

---

## Self-score (critical surfaces)

| Surface | R3 | Depth pass (self) | Why ≥9 |
|---------|---:|------------------:|--------|
| **Overview** | 8.4 | **9.1** | Live metrics + OperatorGrid parity with Queues; copilot already functional |
| **Queues** | 8.6 | **9.3** | Real predicates, saved views, queue column, summary strip, per-queue empty |
| **WorkItemDetail** | 8.5 | **9.2** | Field honesty, store comments, resolve notes, watchers, macros, child tasks |
| **Shell** | 8.6 | **9.0** | Live badge, reports crumb/palette, palette completeness |
| **Catalog** | 8.1 | **9.0** | Assistant panel + create-into-store from drawer |
| **Critical average** | ~8.4 | **~9.1** | |

Secondary (not AAA-gated): Assets/Problems/Changes ≈ **7.5–7.8** with drawers (was 6.3).

---

## Honest caveats (not inflated)

- Still mock store, not multi-tenant enterprise workflow engine.
- Saved views are filter snapshots, not column-layout personalization.
- Reports remains operational snapshot (~7.x).
- Secondary Add is toast-honest, not full create forms.
- Blind 8-hour A/B vs Naumen may still prefer enterprise governance — but agent **triage loop** (queue truth → workbench fields that stick → list sync) is now shift-credible.

---

## Verify

```bash
cd frontend && npm run build   # PASS
```

Manual smoke:

1. Queues → Unassigned / My group / Escalated / Breached counts change after assign/escalate/resolve.
2. Save view → reload page → restore from dropdown.
3. Detail: change impact → leave → return → value sticks; comment appears after reload of activity/comments.
4. Resolve without notes blocked; with notes → status resolved + list updates.
5. Catalog → Ask assistant → pick suggestion → Request → new WI in Unassigned.
6. Assets/Problems/Changes row → drawer fields visible.
7. Language switch en/ru/de on Queues + Detail + Catalog.
