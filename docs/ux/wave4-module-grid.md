# Wave 4 — ModuleGrid (S3b close)

**Date:** 2026-07-31  
**Scope:** Shared `ModuleGrid` for Assets / Problems / Changes lists  
**Closes residual S3b** (full ModuleGrid / OperatorGrid list craft parity for secondary modules)

---

## Goal

Secondary module lists stop shipping per-page bespoke `<table>` duplication. One reusable grid matches OperatorGrid craft:

| Craft item | Status |
|------------|--------|
| Sticky header | Yes (`table-head--sticky`) |
| Bulk checkboxes + select-all (indeterminate) | Yes |
| Sticky bulk bar | Yes via integrated `ModuleBulkBar` |
| `aria-sort` on sortable columns | Yes |
| Keyboard J/K · ↑↓ navigate | Yes |
| Space select focused row | Yes |
| Enter open | Yes |
| Ctrl/Cmd+A select all (filtered set) | Yes |
| Esc clear focus + selection | Yes |
| Density compact / comfortable | Yes (`useDensity`) |
| Empty / error / loading slots | Yes (defaults + overrides) |
| Column config via props | Yes |

i18n: reuses existing `grid.*` keys (no new strings).

---

## API

**File:** `frontend/src/components/modules/ModuleGrid.tsx`

```ts
interface ModuleGridColumn<T> {
  id: string;
  header: ReactNode;
  sortKey?: string;       // sortable when set + onSort provided
  render: (row: T) => ReactNode;
  width?: string;         // CSS grid track
  className?: string;
}

// Key props
rows, columns, getRowId, getRowLabel, ariaLabel
sortKey, sortDir, onSort          // parent owns data order
selectedIds, onSelectionChange
onRowOpen, activeRowId
emptyTitle / emptyHint / emptyAction*  // or emptySlot
loading / loadingSlot
error / errorSlot / onRetry
onBulkAssign + bulkActions        // ModuleBulkBar chips
showKeyboardHint
```

Bulk status chips stay page-owned and pass through `bulkActions` into `ModuleBulkBar` (assign / clear / change-status unchanged).

---

## Consumers

| Page | Columns | Bulk status chips |
|------|---------|-------------------|
| **Assets** | tag · name · status · location · type · assignee | in_use / stock / repair / retired |
| **Problems** | number · title · status · priority · known error · incidents · assignee · updated | in_progress / waiting / resolved / closed / cancelled |
| **Changes** | number · title · type · status · risk · window · assignee | cab_review / scheduled / in_progress / completed / cancelled |

Changes keeps CAB calendar + board above the grid; only the main list is ModuleGrid.

---

## Files

| Path | Change |
|------|--------|
| `frontend/src/components/modules/ModuleGrid.tsx` | **New** shared grid |
| `frontend/src/components/modules/ModuleBulkBar.tsx` | Unchanged (consumed by ModuleGrid) |
| `frontend/src/pages/Assets/AssetsPage.tsx` | Table → ModuleGrid |
| `frontend/src/pages/Problems/ProblemsPage.tsx` | Table → ModuleGrid |
| `frontend/src/pages/Changes/ChangesPage.tsx` | Table → ModuleGrid |
| `frontend/src/styles/global.css` | `.module-grid*`, sticky panel, density, mobile min-width |

---

## Self-scores (list craft)

Score rubric vs OperatorGrid / Queues list as 9.5 reference. Drawer / workflow depth unchanged this wave.

| Module | List craft | Notes |
|--------|------------|--------|
| **Assets** | **9.2** | Full ModuleGrid craft; simple columns; density + filter chrome solid |
| **Problems** | **9.3** | Richer columns (priority, known-error, incidents); same kbd/bulk class |
| **Changes** | **9.1** | Grid parity achieved; calendar/CAB board are separate surfaces (not grid) |

### Dimensional breakdown (all three)

| Dimension | Score | Note |
|-----------|------:|------|
| Sticky head + scroll panel | 9.5 | Matches OperatorGrid pattern |
| Bulk select + sticky bar | 9.5 | Shared ModuleBulkBar + domain status chips |
| Keyboard ops | 9.0 | Space (not X); Ctrl+A + Esc documented in ModuleKbdHint |
| aria-sort / sortable headers | 9.5 | Per-column sortKey |
| Density | 9.0 | Comfortable / compact via shell density |
| Empty / loading / error | 9.0 | Slots + defaults; page-level hard error still full-page |
| Column configurability | 9.5 | Props-driven widths + render |
| Mobile | 8.5 | Horizontal scroll + min-width (no meta-line collapse like OperatorGrid) |
| Predicate / saved views | 7.0 | Still page filters only — not OperatorGrid predicate chrome |

**S3b status:** **CLOSED** for shared ModuleGrid row model + sticky head + bulk + kbd. Remaining optional polish: mobile meta-line, X-select alignment with Queues (secondary modules intentionally keep Space per ModuleKbdHint).

---

## Build

```bash
cd frontend && npm run build
```

**Result:** TypeScript + Vite **PASS** (2026-07-31).
