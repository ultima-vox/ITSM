# Visual Critic Round 5 — Fixes Applied

**Date:** 2026-07-31  
**Source:** `docs/ux/visual-critic-round5.md`  
**Frontend:** `G:\ITSM\frontend`  
**Build:** `npm run build` — **PASS** (`tsc --noEmit` + `vite build`)

---

## Verdict target

Clear R5 secondary AAA floor blockers:

| Gate | Before (R5 critic) | After this fix |
|------|-------------------:|----------------|
| CMDB ≥ 8.5 | **8.4 FAIL** | Honesty + selection impact + new nodes on graph → target **≥ 8.5 / ~9** |
| Related raw IDs | **FAIL** | Human labels via `resolveRelatedLabel` |
| Preferred ≥ 9 on one secondary | **0/5** | Problems (+ Changes) craft raised (activity icons/timestamps, transition hierarchy, empty related CTA, history chronology) |
| Knowledge 7px | Already closed | **Verified** — no `font-size: 7px`; `.article-score small` uses `var(--text-meta)` |

---

## P0 fixes

### 1. CMDB honesty — `pages/CMDB/CmdbPage.tsx`

**Vanity stats removed / recomputed from live mock store**

| Stat | Before | After |
|------|--------|-------|
| Total CIs | Live count + fake `+2.4%` | Live count + “live count” caption |
| Health / freshness | Hardcoded **97.8%** | **Operational %** = `operational / total` from current CI list |
| Relations | Live relation length | Unchanged (honest) |

Stats recompute when CIs are added (subscribe + local list) so create updates the strip live.

**Impact analysis is selection-aware**

- Removed sole dependency on seeded PG-upgrade `fetchCiImpact` for the operator path.
- BFS **1–2 hop** over live `ciRelations` from the **selected CI**.
- Strip title: `Impact scenario · {name}`.
- Drawer title: `Scenario for selected CI · {name}`.
- Honest note: blast radius from selected CI / live relations (not a fixed demo change).
- Empty neighbors → empty state with orphan/unlinked hint.
- Estimated users derived from CI criticality (transparent heuristic, not vanity 1,842 theatre independent of selection).

**New CIs appear on the graph**

- Seed layout retained for known topology nodes.
- Created CIs get free-space positions via `buildGraphLayout` / `ORPHAN_SLOTS`.
- Orphan nodes render with **dim / dashed** style (`is-orphan`) and “New · not linked” subtitle.
- List remains source of truth; map includes all CIs in session store.

**Deep-link**

- `/cmdb?ci={id}` selects that CI (used by Related tab links).

### 2. Related tabs human labels — Assets / Problems / Changes

**Helper:** `src/lib/resolveRelated.ts`

- `resolveRelatedLabel(id)` → human string from mock store:
  - Work items: `INC-1842 · {title}`
  - CIs: `Northstar Portal` (name)
  - Assets / problems / changes when applicable
- `resolveRelatedHref(id)` → router path (`/work-items/…`, `/cmdb?ci=…`, etc.)

**Wired in**

- `pages/Assets/AssetsPage.tsx`
- `pages/Problems/ProblemsPage.tsx`
- `pages/Changes/ChangesPage.tsx`

**Drawer list**

- `ModuleDetailDrawer` Related tab uses human labels (not mono raw ids).
- Internal routes use `react-router` `Link` (not bare dump to `/cmdb`).
- Empty related: `EmptyState` + CTA (`relatedEmptyAction` / `relatedEmptyHint`).

Raw ids such as `ci-portal` / `wi-1842` no longer appear as Related labels.

### 3. Problems / Changes craft → preferred ~9 band

Shared drawer polish in `components/modules/ModuleDetailDrawer.tsx`:

| Item | Change |
|------|--------|
| Activity icons | Kind icons (status / field / system / comment) |
| Timestamps | Relative on Activity; absolute `formatDateTime` on History (`title` + `dateTime`) |
| Actor | Actor name on each entry |
| History order | **Chronological** oldest → newest with timeline chrome |
| Activity order | Newest first |
| Empty related CTA | Module-level empty action (Problems → queues; Assets/Changes → CMDB) |

**Problems-specific**

- Transition **hierarchy**: primary stack (Start work / Resolve) vs secondary/danger (Waiting / Close / Cancel).
- Ranked action order.
- Related incident count uses human phrase (`{n} related incidents`).
- Seeded activities enriched for thin problems (`pr-76`, `pr-61`).

**Changes-specific**

- Same primary/secondary workflow stack + ranked transitions.
- Related labels resolved; empty related CTA to CMDB.

### 4. Knowledge residual 7px — verified closed

- No `font-size: 7px` in `src/`.
- `.article-score small { font-size: var(--text-meta) }` (≥11px) remains.
- No further Knowledge type-floor work required this round.

---

## Files touched

| Path | Role |
|------|------|
| `frontend/src/pages/CMDB/CmdbPage.tsx` | Live stats, selection impact, orphan graph nodes, `?ci=` |
| `frontend/src/lib/resolveRelated.ts` | **New** — label/href resolution |
| `frontend/src/pages/Assets/AssetsPage.tsx` | Related labels + empty CTA |
| `frontend/src/pages/Problems/ProblemsPage.tsx` | Related labels, workflow hierarchy, empty CTA |
| `frontend/src/pages/Changes/ChangesPage.tsx` | Related labels, workflow hierarchy, empty CTA |
| `frontend/src/components/modules/ModuleDetailDrawer.tsx` | Activity/history craft, related Link, empty CTA |
| `frontend/src/mock/store.ts` | Richer problem activity seeds |
| `frontend/src/styles/global.css` | Workflow stack, activity icons/timeline, orphan nodes, impact note |
| `frontend/src/i18n/locales/{en,ru,de}.json` | CMDB honesty + module related strings |

---

## Build

```text
cd frontend
npm run build
# tsc --noEmit && vite build → PASS
```

---

## Honest residual (not claimed fixed)

- Secondary tables still not full OperatorGrid (no bulk / `aria-sort`) — R5 S3 P1.
- CAB remains a status transition, not a calendar/vote product — R5 S4.
- Knowledge helpful vote still toast-level — R5 S5.
- Mock is session-memory only.

These do not re-open the R5 **P0** floor blockers addressed above.

---

## Expected score movement (critic, not self-marketing)

| Surface | R5 critic | Expected after fix |
|---------|----------:|-------------------:|
| CMDB | 8.4 | **≥ 8.5**, likely **~8.8–9.0** if honesty holds on review |
| Assets | 8.5 | **~8.6–8.7** (related labels) |
| Problems | 8.6 | **~9.0** craft path (preferred bar) |
| Changes | 8.6 | **~8.8–9.0** |
| Knowledge | 8.5 | Hold (7px already closed) |

**Suite secondary AAA floor (≥8.5 all):** intended **PASS** after critic re-run.  
**Preferred ≥9 count:** intended **≥1** (Problems primary path).
