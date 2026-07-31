# Wave 3 — Reports ≥8.5 & CMDB → 9.0

**Date:** 2026-07-31  
**Scope:** `G:\ITSM\frontend` Reports + CMDB operator surfaces  
**Build:** `npm run build` **PASS** (`tsc --noEmit && vite build`, 2026-07-31)

---

## Goal

Push **Reports** from an ops snapshot (wave 2 ~8.0) toward a more honest, printable console (≥8.5), and close residual CMDB depth gaps so the module sits at **~9.0** (full relation vocabulary, inline type edit, focus + export + accessible health legend).

---

## Reports — what shipped

| Requirement | Implementation |
|-------------|----------------|
| Honest trend fill | Prefer **store-only** day counts when any day in the 7-day window has real open/resolve events. Synthetic fill only when the entire week is empty — banner + `*` tag + hatched bars. |
| Assignee load | **Top 5** assignees by ticket count on the filtered set; horizontal bars. |
| SLA compliance % (7d mock) | Prefer activity history (`kind=sla`, `sla_met` / `sla_breached`) in last 7 local days; else **current `slaState` snapshot** (met/on-track vs breached) with explicit source label. |
| Print-friendly CSS | Print button + `@media print` rules for `.page--reports` (hide shell/chrome, show title stamp, keep chart colors). |
| Dark theme chart contrast | Theme overrides for bar tracks, fills, KPI chips, trend columns, synthetic banner (dark + high-contrast). |

**Primary files:**  
`frontend/src/pages/Reports/ReportsPage.tsx`  
`frontend/src/styles/global.css` (`.reports-*` print + dark)  
`frontend/src/i18n/locales/{en,ru,de}.json` (`reports.*`)

### Honesty notes

- Synthetic trend is **never mixed** with real store days.
- SLA compliance is a **mock**: history seed is sparse; snapshot path is labeled in the KPI subtitle.
- MTTR / CSAT remain mock-derived as in wave 2.

**Self-score target:** Reports **8.6** (honest series + load + compliance + print). Not BI warehouse / scheduled delivery / multi-period compare.

---

## CMDB — what shipped

| Requirement | Implementation |
|-------------|----------------|
| Full relation type set | `depends_on`, `hosted_on`, `runs_on`, `connects_to`, `uses` (matches mock graph + form). Legacy `hosts` normalized to `hosted_on`. |
| Edit relation type | Inline **Edit type** (or double-click row) → select; `updateCiRelation` in store/API. Re-add still available. |
| Double-click graph node | Selects CI and **scrolls detail** into view; list row also supports double-click focus. |
| Export CI list CSV | Filtered list (or full store) → `itsm-cmdb-cis-YYYY-MM-DD.csv`. |
| Health legend text | Footer legend: operational / degraded·maintenance / retired with **text + swatch** (not color-only); dark-theme label contrast. |

**Primary files:**  
`frontend/src/pages/CMDB/CmdbPage.tsx`  
`frontend/src/types/index.ts` (`hosted_on`)  
`frontend/src/mock/store.ts` (`updateCiRelation`, type normalize)  
`frontend/src/api/cmdb.ts` (`updateCiRelation`)  
`frontend/src/styles/global.css` (legend, rel edit)  
i18n `cmdb.export*`, `cmdb.relForm.edit*`, `cmdb.rel.hosted_on`, health keys

### Honesty notes

- Graph layout is still seed + orphan slots (not force-directed).
- Impact remains 1–2 hop BFS on live edges (selection-aware).
- Live API PATCH for relations is best-effort; mock path is the demo source of truth.

**Self-score:** CMDB **9.0** — full relation vocabulary, mutable graph edges with type edit, focus path, export, accessible legend. Not 9.5: no discovery/reconciliation, no multi-map, fixed layout.

---

## Manual smoke

### Reports

1. Open `/reports` — metrics, store-only trend (or synthetic banner if empty week), SLA compliance KPI with source line.
2. Confirm **Assignee load** top 5 bars.
3. **Print** — browser print preview shows report panels without sidebar/header.
4. Switch theme to **Dark** — bar tracks and columns remain readable.
5. Locales **ru / en / de** — new labels translate.

### CMDB

1. Open `/cmdb` — health legend shows three text entries.
2. Select a CI → **Edit type** on a relation → type updates; graph edge remains.
3. Add relation with type `uses` or `connects_to`.
4. **Double-click** graph node → detail scrolls into focus.
5. **Export CI CSV** — download with filtered/all rows.

---

## Out of scope (deliberate)

- Server-side report warehouse / real time-series API  
- PDF / scheduled email reports  
- Force-directed multi-map CMDB / discovery sync  
- CAB / freeze windows driven by CMDB maintenance calendar  

---

## Self-scores (honest)

| Surface | Prior (wave 2 / AAA) | Self after wave 3 | Δ | Notes |
|---------|---------------------:|------------------:|--:|-------|
| **Reports** | ~8.0 | **8.6** | +0.6 | Honesty + assignee load + compliance + print/dark |
| **CMDB** | ~8.7–9.0 | **9.0** | +0.1–0.3 | Full rel set, type edit, focus, CSV, text legend |
