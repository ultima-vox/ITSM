# Wave 5 — Global Search · Work-item activity diffs · Admin Audit

**Date:** 2026-07-31  
**Scope:** `frontend/src/pages/Search`, `frontend/src/pages/Admin/AuditPage`, `WorkItemDetail` activity, `api/search.ts`, `api/audit.ts`, command palette, sidebar, i18n, CSS  
**Build:** `npm run build` **PASS**

---

## Goal

Ship discoverability and audit surfaces for operators:

| Surface | Target | Focus |
|---------|--------|--------|
| **`/search`** | Full-page search | Query from URL, type chips, results with badge + snippet + open |
| **Work item activity** | Diff depth | Before/after field rows when present on mock activity |
| **`/admin/audit`** | Light admin | Mock audit list, action filter, work-item deep links |

---

## 1. Global Search (`/search`)

**Files:** `pages/Search/SearchPage.tsx`, `api/search.ts`

1. **URL state:** `?q=` drives the query; `?types=work-item,knowledge,…` optional multi-type filter.
2. **API:** reuses `searchAll()` — live `GET /search?q=`; mock indexes work items, knowledge, CIs, assets, problems, changes.
3. **Type chips:** All + work-item / knowledge / ci / asset / problem / change; counts from current result set.
4. **Result row:** type badge + icon, title (link), snippet, relative updated time, **Open** action via `searchHitPath()`.
5. **States:** empty query, loading skeletons, error + retry, no matches with clear action.
6. **Palette:** “Search all for «q»” action; Enter with no selection still goes to `/search?q=`.
7. **Nav:** Sidebar primary + command palette + breadcrumb.

---

## 2. Work-item activity diffs

**Files:** `types` (`WorkItemActivity.before/after`), `mock/data` seed, `mock/store` `pushActivity`, `api/mappers/workItem`, `WorkItemDetailPage`

- Seed + live field/status/assign/escalate/resolve activities may carry `before` / `after` maps.
- Activity tab and side stream render **ActivityDiff** (field label → struck before → after).
- Backend `BackendActivity.before/after` already mapped through.

---

## 3. Admin Audit (`/admin/audit`)

**Files:** `pages/Admin/AuditPage.tsx`, `api/audit.ts`, `mock/data` `auditEvents`

1. Table: time (relative + absolute title), actor, action badge, object type + label, detail.
2. **Action chips** from seed action keys (`create`, `update`, `assign`, …).
3. **Work-item objects** link to `/work-items/:id`.
4. Empty / loading / error; mock-only seed (live would hit `GET /audit`).

---

## Shared

| Piece | Change |
|-------|--------|
| Router | Lazy `/search`, `/admin/audit` |
| Sidebar / palette / crumbs | Search + Audit entries |
| i18n | **en / ru / de** — `search.*`, `audit.*`, `nav.search/audit`, `command.searchAll*`, `workItem.fields.*` |
| CSS | Search hit cards, activity-diff, audit table actors |

---

## Self-scores (honest)

| Surface | After (self) | Notes |
|---------|-------------:|-------|
| **Global search** | **8.0** | Full page + multi-type mock; live path depends on OpenSearch payload |
| **Activity diffs** | **7.8** | Clear before/after when seeded; not full field catalog |
| **Admin audit** | **7.5** | Read-only mock list + filter; no export/paging/backend yet |

**Criteria met**

- [x] `/search` full-page search via `api/search.ts`  
- [x] Object-type chips + result badge/snippet/open  
- [x] Palette “Search all” / Enter → `/search?q=`  
- [x] Empty / loading / error  
- [x] i18n ru/en/de  
- [x] Activity before/after diffs when available  
- [x] `/admin/audit` mock list + action filter + work-item links  
- [x] Sidebar / nav / palette + lazy routes  
- [x] `docs/ux/wave5-search-audit.md`  
