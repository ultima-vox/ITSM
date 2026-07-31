# Vox ITSM — Visual / Operator UX Critic Round 1

**Date:** 2026-07-30  
**Scope:** `G:\ITSM\frontend\src` (pages, components, design tokens, global CSS, i18n)  
**Bar:** Triple-A operator workspace vs modern **Naumen ITSM / ServiceNow / Jira Service Management**  
**Viewports mentally reviewed:** 1440 · 1024 · 768 · 320  
**AAA gate:** ≥9 on all critical operator surfaces (Overview, Queues, WorkItemDetail, Shell, Catalog) **and** no critical a11y/usability defects.

---

## Verdict: **FAIL**

This is a **polished marketing prototype / design-system demo**, not a Naumen-class operator workspace. The shell, violet/navy palette, and Overview/Catalog chrome look intentional. Critical agent work surfaces (Queues, Work item detail, list density, SLA urgency, real tables, error/degraded states) are shallow, typographically fragile, and inconsistent in craft depth.

**Does not meet AAA.** Several critical a11y/usability defects alone would block a PASS even if visuals were stronger.

---

## Blind A/B winner

**Winner: ServiceNow / Naumen / JSM operator UI.**

If unlabeled screenshots of Vox Overview + Queues + Work Item Detail were placed next to a current Naumen SD agent desk or ServiceNow Workspace:

| Dimension | Winner | Why |
|-----------|--------|-----|
| Premium polish (chrome, marketing surfaces) | **Vox ties / slight edge on Catalog hero** | Soft surfaces, gradient copilot, catalog hero are on-trend SaaS |
| Operator density & scan speed | **Naumen / SN / JSM** | Vox type is toy-small; lists lack columns, bulk, saved views |
| Hierarchy & information architecture | **Naumen / SN / JSM** | Vox secondary modules are empty shells |
| SLA urgency | **Naumen / SN / JSM** | Vox SLA is a colored string; no timeline/bar/breach choreography |
| Consistency across modules | **Naumen / SN / JSM** | Vox Overview ≠ Assets/Problems/Changes craft level |
| Russian typography at operator sizes | **Naumen / SN-class** | 8–10px Manrope for RU body/headers is not enterprise-grade |
| Demo vs product feel | **Naumen / SN / JSM** | Fake Reports nav, decorative copilot, hardcoded EN SLA, CSS “dependency map” |

**Honest summary:** Vox would win a Dribbble shot of “ITSM dashboard.” It would lose an 8-hour service-desk shift.

---

## Score table

| Surface | Score (0–10) | Critical for AAA? | Notes |
|---------|-------------:|:-----------------:|-------|
| **Overview** | **6.5** | Yes | Best composed page; decorative copilot; micro type; metrics error = blank |
| **MyWork** | **5.5** | No | Reuses work-item row; tabs ok; no SLA summary strip / capacity |
| **Queues** | **5.0** | Yes | Single fake “all queues” tab; 3 filters; no bulk/saved views/SLA chrome |
| **Catalog** | **6.5** | Yes | Strongest consumer surface; hardcoded `⌘ K`; cards don’t deep-link to real forms |
| **Knowledge** | **5.5** | No | Card stack only; no article reader; tabs don’t change data |
| **CMDB** | **5.0** | No | Hardcoded stats; toy map with EN labels; list is fine-ish |
| **Assets** | **4.0** | No | Generic `data-table` shell; no detail, filters, or density craft |
| **Problems** | **4.0** | No | Same bare table pattern |
| **Changes** | **4.0** | No | Same bare table pattern; window cell will overflow |
| **Settings** | **5.5** | No | Decent cards; density toggle is local-only; toast is inline not system |
| **WorkItemDetail** | **5.5** | Yes | Structure present; actions non-functional; hardcoded EN SLA policy |
| **Shell (sidebar/header)** | **6.5** | Yes | Solid bones; i18n nits; language switcher gone ≤768; fake Reports |

**AAA requires ≥9 on Overview, Queues, WorkItemDetail, Shell, Catalog → all fail.**

