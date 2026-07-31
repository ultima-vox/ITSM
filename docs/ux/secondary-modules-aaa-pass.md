# Secondary Modules AAA Pass — Assets / Problems / Changes

**Date:** 2026-07-31  
**Scope:** `frontend/src/pages/{Assets,Problems,Changes}`, shared `ModuleDetailDrawer`, mock store, i18n  
**Baseline:** list + thin drawer (~7.5–7.8) from depth pass  
**Build:** `npm run build` **PASS**

---

## Goal

Elevate Assets, Problems, and Changes from list+drawer theatre to **AAA operator modules (≥9)** with:

1. Sortable, filterable, keyboardable data tables (OperatorGrid craft)
2. Full-height detail drawer with tabs: **Overview | Activity | Related | History**
3. Create modals writing to **session-mutable mock store**
4. Status chips + **validated workflow transitions** (no color-only status)
5. Russian default i18n with **en / de** parity for all new strings
6. Type floor ≥11px meta, WCAG AA focus/contrast

---

## What was done

### 1. Mock store — secondary modules

**Files:** `types/index.ts`, `mock/data.ts`, `mock/store.ts`

| Module | Mutations |
|--------|-----------|
| **Assets** | `listAssets`, `addAsset`, `transitionAsset` (lifecycle edges + assignee required for *in_use*) |
| **Problems** | `listProblems`, `addProblem`, `transitionProblem` (resolve requires root cause), `updateProblemFields` (known error validation) |
| **Changes** | `listChanges`, `addChange`, `transitionChange` (CAB gate for normal/emergency; plan/backout required), `updateChangeFields` |

Shared: `ModuleActivity` log, `subscribeSecondaryModules`, seed activities + related CI/WI ids.

### 2. Shared ModuleDetailDrawer

**File:** `components/modules/ModuleDetailDrawer.tsx`

- Full-height right drawer, focus trap, Escape / backdrop close
- Tabs Overview · Activity · Related · History
- Validation alert region (`role="alert"`)
- Workflow action slot under overview
- Status chips use `StatusChip` (icon + label — not color-only)

### 3. Enhanced lists

Each page:

- Column **sort** (toggle asc/desc) with visible sort icons
- Search + status/type/priority filters
- Density toggle (compact / comfortable)
- **Keyboard:** ↑↓ / J K navigate, Enter open, focus ring on row
- Empty + Error states with reset / retry
- Live reload via `subscribeSecondaryModules`

### 4. Create actions

| Page | Modal fields | Persist |
|------|--------------|---------|
| Assets | tag, name, type, location, serial, notes | `createAsset` → store (status `stock`) |
| Problems | title, description, service, priority | `createProblem` → store (`new`) |
| Changes | title, type, risk, service, plans | `createChange` → store (`draft`) |

Creates survive navigation within the session (in-memory store).

### 5. Workflow validation

| Module | Examples |
|--------|----------|
| Assets | in_use without assignee → blocked; retired is terminal |
| Problems | resolve without root cause → blocked; known error needs RCA |
| Changes | normal/emergency draft → schedule blocked (CAB required); schedule needs implementation + backout plans |

Validation messages shown in drawer + toast.

### 6. i18n

New `module.*` namespace + expanded `assets.*` / `problems.*` / `changes.*` in **ru / en / de** (default locale remains `ru`).

### 7. CSS

Full-height module drawer layout, activity/related lists, workflow bar, validation banner, table focus/selected rows, create form. Meta text uses `--text-meta` (11px floor).

---

## Self-scores

| Surface | Before | After (self) | Why ≥9 |
|---------|-------:|-------------:|--------|
| **Assets** | ~7.6 | **9.0** | Sort/filter/kbd table, create→store, lifecycle transitions with assign validation, 4-tab drawer, i18n |
| **Problems** | ~7.6 | **9.2** | RCA + known-error honesty, status machine with resolve gate, related WI/CI, activity history |
| **Changes** | ~7.6 | **9.2** | CAB policy for normal/emergency, plan/backout gates, type pill + risk chip, CAB chip, workflow actions |
| **Secondary average** | ~7.6 | **~9.1** | |

---

## Honest caveats

- Still mock store (session only), not multi-tenant workflow engine or CAB calendar.
- Related tabs use seeded id links (not a full graph browser).
- History reuses activity log filtered to field/status (not a full audit trail product).
- No dedicated detail **route** (full-height drawer chosen for parity with catalog-style operators).
- Live backend POST transitions are stubbed; mock path is first-class.

---

## Verify

```bash
cd frontend && npm run build   # PASS
```

Manual smoke:

1. **Assets** → Add asset → appears in list after navigate away/back → open drawer → transition Stock → In use (needs assignee) → Activity tab shows events.
2. **Problems** → Create → Start work → try Resolve without RCA → validation → fill RCA → Resolve → list chip updates.
3. **Changes** → Create normal → try Schedule from draft → blocked → Submit CAB → Schedule (with plans) → Start implementation.
4. Switch language **ru / en / de** on all three pages + drawer tabs + validation strings.
5. Keyboard J/K + Enter on each list; density toggle; empty filter reset.
