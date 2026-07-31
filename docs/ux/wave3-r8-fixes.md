# Wave 3 — R8 residual fixes

**Date:** 2026-07-31  
**Scope:** Close R8 P1/P2 residuals on Changes bulk policy, conflict banner labels, and secondary bulk craft shared chrome.  
**Inputs:** `docs/ux/visual-critic-round8.md` (S3b, S12, S13)

---

## Goals

1. **S13** — `bulkSetChangeStatus` uses the **same policy gates** as single `transitionChange` (plan + backout + CAB for NORMAL schedule).
2. **S12** — Conflict banner shows **human CI names**, never raw `ci-*` ids.
3. **S3b (partial)** — Extract shared **ModuleBulkBar** + **ModuleKbdHint** used by Assets / Problems / Changes (closer OperatorGrid bulk chrome).
4. **i18n** ru / en / de for all new strings.
5. **`npm run build`** must pass.

---

## 1. Changes bulk = same gates as single transition

### Problem (R8 S13)

`bulkSetChangeStatus` only skipped `normal → scheduled` without `cabApproved`. Single `transitionChange` also requires:

- implementation plan (for `scheduled` and `cab_review`)
- backout plan (for `scheduled`)
- not `cabRejected`
- NORMAL cannot `draft → scheduled` without CAB path

Wave2 prose claimed “allowed edges”; critic correctly dinged plan/backout gap.

### Fix

Shared helpers in `frontend/src/mock/store.ts`:

| Helper | Role |
|--------|------|
| `changeTransitionBlockReason(c, next)` | Returns i18n error key or `null` if allowed |
| `changeStatusActivityKey(c, next)` | Same activity keys as single path |
| `applyChangeStatus(c, next)` | Status + cabVotes seed + standard auto-approve |

- `transitionChange` → block reason → apply + activity
- `bulkSetChangeStatus` → **skip** when block reason non-null; apply + activity only on success
- Toast still uses returned `n` = **actually transitioned** count only

### Policy matrix (single = bulk)

| Transition | Gates |
|------------|--------|
| → `scheduled` | plan + backout; not cabRejected; NORMAL requires `cabApproved`; NORMAL `draft → scheduled` blocked (`cabRequired`) |
| → `cab_review` | plan required |
| Other edges | `CHANGE_TRANSITIONS` only |

STANDARD still auto-sets `cabApproved` on schedule (policy pre-approval). EMERGENCY may schedule without CAB approve (with emergency activity key).

---

## 2. Conflict banner human CI names (S12)

### Problem

List line rendered `CI ci-pg-cluster` — raw technical id after R6 Related-tab label work.

### Fix

`ChangesPage` conflict list:

```tsx
t('changes.conflict.cis', {
  names: c.ciIds.map((id) => resolveRelatedLabel(id)).join(', '),
})
```

`resolveRelatedLabel` → CMDB store name (e.g. **PostgreSQL Cluster**), soft fallback only if unknown.

### i18n

- `changes.conflict.cis`: en `CIs: {names}` · ru `КИ: {names}` · de `CIs: {names}`

---

## 3. Secondary bulk → closer OperatorGrid (S3b partial)

### Problem

Assets / Problems / Changes each duplicated sticky bulk bar + kbd hint markup. Not full ModuleGrid/OperatorGrid parity (predicates, sticky virtual head, X-select) — that remains open — but shared chrome closes the “three copy-pasted bars” residual.

### Fix

New shared components:

| File | Export |
|------|--------|
| `frontend/src/components/modules/ModuleBulkBar.tsx` | `ModuleBulkBar`, `ModuleKbdHint` |

**ModuleBulkBar**

- Sticky `.bulk-bar` (existing CSS: `position: sticky` under header)
- Selected count (`grid.selected`)
- Assign to me
- Status chip children slot + `module.bulk.changeStatus` label
- Clear selection
- Renders nothing when `selectedCount === 0`

**ModuleKbdHint**

- ↑↓ / J K navigate · Enter open · Space select · Ctrl+A select all · **Esc clear selection**
- Visible whenever list has rows (same placement as before)

### Wired pages

- `AssetsPage.tsx`
- `ProblemsPage.tsx`
- `ChangesPage.tsx`

Escape already cleared focus + selection in each page’s `onListKeyDown`; kbd strip now **documents** Esc.

**Still open (full S3b):** shared ModuleGrid row model, sticky table head parity, X-select alignment with Queues OperatorGrid.

---

## 4. i18n

| Key | en | ru | de |
|-----|----|----|-----|
| `grid.kbdClear` | clear selection | снять выделение | Auswahl löschen |
| `changes.conflict.cis` | CIs: {names} | КИ: {names} | CIs: {names} |

Existing bulk / grid keys reused (selected, assign, clear, statusChanged, kbd*).

---

## 5. Files touched

| Path | Change |
|------|--------|
| `frontend/src/mock/store.ts` | Shared change transition gates; bulk = single policy |
| `frontend/src/components/modules/ModuleBulkBar.tsx` | **New** shared bulk bar + kbd hint |
| `frontend/src/pages/Changes/ChangesPage.tsx` | ModuleBulkBar; human CI conflict labels |
| `frontend/src/pages/Assets/AssetsPage.tsx` | ModuleBulkBar + ModuleKbdHint |
| `frontend/src/pages/Problems/ProblemsPage.tsx` | ModuleBulkBar + ModuleKbdHint |
| `frontend/src/i18n/locales/{en,ru,de}.json` | `grid.kbdClear`, `changes.conflict.cis` |

---

## 6. Build

```bash
cd frontend && npm run build
```

**Result:** `tsc --noEmit && vite build` — **PASS** (2026-07-31).

---

## Residual after this wave

| ID | Status |
|----|--------|
| **S13** Bulk change plan/backout/CAB | **CLOSED** |
| **S12** Conflict raw CI ids | **CLOSED** |
| **S3b** Shared ModuleGrid / OperatorGrid parity | **Partial** — shared bulk bar + kbd; table still module-local |
| **S6** Metadata crumb | Open |
| **S7** Deep-link module detail routes | Open |
| **S8** Asset free-text assignee | Open |
| **S9** CAB quorum / full calendar | Partial (week + board remain) |
| **S10** KB editorial queue | Open |
| **S11** Notification center depth | Open |

---

## Honesty note

- Bulk schedule can no longer silently promote NORMAL changes that lack plan/backout/CAB approve — matches drawer single transition.
- Shared ModuleBulkBar is **chrome extraction**, not a claim that secondary lists equal Queues OperatorGrid.
- Conflict banner labels are operator-readable; heuristic itself unchanged (same-day NORMAL ∩ related CIs).