**Average (all surfaces):** ~5.3  
**Average (critical only):** ~6.0  

---

## Critical defects (must fix)

### C1. Operator typography is below enterprise floor (scan failure + a11y)

**Evidence**

- `.table-head` / `.data-table th`: **8px** uppercase (`global.css`)
- `.wi-row` body: **10–11px**; SLA/meta **9–10px**
- Category/service meta **8–9px**; CMDB CI meta **8px**; map footer **8px**
- Form labels **10px**; field errors **9px**

**Why critical**  
Russian operator UIs need legible cyrillic at 100% zoom for 8–10h shifts. 8px headers fail WCAG text size practice and look like a Figma export left at 0.75 scale. Naumen/SN body for queues is typically **12–13px** with **11–12px** meta, not 8–10.

**Fix**

- File: `frontend/src/styles/global.css`
- Raise floor: table headers ≥11px (or 10px + stronger weight/contrast, not 8); row primary text ≥12–13px; meta ≥11px; chips ≥11px.
- File: `frontend/src/design-system/tokens.css` — add semantic type roles (`--text-table-header`, `--text-row-primary`, `--text-meta`) and stop hardcoding 8–10px ad hoc.

### C2. Work-item lists are not accessible data tables

**Evidence**

- `MyWorkPage.tsx`, `QueuesPage.tsx`, `OverviewPage.tsx`: CSS grid “table” with  
  `className="table-head" aria-hidden` + `WorkItemRow` as `<Link class="wi-row">`.
- Headers are hidden from AT; rows are links without column association.
- At ≤768: `.table-head, .wi-row { min-width: 690px }` forces horizontal scroll without sticky first column or card reflow at 320.

**Why critical**  
Queues/My Work are the #1 agent surfaces. Screen readers get an unlabeled list of links. Keyboard users get endless tab stops without column context. 320px is unusable for triage.

**Fix**

- Prefer real `<table>` + `<th scope="col">` for Queues/MyWork (Assets already uses `data-table` — unify).
- Or keep virtualized list but expose `role="table"` / `role="row"` / `role="columnheader"` correctly and **remove `aria-hidden` from headers**.
- At ≤768: card layout or 2-line primary + chips; do not ship 690px min-width as the only mobile story.
- Files: `WorkItemRow.tsx`, `QueuesPage.tsx`, `MyWorkPage.tsx`, `OverviewPage.tsx`, `global.css` (`.table-head`, `.wi-row`).

### C3. Hard-coded English / non-i18n strings on critical paths

**Evidence**

| Location | String |
|----------|--------|
| `Header.tsx` | `"Northstar"` breadcrumb (not `t()` / not workspace i18n) |
| `WorkItemDetailPage.tsx` | `"P1 / Critical Response"`, `"15m"`, `"4h"` |
| `CatalogPage.tsx` / `KnowledgePage.tsx` | `<kbd>⌘ K</kbd>` hard-coded (header uses `t('header.searchShortcut')`) |
| `CmdbPage.tsx` map | `"Northstar Portal"`, `"prod-api-01"`, `"PostgreSQL"`, `"AMS-01"` |
| `Sidebar.tsx` | `aria-label="Primary"` English |
| Brand | `"vox"` / `"ITSM"` hard-coded (acceptable as brand if intentional; document) |

Primary locale is RU (`DEFAULT_LOCALE = 'ru'`). EN leakage on WorkItemDetail SLA and header crumb fails product language policy.

**Fix**

- Move all user-visible strings to `i18n/locales/{ru,en,de}.json`.
- SLA policy name from API/mock, not JSX literal.
- Reuse `t('header.searchShortcut')` on Catalog/Knowledge; platform-aware shortcut (Ctrl vs ⌘) if claimed functional.

### C4. No error / degraded UI despite `useAsync` exposing `error`

**Evidence**

- `hooks/useAsync.ts` returns `{ error, reload }`.
- Pages destructure only `{ data, loading }` (Overview, Queues, Catalog, WorkItemDetail, etc.).
- Overview metrics: `metrics.data ? … : null` → **empty metric strip on failure**, not error.

