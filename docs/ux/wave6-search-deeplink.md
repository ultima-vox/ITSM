# Wave 6 — Search entity deep-links (R11 S7 / S20)

**Date:** 2026-07-31  
**Scope:** `resolveRelatedHref`, `searchHitPath`, Search Open, Knowledge / Assets / Problems / Changes / CMDB URL params  
**Build:** `npm run build` · **E2E:** `npm run test:e2e`

---

## Goal

Close residual **S20** (search non–work-item hits lacked entity deep-link) and reduce **S7** pain: **Open** from global search must land on the entity, not the module root.

| Type | Deep-link | Honored by |
|------|-----------|------------|
| Work item | `/work-items/:id` | WorkItemDetailPage |
| CI | `/cmdb?ci=:id` | CmdbPage (existing) |
| Asset | `/assets?id=:id` | AssetsPage → detail drawer |
| Problem | `/problems?id=:id` | ProblemsPage → detail drawer |
| Change | `/changes?id=:id` | ChangesPage → detail drawer |
| Knowledge | `/knowledge?article=:id` | KnowledgePage → article reader |

---

## Implementation

### 1. `lib/resolveRelated.ts`

- `resolveRelatedHref(id)` returns entity URLs with query ids for problem / change / asset / knowledge (was module root only).
- Prefix fallbacks: `wi-`, `ci-`, `pr-`, `ch-`, `as-`, `kb-`.
- Label + kind support for knowledge (`kb-*`).

### 2. `api/search.ts` — `searchHitPath`

- Prefers `resolveRelatedHref(hit.id)`.
- Type-based fallback with the same query shapes for live API ids outside mock store.

### 3. Search page

- Result title link + **Open** use `resolveRelatedHref(hit.id) ?? searchHitPath(hit)`.
- Command palette keeps `searchHitPath` (now deep-link aware).

### 4. Module pages

| Page | Param | Behavior |
|------|-------|----------|
| Knowledge | `?article=` | Open ArticleReader; clear param on close |
| Assets | `?id=` | Open ModuleDetailDrawer; clear on close |
| Problems | `?id=` | Same |
| Changes | `?id=` | Same |
| CMDB | `?ci=` | Unchanged (selection deep-link) |

---

## Acceptance

- [x] CI search Open → `/cmdb?ci=…` selects CI  
- [x] Asset / problem / change Open → module with drawer for that id  
- [x] Knowledge Open → reader for that article  
- [x] Related drawer hrefs use the same resolver (entity paths)  
- [x] `npm run build` + `npm run test:e2e` pass  

---

## Residual

- Full path routes (`/problems/:id`) not introduced; query + drawer is the supported deep-link surface.
- Live search still depends on backend hit `id` / `objectType` fidelity.
}
