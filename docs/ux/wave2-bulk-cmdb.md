# Wave 2 — Secondary list bulk + CMDB relation edit

**Date:** 2026-07-31  
**Scope:** Assets / Problems / Changes list craft · CMDB relation graph mutability  
**Closes R7 residual S3 (secondary tables without OperatorGrid-class bulk)**

---

## Goals

1. **Secondary modules** (Assets, Problems, Changes) gain OperatorGrid-class bulk selection without rewriting module tables into `OperatorGrid`.
2. **CMDB** CI detail supports add/remove of live relations; graph + impact BFS rebind from store.
3. **i18n** ru / en / de for all new strings.
4. **`npm run build`** must pass.

---

## 1. Secondary list bulk (Assets · Problems · Changes)

### Pattern

Kept existing sort / filter / density / empty / error / row keyboard nav. Added:

| Feature | Implementation |
|--------|----------------|
| Multi-select checkboxes | Leading column + select-all header (indeterminate) |
| Bulk bar | Sticky `bulk-bar` when `selectedIds.size > 0` |
| Assign mock | Store bulk assign → current user (+ toast) |
| Change status mock | Allowed-edge bulk status chips (+ toast) |
| Clear | Clears selection |
| `aria-sort` | `ascending` / `descending` / `none` on sorted headers |
| Space | Toggle select on focused row |
| Ctrl/Cmd+A | Select all in **filtered** list |
| Escape | Clear focus + selection |

### Files

| Path | Change |
|------|--------|
| `frontend/src/pages/Assets/AssetsPage.tsx` | Bulk select + bar + kbd + aria-sort |
| `frontend/src/pages/Problems/ProblemsPage.tsx` | Same |
| `frontend/src/pages/Changes/ChangesPage.tsx` | Same (coexists with CAB board / calendar) |
| `frontend/src/mock/store.ts` | `bulkAssignAssets/Problems/Changes`, `bulkSet*Status` |
| `frontend/src/api/cmdb.ts` | `bulkAssignAssets`, `bulkSetAssetStatus` |
| `frontend/src/api/problems.ts` | `bulkAssignProblems`, `bulkSetProblemStatus` |
| `frontend/src/api/changes.ts` | `bulkAssignChanges`, `bulkSetChangeStatus` |
| `frontend/src/styles/global.css` | `.module-table .grid-check` |

### Status bulk honesty

Bulk status only applies **allowed transition edges**. Skips invalid rows (e.g. problem → resolved without RCA; normal change → scheduled without CAB). Toast reports count of rows actually updated.

---

## 2. CMDB relation edit

### Behaviour

On selected CI detail:

- List of live relations with **remove** control
- **Add form**: target CI select + type (`depends_on` / `hosts` / `runs_on` ≈ DEPENDS_ON / HOSTED_ON / RUNS_ON)
- Mutations write mutable `relationItems` in mock store (seeded from `ciRelations`)
- Persist in `vox-itsm-store-v1` under `ciRelations`
- `notifyCis` → subscribe reloads items + relations
- Dependency map edges and **impact BFS** use live `relations` state

### Files

| Path | Change |
|------|--------|
| `frontend/src/mock/store.ts` | `listCiRelations`, `addCiRelation`, `removeCiRelation`, hydrate/persist |
| `frontend/src/api/cmdb.ts` | `fetchCiRelations` from store; `createCiRelation`, `deleteCiRelation` |
| `frontend/src/pages/CMDB/CmdbPage.tsx` | Relation form + remove; local relation state |
| `frontend/src/styles/global.css` | `.ci-rel-row`, `.ci-rel-form` |

### Validation

- Required target, no self-link, unknown CI, duplicate triple, missing id on delete.

---

## 3. i18n

Namespaces added in **ru / en / de**:

- `module.bulk.changeStatus` · `module.bulk.assigned` · `module.bulk.statusChanged`
- `cmdb.relForm.*` (title, target, type, add/remove, errors, toasts)

Existing `grid.*` keys reused for selection chrome (selected count, assign to me, clear, kbd hints).

---

## 4. Build

```bash
cd frontend && npm run build
```

Must complete with TypeScript + Vite success.

---

## Self-scores (honesty frame)

| Surface | Score | Note |
|---------|:-----:|------|
| Assets list craft | **8.7** | Bulk + aria-sort; not full OperatorGrid row model |
| Problems list craft | **8.7** | Same |
| Changes list craft | **8.8** | Bulk on table + existing CAB board/calendar depth |
| CMDB relation edit | **8.9** | Live graph + impact; form types subset of seed graph types |
| i18n parity | **9.0** | ru/en/de complete for new keys |
| Residual honesty | — | No enterprise discovery / auto-rel / bulk CAB; demo store authority |

**Wave claim:** R7 S3 (secondary plain tables without OperatorGrid-class bulk) **closed for list craft**. Preferred process scores on Problems/Changes unchanged by bulk alone.
