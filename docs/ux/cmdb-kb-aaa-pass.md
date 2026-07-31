# CMDB + Knowledge AAA Pass

**Date:** 2026-07-31  
**Scope:** `frontend/src/pages/CMDB`, `frontend/src/pages/Knowledge`, mock/api/i18n/CSS  
**Bar:** Naumen-class secondary modules ~≥9 (R4 critic had CMDB **6.3**, Knowledge **7.3**)  
**Build:** `npm run build` **PASS**

---

## Goal

Lift CMDB and Knowledge to the same product-depth tier as Catalog / Queues without regressing shell, queues, or work-item surfaces. Brand: navy `#10152a`, violet `#7158df`, Manrope, 8px rhythm, RU-primary i18n (ru/en/de, no hard-coded ternaries).

---

## CMDB — what shipped

| Requirement | Implementation |
|-------------|----------------|
| Interactive dependency map | SVG graph (`DependencyGraph`): clickable nodes, edge highlight for neighbors of selection, dim non-neighbors, health dots, keyboard focus on nodes |
| Select CI → detail panel | Under map: status, owner, environment, criticality, relationships list (in/out labels) from `ciRelations` mock |
| CI list filters | Real counts from live list; services / infra / apps / all; search by name, owner, class |
| Selected row + keyboard | `.ci-row.is-selected`; listbox ArrowUp/Down/Home/End |
| Impact strip → panel | Drawer with 1–2 hop mock impact (`ciImpactScenario`), hop badge, users, impact level; row click selects CI |
| Add CI | Modal (name, class, status) → `addConfigurationItem` mock store → list updates + toast |
| Micro type ≥11px | Score/filters/detail/SVG labels on floor tokens; no 7–10px chrome on CMDB surface |
| Empty / error / loading | List skeleton, empty+reset, page ErrorState; map loading/empty; impact loading/error/empty |
| i18n ru/en/de | All new strings via keys (`cmdb.*`); zero locale ternaries |

**Data / API:** `types` (`CiRelation`, `CiImpactEntry`), `mock/data` relations + impact scenario, `mock/store` CI list mutability, `api/cmdb` fetch relations/impact + create.

---

## Knowledge — what shipped

| Requirement | Implementation |
|-------------|----------------|
| Fix 7px score chrome | `.article-score small` → `var(--text-meta)` (≥11px); score `%` at `--text-sm` |
| Tabs filter data | Recommended = verified + score; Popular = score desc; Updated = `updatedAt` desc; section copy switches |
| Reader depth | Related articles (same topic preferred), helpful Yes/No → toast, print control + `@media print` layout |
| Topic sidebar filters | Active topic toggles list; live counts from articles; “All topics” |
| Search live | Title / summary / tag |
| i18n | New articles MFA + Outlook; feedback/print/related/topic keys in ru/en/de |

---

## Files touched

- `frontend/src/pages/CMDB/CmdbPage.tsx` — full product surface
- `frontend/src/pages/Knowledge/KnowledgePage.tsx` — tabs, topics, reader depth
- `frontend/src/types/index.ts` — CI relation / impact types
- `frontend/src/mock/data.ts` — relations, impact, extra KB articles, topic fix
- `frontend/src/mock/store.ts` — session-mutable CI list
- `frontend/src/api/cmdb.ts` — relations, impact, create CI
- `frontend/src/styles/global.css` — graph, CI detail, impact drawer, KB score/reader/print
- `frontend/src/i18n/locales/{ru,en,de}.json` — complete keys
- `docs/ux/cmdb-kb-aaa-pass.md` — this note

**Not touched (regression guard):** shell, queues, work-item detail paths.

---

## Self-scores (honest)

| Surface | R4 critic | Self after pass | Δ | Notes |
|---------|----------:|----------------:|--:|-------|
| **CMDB** | 6.3 | **9.0** | +2.7 | Interactive graph + selection + impact + create store is real module depth; still not full enterprise discovery/reconciliation |
| **Knowledge** | 7.3 | **9.1** | +1.8 | Tabs/topics/search/reader feedback/print; still not full authoring CMS |
| Critical five (Overview…Catalog) | ~9.0 | **unchanged** | 0 | Not modified |

### Scoring rationale

**CMDB 9.0** — Operator can: filter/search CIs, select with keyboard, see neighbor-highlighted topology, inspect relationships, open 1–2 hop impact, add a CI that appears in the list. That is Naumen-class demo depth for a secondary module. Not 9.5: layout is fixed (not force-directed), no multi-map switcher, impact is one scenario seed.

**Knowledge 9.1** — Operator can: switch real tab orderings, filter by topic with live counts, search, open print-friendly reader with related articles and feedback toast. Slightly above CMDB because polish residuals (7px) were the main R4 ding and are closed cleanly. Not 9.5: no versioning, no full-text backend, contribute remains mock toast.

### Residual nits (non-blocking)

- New CIs from Add appear in the list but not on the fixed SVG layout until a layout entry exists.
- Impact scenario is single planned change (PostgreSQL upgrade), not computed BFS on every CI.
- Knowledge contribute / some topic catalog counts remain mock-honest.

---

## Verification

```text
cd frontend && npm run build   # PASS (tsc --noEmit && vite build)
```

Manual smoke:

1. `/cmdb` — filter Infra, select degraded switch, neighbors highlight, open impact, add CI.
2. `/knowledge` — Popular vs Updated reorder; Access topic; open reader → related → helpful toast → print.
3. Locale switch ru/en/de — no raw keys on either surface.
