# Wave 7 — Overview personalization

**Date:** 2026-07-31  
**Scope:** `frontend/src/pages/Overview/OverviewPage.tsx`, i18n `overview.*`, overview CSS  
**Build:** `npm run build` · **E2E:** `npm run test:e2e` (smoke h1 preserved)

---

## Goal

Make Overview feel operator-owned: hide/show widgets, personal greeting when signed in, queue quick filters, and a denser hero in compact mode — without breaking the smoke contract that an **h1** remains on `/`.

---

## Implementation

### 1. Widget visibility (`localStorage`)

| Key | Default |
|-----|---------|
| `vox-overview-widgets` | all `true` |

Persisted shape:

```json
{ "metrics": true, "queue": true, "copilot": true, "flow": true }
```

- **Customize** control in the headline opens a popover with toggles (metrics / work queue / work flow / assistant).
- Hidden widgets are unmounted; dashboard grid collapses to a single column when only one column is visible.
- Smoke clears `vox-*` keys on init, so defaults always apply in e2e.

### 2. Greeting + auth name

- Unsigned / mock demo: `overview.greeting` (e.g. «Доброе утро, Анна»).
- OIDC signed-in: `overview.greetingNamed` with first token of `user.name`.
- **h1** always present (e2e: `getByRole('heading', { level: 1 })`).

### 3. Queue quick filters

Chips on the work-queue panel (local filter, not navigation):

| Chip | Predicate |
|------|-----------|
| My | `assignee.id === currentUser.id` |
| Unassigned | `!assignee` |
| Breached | `slaState === 'breached'` |

- Exclusive selection; second click on the active chip returns to **all**.
- Counts shown on each chip; empty state CTA clears the filter when one is active.

### 4. Compact hero

When shell density is `compact` (`useDensity` → `isCompact`):

- Headline gets `headline--compact`.
- Subtitle hidden; smaller h1 / tighter eyebrow.
- Customize button icon-only (label via `aria-label`).

### 5. i18n (ru / en / de)

New keys under `overview.*`:

`greetingNamed`, `customize`, `widgetsTitle`, `widgetMetrics`, `widgetQueue`, `widgetFlow`, `widgetCopilot`, `queueFilters`, `filterMy`, `filterUnassigned`, `filterBreached`, `clearQueueFilter`.

---

## Acceptance

- [x] Widget toggles persist in `localStorage` and rehydrate on load  
- [x] Greeting uses auth display name when authenticated  
- [x] Queue chips: my / unassigned / breached  
- [x] Compact density tightens the hero  
- [x] i18n ru/en/de  
- [x] `npm run build` pass  
- [x] E2E smoke: brand + h1 still visible  

---

## Residual

- Greeting time-of-day is still static «good morning» copy (not clock-based).
- «My» filter uses mock `currentUser.id` (same as My Work), not OIDC `sub`, until live assignee mapping is wired.
- Widget order is fixed; no drag-reorder.
}