**Why critical**  
Quality gates (`docs/ux/quality-gates.md`) explicitly require loading/empty/**error/degraded**. Operators cannot recover without refresh guesswork.

**Fix**

- Shared `<ErrorState title={t('app.error')} onRetry={reload} />`.
- Wire every `useAsync` consumer: Overview metrics + queue, Queues, WorkItemDetail, Catalog, CMDB, etc.

### C5. Queues is not an operator queue (depth)

**Evidence — `QueuesPage.tsx`**

- One tab: `queues.allQueues` with static filters only (priority/type/status).
- No multi-queue list, assignment filters, SLA state filter, bulk select, sort, column picker, saved views, or “my group” vs “unassigned”.
- Density toggle exists but default dense is good; still no urgency rail.

Compared to Naumen/SN: agents live in multi-queue boards with SLA clocks and bulk assign. This page is a filtered list mock.

**Fix (product + UI)**

- Queue switcher (tabs or left rail): Unassigned / My group / Escalated / Breached.
- Columns: Status chip, SLA remaining with state color + optional mini bar, Queue name, Updated.
- Bulk checkbox + sticky action bar (Assign, Priority, Status).
- Persist filters in URL query.
- Files: `QueuesPage.tsx`, `WorkItemRow.tsx`, `global.css`, mock/API as needed.

### C6. WorkItemDetail lacks operator workbench depth + fake primary actions

**Evidence — `WorkItemDetailPage.tsx`**

- Assign / Escalate / Resolve buttons have **no handlers** (dead chrome).
- Comment “send” clears local state and shows `settings.saved` — wrong copy, no persistence.
- Details tab is description + tags only; no form fields (impact, urgency, category, assignment group).
- SLA tab hardcodes English policy; side panel SLA is better but still thin.
- Related tab ok-ish; no linked knowledge, no child tasks.
- At 1024 layout stacks; action bar becomes full-width buttons — acceptable, but three equal primary-looking actions compete (Resolve should dominate).

**Fix**

- Implement or disable actions honestly (`disabled` + tooltip “скоро” is better than fake).
- Activity stream visible by default in a 2-pane workbench (form left / activity right) — SN pattern.
- Real SLA block: policy (i18n), response/resolve targets from data, progress, breach banner.
- Comment compose: post to API mock, success via `t('workItem.commentSent')` not settings string.
- File: `WorkItemDetailPage.tsx`, `global.css` (`.detail-*`, `.sla-*`).

### C7. Language switcher removed on tablet/phone (≤768)

**Evidence — `global.css`**

```css
@media (max-width: 768px) {
  .language { display: none; }
}
```

Settings still has locale, but header switcher is the always-visible control. Hiding it violates “primary RU, switcher to EN/DE” for mobile operators.

**Fix**

- Keep compact locale control in header at 768/320, or put locale in mobile menu/sidebar profile.
- Do not `display: none` without alternative.

### C8. Color-only urgency risks

**Evidence**

- `.wi-row__sla.is-urgent` / `.is-urgent` → color `#df5364` only.
- Priority is icon+text (good); SLA often time string only with red on risk.
- Map footer health: color dots only (`is-ok` / `is-warn`).

**Fix**

- SLA: prefix icon (clock/alert) + text state `t('sla.at_risk')` or badge, not color alone.
- Ensure contrast of rose on white meets AA for the chosen size (after raising type size).

---

## Major defects

### M1. Inconsistent craft: “hero modules” vs “table stubs”

Overview, Catalog, Knowledge, CMDB have custom layouts, gradients, cards.  
Assets / Problems / Changes are near-identical bare `data-table` pages with a primary button that does nothing.

**Blind A/B impact:** product feels unfinished past the demo path.

**Fix:** Shared list-page template (toolbar, filters, density, empty, error) before more marketing chrome. Files under `pages/Assets|Problems|Changes`.

### M2. Fake / decorative chrome that reads as product

| Element | Issue |
|---------|--------|
| Sidebar Reports (`sidebar__fake`) | Looks like nav, `opacity: 0.72`, not a link, no explanation |
| Overview Copilot | Suggestions/input non-functional decoration |
| `⌘ K` | Branded shortcut; no global command palette / keybinding |
| CMDB dependency map | Absolute CSS boxes + rotated 1px lines — demo toy |
| CMDB stats | Hardcoded `14 286`, `97.8%`, `36 104` |
| Knowledge tabs | recommended/popular/updated do not change query |
| Settings density | Local state only; does not affect Queues/MyWork |

**Fix:** Either wire features or mark clearly as “preview”; remove fake nav or route to empty Reports with real empty state.

### M3. 8px rhythm is claimed, not enforced

Tokens define 4/8/12/16… but CSS freely uses 5, 7, 9, 11, 14, 15, 17, 19, 23, 27, 31, 38, 69px.

**Fix:** Prefer token spacing for padding/gap; document intentional exceptions (e.g. 38px control height).

### M4. Table header contrast + uppercase tracking

`#9aa2b2` at 8px bold uppercase on `#fafbfc` is weak; letter-spacing cannot fix size.

**Fix:** Darker ink (`ink-400`/`ink-500`), larger size (see C1).

### M5. Shell breadcrumb / workspace naming

- Header crumb hardcodes English workspace name `"Northstar"`.
- Sidebar uses `workspaceName` from mock — good source, wrong header.

**Fix:** `Header.tsx` should use same i18n/mock workspace name as Sidebar.

### M6. Sidebar badge hard-coded `badge: 3`

Not from live count; will desync from My Work.

### M7. Overview greeting / date frozen in locale files

`overview.eyebrow`: `"30 июля · среда"` — static demo date, not runtime.

**Fix:** format from `Date` + locale.

### M8. Focus & interaction polish gaps

- Global `:focus { outline: none }` relies entirely on `:focus-visible` — OK if complete; verify custom controls (`.chip--toggle`, `.ci-row`, `.service-card`, language menu items).
- Language menu: mousedown outside closes; no Escape handler in Header (Modal has Escape).
- Create popover: Escape handled on Overview only; good pattern — replicate for language menu.

### M9. Header search is page-local shell state, not a real global search

Works as filter on some pages; Catalog/Knowledge ignore header search and use local inputs. Dual search models confuse operators.

### M10. WorkItemRow SLA display

`slaLabel` special-cases `'tomorrow' → '24h'`, `'1d'`, `'2d'` — English-ish abbreviations mixed with `t('sla.met')`. Not localized fully for RU (“24 ч”, “1 д”).

### M11. Priority / status chips small and low-contrast on dense rows

Priority text-only color + 12px icon at 10px type — ok conceptually, weak at current scale.

### M12. Responsive: language hidden; search shrinks to 130px at 430px

Placeholder Russian strings will clip; no icon-only search expansion pattern.

### M13. No dark mode / high-contrast operator theme

Not required for AAA gate here, but SN-class ops desks often offer high-contrast. Note as backlog, not critical.

### M14. External Google Fonts in production path

`global.css` + `index.html` load Manrope from Google — enterprise offline/air-gapped risk; FOUT. Self-host.

### M15. Knowledge article cards are not links/buttons

`<article class="article-card">` with no navigation — dead ends.

---

## Minor / nits

1. Decorative ✦ in Overview greeting — fine; keep `aria-hidden` (done).
2. `btn--sm` at 10px — raise with type scale.
3. Avatar `font-size: 7px` (`avatar--sm`) — illegible initials.
4. `.sidebar__label` 9px uppercase — ok for section label if contrast improved.
5. Copilot suggestion buttons 9px — decorative; if kept, 11px.
6. Success toast in Settings is not a portal toast; fine for v1.
7. `status.cab_review` chip exists — good foresight.
8. Skip link present — good; keep.
9. `prefers-reduced-motion` present — good.
10. Create modal: validation + focus trap + audit note — one of the more complete flows.
11. Empty states exist on most lists — good pattern; standardize illustration size.
12. Reports fake item uses `<span>` not `<button disabled>` — not focusable (maybe intentional); still confusing visually.
13. Detail related list uses `StatusChip` for CI status — good reuse.
14. `content-max: 1540px` — fine for 1440; at ultra-wide ok.
15. Sidebar sticky full height with scroll — good; ensure focus order when open overlay on mobile.
16. Overlay z-index: `z-header - 1` while sidebar is `z-header` — verify overlay sits under sidebar but above content (ok).
17. Hardcoded filter counts in CMDB (`14286` etc.) diverge from filtered list length.
18. Changes `window-cell` `white-space: nowrap` will force wide tables — expected but needs scroll affordance chrome.
19. No page-level `<h1>` consistency: Catalog uses hero h1; good. Ensure one h1 per page (generally yes).
20. `index.html` title/description Russian-only — OK for default; optional locale title later.

---

## Viewport notes

| Viewport | Assessment |
|----------|------------|
| **1440** | Overview grid + right rail works; type still undersized; best impression of product. |
| **1024** | Dashboard stacks; right-rail becomes 2-col then 1-col at 768; catalog/detail single column — acceptable. Sidebar stays 220px — eats width. |
| **768** | Sidebar drawer OK; **language hidden (C7)**; crumbs hidden; tables force horizontal scroll (C2); detail actions stretch. |
| **320** | Metrics 1-col; search 130px unusable for RU placeholder; tables still 690px min — **fail for agent triage**. Catalog cards stack OK. |

---

## i18n assessment

| Check | Result |
|-------|--------|
| Primary locale RU | **Pass** (`DEFAULT_LOCALE = 'ru'`, `lang` on `<html>`) |
| EN / DE catalogs | **Present** (`en.json`, `de.json`) |
| Switcher | **Header + Settings**; **missing ≤768** |
| Hard-coded UI strings | **Fail** (C3) |
| Runtime date greeting | **Fail** (static locale string) |
| Mock business data language | Mixed RU mock titles (good for RU demo); CMDB map EN |

---

## Design system notes (honest)

**Strengths**

- Token file exists (`tokens.css` / `tokens.ts`): brand navy/violet, semantic colors, spacing scale, radii, motion, z-index.
- Shared UI primitives: Button, Input, Select, Modal, Tabs, EmptyState, Skeleton, Toggle.
- Status/priority components centralize mapping.
- Skip link, focus-visible, reduced motion, modal focus trap.

**Weaknesses**

- Most “design” is one giant `global.css` (~3.4k lines) of page-specific classes — not a componentized DS.
- Type scale under-uses mid sizes; jumps to microscopic utility sizes.
- Shadows/radii pleasant but slightly “consumer SaaS” vs dense operator (not fatal).
- No documented component states matrix (hover/focus/disabled/loading/error) enforced in CI.

---

## Ordered fix backlog

Prioritized for maximum AAA delta. Each item: **path + what to change**.

### P0 — block PASS

1. **Typography floor for operator lists**  
   - `frontend/src/styles/global.css` — `.table-head`, `.data-table th/td`, `.wi-row*`, `.status-chip`, `.priority`, `.metric-card__*`, `.field`  
   - `frontend/src/design-system/tokens.css` — semantic type roles  
   - Target: headers ≥11px, primary row ≥12–13px, meta ≥11px; re-check RU samples.

2. **Accessible work-item tables**  
   - `frontend/src/components/data-display/WorkItemRow.tsx`  
   - `frontend/src/pages/Queues/QueuesPage.tsx`  
   - `frontend/src/pages/MyWork/MyWorkPage.tsx`  
   - `frontend/src/pages/Overview/OverviewPage.tsx`  
   - Remove `aria-hidden` headers; real table semantics; mobile card reflow in `global.css`.

3. **i18n hard-string purge**  
   - `frontend/src/components/layout/Header.tsx` — workspace from mock/`t()`  
   - `frontend/src/pages/WorkItemDetail/WorkItemDetailPage.tsx` — SLA policy/targets  
   - `frontend/src/pages/Catalog/CatalogPage.tsx`, `Knowledge/KnowledgePage.tsx` — shortcut kbd  
   - `frontend/src/pages/CMDB/CmdbPage.tsx` — map node labels  
   - `frontend/src/components/layout/Sidebar.tsx` — `aria-label={t(...)}`  
   - Sync keys in `i18n/locales/ru.json`, `en.json`, `de.json`.

4. **Error/degraded states**  
   - New: `frontend/src/components/ui/ErrorState.tsx` (or extend EmptyState)  
   - Wire `error`/`reload` from `useAsync` on all pages under `frontend/src/pages/**`.

5. **Language switcher at ≤768**  
   - `frontend/src/styles/global.css` — remove `.language { display: none }`  
   - Or relocate control into `Sidebar.tsx` profile area for mobile.

6. **SLA urgency not color-only**  
   - `WorkItemRow.tsx` + `global.css` — icon + state label/badge.

### P1 — critical surfaces to ≥9 trajectory

7. **Queues operator depth**  
   - `QueuesPage.tsx` — multi-queue tabs, SLA filter, sort, bulk bar, URL state  
   - Enhance row: status column, queue name, richer SLA cell.

8. **WorkItemDetail workbench**  
   - `WorkItemDetailPage.tsx` — wire or disable actions; fix comment success copy; SLA from data; default activity visibility; assignment fields  
   - `global.css` — breach banner, action hierarchy (primary Resolve only).

9. **Unify list modules**  
   - `AssetsPage.tsx`, `ProblemsPage.tsx`, `ChangesPage.tsx` — filters, row click → detail, loading/error/empty parity with Queues.

10. **Kill or wire demo chrome**  
    - Sidebar Reports: real route or remove  
    - Overview copilot: hide until functional or mark “preview”  
    - `⌘ K`: implement command palette **or** remove kbd affordances  
    - CMDB map: replace with simple related-CI list until real graph.

11. **Localize SLA abbreviations**  
    - `WorkItemRow.tsx` `slaLabel` + locale strings.

12. **Runtime Overview greeting date**  
    - `OverviewPage.tsx` + `lib/format.ts` — format weekday/date by locale; remove static `overview.eyebrow` content dependency.

### P2 — polish toward AAA consistency

13. Enforce spacing tokens (replace odd px in `global.css` gradually).  
14. Self-host Manrope (`index.html` / `global.css`).  
15. Global density preference (context from Settings → Queues/MyWork).  
16. Language menu Escape + focus return (`Header.tsx`).  
17. Knowledge article navigation (`KnowledgePage.tsx`).  
18. Header search model: either global results page or clearly scoped filter label.  
19. Sidebar badge from real My Work count.  
20. Avatar min font ≥9–10px; chip type ≥11px.  
21. Sticky first column or better scroll shadows on wide tables.  
22. Visual regression + contrast checks per `docs/ux/quality-gates.md` in CI.

---

## What is already good (credit where due)

- Coherent brand (navy shell + violet accent) that could become premium with type/density fixes.  
- Russian-first i18n architecture with three catalogs and `document.documentElement.lang`.  
- Create-work-item modal is relatively complete (validation, a11y dialog, success state).  
- Loading skeletons and empty states are present on main lists.  
- Skip link, focus-visible, reduced-motion, modal focus trap.  
- Status/priority componentization is the right pattern.  
- Catalog/Knowledge visual hierarchy shows design ambition beyond bootstrap defaults.

These are **foundations**, not AAA completion.

---

## Final call

| Gate | Result |
|------|--------|
| Critical surfaces ≥9 | **FAIL** (best critical ≈6.5) |
| No critical a11y/usability defects | **FAIL** (C1–C8) |
| Blind A/B vs Naumen/SN/JSM | **They win** |
| **AAA verdict** | **FAIL** |

**Next critic round should re-score only after P0 + Queues + WorkItemDetail workbench land.** Cosmetic gradient work without C1–C6 will not change the verdict.
