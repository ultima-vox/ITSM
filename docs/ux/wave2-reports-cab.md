# Wave 2 — Reports 8.0+ & Changes CAB calendar

**Date:** 2026-07-31  
**Scope:** `G:\ITSM\frontend` Reports + Changes operator surfaces  
**Build:** `npm run build` **PASS** (`tsc --noEmit && vite build`)

---

## Goal

Lift **Reports** from a static heat snapshot toward a usable ops console (filters, trend, export, resolution KPIs), and add a **light CAB calendar** on Changes without building a full enterprise change-calendar product.

---

## Reports — what shipped

| Requirement | Implementation |
|-------------|----------------|
| Trend mock (7 days open / resolved) | Bar pair sparkline from store `createdAt` / resolved `updatedAt`; light synthetic fill when a day is empty |
| Export CSV | Client-side download of **filtered** work items (`number,title,type,priority,status,assignee,sla…`) |
| SLA heat + resolution rate + MTTR | Existing breach / at-risk / unassigned cards kept; **resolution rate %** = resolved ÷ (resolved + active); **MTTR mock hours** = avg `updatedAt − createdAt` on resolved items |
| Filter type / priority | Select filters recompute all aggregates, bars, trend, urgent list, and CSV scope |
| i18n ru / en / de | `reports.exportCsv`, `filter*`, `trend*`, `resolutionRate*`, `mttr*` |

**Primary files:**  
`frontend/src/pages/Reports/ReportsPage.tsx`  
`frontend/src/styles/global.css` (`.reports-trend*`, `.reports-kpi*`, `.reports-filters*`)  
`frontend/src/i18n/locales/{en,ru,de}.json`

### Honesty notes

- Trend synthetic fill is **visual**, not a backend series — documented in UI hint.
- MTTR is a mock derived from mock timestamps, not an SLA-engine measure.
- CSAT metric still comes from dashboard metrics mock (unchanged).

**Self-score target:** Reports **8.0+** (ops snapshot with action: filter, export, trend, KPIs). Not BI / multi-period compare / scheduled report delivery.

---

## Changes CAB calendar (light) — what shipped

| Requirement | Implementation |
|-------------|----------------|
| Mini calendar week view | 7-day strip with prev / next / this week; chips for draft / CAB / scheduled / in-progress using `plannedStart` (fallback `createdAt + 3d`) |
| CAB board panel | Lists `cab_review` (not yet chair-decided); **Approve / Reject** via existing `setChangeCabDecision` store/API |
| Freeze / conflict banner | Heuristic: **two NORMAL** changes (not completed/cancelled) on the **same local day** with **intersecting `relatedCiIds`** |
| Seed demo data | `CHG-430` (normal, scheduled, 2026-08-02, `ci-pg-cluster`) conflicts with `CHG-422`; `CHG-435` extra CAB queue row |

**Primary files:**  
`frontend/src/pages/Changes/ChangesPage.tsx`  
`frontend/src/mock/data.ts`  
`frontend/src/styles/global.css` (`.changes-calendar*`, `.changes-cab-board*`, `.changes-conflict-banner*`)  
i18n `changes.calendar.*`, `changes.cabBoard.*`, `changes.conflict.*`

### Schedule / CAB policy (unchanged)

NORMAL still requires explicit CAB approve before schedule; emergency skip banner and drawer CAB panel remain. Board actions reuse the same chair APIs as the drawer.

### Honesty notes

- Calendar is **week strip**, not month / drag-reschedule / freeze windows from CMDB.
- Conflict is a **simple CI ∩ day heuristic**, not dependency graph or maintenance calendar.
- No bulk CAB agenda export.

**Self-score:** Changes process depth held / slightly lifted by visible CAB queue + conflict awareness; still not enterprise CAB suite.

---

## Manual smoke

### Reports

1. Open `/reports` — metrics, SLA heat, trend, resolution rate, MTTR.
2. Filter **Type = Incident** — counts and bars shrink; urgent list filters.
3. **Export CSV** — browser downloads `itsm-work-items-YYYY-MM-DD.csv` with filtered rows.
4. Switch locale **ru / en / de** — new labels translate.

### Changes

1. Open `/changes` — conflict banner for CHG-422 ↔ CHG-430 (same day + `ci-pg-cluster`).
2. Week calendar shows chips; navigate **next week** to see Aug windows if current week is prior.
3. CAB board lists CHG-422 / CHG-435 — **Approve** updates status flags and removes from board (or marks approved); toast confirms.
4. Open a change drawer — existing CAB panel still works.

---

## Out of scope (deliberate)

- Server-side report warehouse / real time-series API  
- PDF / scheduled email reports  
- Full CAB meeting minutes, voting quorum rules, freeze windows from CMDB  
- Drag-and-drop reschedule on calendar  
