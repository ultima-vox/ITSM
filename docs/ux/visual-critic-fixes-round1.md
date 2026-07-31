# Visual Critic Round 1 — Fixes Applied

**Date:** 2026-07-30  
**Source:** `docs/ux/visual-critic-round1.md`  
**Frontend:** `G:\ITSM\frontend`  
**Build:** `npm run build` — **PASS** (tsc + vite)

Polish features preserved: CommandPalette, OperatorGrid, density, notification menu, work item detail upgrades, catalog drawer, knowledge reader.

---

## Critical defects

### C1 Typography floor — **DONE**
- `design-system/tokens.css`: raised base scale floor; added semantic roles  
  `--text-table-header` (11px), `--text-row-primary` (13px), `--text-meta` (11px), `--text-chip` (11px)
- `styles/global.css`: `.table-head`, `.data-table th/td`, `.wi-row*`, `.status-chip`, `.chip`, `.field*`, `.priority`, `.muted`, CI meta, map footer use semantic tokens
- Headers ≥11px, row primary ≥12–13px, meta/chips ≥11px

### C2 Accessible tables — **DONE**
- `OperatorGrid.tsx`: `role="table" / row / columnheader / cell / rowgroup`; **no** `aria-hidden` on headers
- `WorkItemRow.tsx` + `OverviewPage.tsx`: row/cell roles; headers not aria-hidden
- Mobile ≤768: card reflow (2-line primary + chips via `.wi-row__meta-line`); **removed** `min-width: 690px` as only mobile story

### C3 i18n completeness — **DONE**
- Header breadcrumb uses `t('header.workspace')` (was hard-coded `"Northstar"`)
- Sidebar `aria-label={t('app.primaryNav')}`
- WorkItemDetail SLA policy/targets via `sla.policyP1|P2|P3`, `sla.responseTarget`, `sla.resolutionTarget`
- Platform-aware shortcut: `header.searchShortcutMac` / `header.searchShortcutWin`
- CMDB map nodes via `cmdb.mapNode*` keys
- SLA abbreviations localized (`sla.tomorrow`, `sla.hours`, `sla.days`)
- Keys synced in `ru.json`, `en.json`, `de.json` (default locale remains `ru`)

### C4 Error states — **DONE**
- New `components/ui/ErrorState.tsx` (title, hint, retry)
- Wired on: Overview (metrics + queue), Queues, My Work, WorkItemDetail, Catalog, Knowledge, CMDB, Assets, Problems, Changes

### C5 Queues depth — **DONE**
- Queue tabs: Unassigned / My group / Escalated / Breached / All (with counts)
- SLA filter select added
- Filters + tab persisted in URL search params (`tab`, `priority`, `type`, `status`, `sla`)
- Bulk assign/priority remains in OperatorGrid
- Urgency: icon + state text + time (not color-only)

### C6 WorkItemDetail workbench — **DONE**
- Assign / Escalate / Resolve handlers update mock state + activity + toasts
- Details tab: 2-pane workbench (form left / activity stream + comment right)
- SLA policy fully i18n; breach/at-risk banners with icon + text
- Comment post uses `t('workItem.commentSent')`
- Editable impact, urgency, service (mock save toast)

### C7 Mobile language switcher — **DONE**
- Removed `.language { display: none }` at ≤768
- Compact locale control stays in header; Escape closes menu

### C8 Color-only urgency — **DONE**
- SLA cells: Clock / Alert / Shield icon + `t('sla.*')` state label + time
- Map health: color dot **and** text labels (`map-footer__label`)
- Breach/at-risk banners use icon + text + color

---

## Secondary modules (M1 craft)

### Assets / Problems / Changes — **DONE**
- Search + status/type/priority filters
- Density toggle
- ErrorState on load failure
- Empty reset action
- Proper `<th scope="col">`
- Type sizes inherit raised operator floor

---

## Related major / polish

| Item | Status |
|------|--------|
| M5 Header workspace from i18n | Done |
| M7 Runtime Overview greeting date | Done (`formatGreetingDate`) |
| M8 Language menu Escape | Done |
| M10 Localized SLA abbreviations | Done |
| M4 Header contrast / size | Done via C1 |
| Avatar sm ≥9px | Done |

---

## Files changed

### New
- `frontend/src/components/ui/ErrorState.tsx`
- `docs/ux/visual-critic-fixes-round1.md`

### Design / styles
- `frontend/src/design-system/tokens.css`
- `frontend/src/styles/global.css`

### i18n
- `frontend/src/i18n/locales/ru.json`
- `frontend/src/i18n/locales/en.json`
- `frontend/src/i18n/locales/de.json`

### Components
- `frontend/src/components/ui/index.ts`
- `frontend/src/components/data-display/OperatorGrid.tsx`
- `frontend/src/components/data-display/WorkItemRow.tsx`
- `frontend/src/components/layout/Header.tsx`
- `frontend/src/components/layout/Sidebar.tsx`

### Pages
- `frontend/src/pages/Overview/OverviewPage.tsx`
- `frontend/src/pages/Queues/QueuesPage.tsx`
- `frontend/src/pages/MyWork/MyWorkPage.tsx`
- `frontend/src/pages/WorkItemDetail/WorkItemDetailPage.tsx`
- `frontend/src/pages/Catalog/CatalogPage.tsx`
- `frontend/src/pages/Knowledge/KnowledgePage.tsx`
- `frontend/src/pages/CMDB/CmdbPage.tsx`
- `frontend/src/pages/Assets/AssetsPage.tsx`
- `frontend/src/pages/Problems/ProblemsPage.tsx`
- `frontend/src/pages/Changes/ChangesPage.tsx`

### Lib
- `frontend/src/lib/format.ts` (`formatGreetingDate`)

---

## Build status

```
✓ tsc --noEmit
✓ vite build
dist/assets/index-*.css ~63 kB
dist/assets/index-*.js  ~393 kB
```

**PASS**
