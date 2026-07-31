# Vox ITSM — Visual / Operator UX Critic Round 2

**Date:** 2026-07-30  
**Scope:** `G:\ITSM\frontend\src` (live code, not Round 1 memory)  
**Inputs:** `docs/ux/visual-critic-round1.md`, `docs/ux/visual-critic-fixes-round1.md`  
**Bar:** Triple-A operator workspace vs modern **Naumen ITSM / ServiceNow / Jira Service Management**  
**Viewports mentally reviewed:** 1440 · 1024 · 768 · 320  
**AAA gate:** ≥9 on Overview, Queues, WorkItemDetail, Shell, Catalog **and** no open critical a11y/usability defects.

---

## Verdict: **FAIL**

Round 1 was a marketing prototype with broken operator fundamentals. Round 2 is a **real step toward an operator workspace** — typography floor on list surfaces, ErrorState, queue tabs + URL filters, OperatorGrid a11y/bulk/sort, WorkItemDetail workbench actions, Command Palette, mobile language, SLA not color-only. Credit is due.

It is **still not AAA**. Critical surfaces land in the **mid–high 7s**, not ≥9. Depth remains mock-workbench (toasts, local state, decorative copilot, fake Reports, CSS dependency map). An 8-hour service-desk shift would still prefer Naumen / ServiceNow Workspace / JSM queues.

**Does not meet AAA.** Partial C1/C2 residue + product-depth gap block PASS even after honest credit for C3–C8.

---

## Blind A/B winner

**Winner: ServiceNow / Naumen / JSM operator UI** (still).

| Dimension | Winner | Why |
|-----------|--------|-----|
| Premium polish (chrome, marketing) | **Vox slight edge / tie** | Catalog hero, shell, gradients still on-trend |
| Operator density & scan speed | **Naumen / SN / JSM** | Type floor fixed on rows; metrics/catalog/KB still micro; no saved views / true multi-queue |
| Hierarchy & IA consistency | **Naumen / SN / JSM** | Assets/Problems/Changes improved but still list shells without detail |
| SLA urgency choreography | **Naumen / SN / JSM** | Icon + state + time is solid; no timeline SLA clock / breach workflow product depth |
| Consistency across modules | **Naumen / SN / JSM** | Operator path (Queues/MyWork/Detail) now coherent; secondary modules lag |
| Russian typography at operator sizes | **Much closer; SN-class still wins** | List headers/rows ≥11–13px; residual 8–10px on cards/metrics/side chrome |
| Demo vs product feel | **Naumen / SN / JSM** | Command palette is real; copilot / Reports / map / bulk still demo |

**Honest summary:** Vox would no longer lose on “illegible toy table + dead buttons.” It would still lose a blind operator A/B on **shift-ready depth**.

---

## Score table

| Surface | R1 | R2 | Δ | Critical for AAA? | Notes |
|---------|---:|---:|--:|:-----------------:|-------|
| **Overview** | 6.5 | **7.5** | +1.0 | Yes | ErrorState, runtime greeting date, SLA cells improved; copilot still fake; Overview “table” a11y incomplete; metric labels still ~9–10px |
| **MyWork** | 5.5 | **7.5** | +2.0 | No | Shares OperatorGrid (sort, bulk, density, roles) — large craft jump |
| **Queues** | 5.0 | **7.8** | +2.8 | Yes | Unassigned / My group / Escalated / Breached / All + SLA filter + URL state + bulk bar — biggest R1→R2 win; still not saved views / real multi-queue board |
| **Catalog** | 6.5 | **7.3** | +0.8 | Yes | ErrorState, service drawer → create path; card type still micro (8–10px); assistant chrome decorative |
| **Knowledge** | 5.5 | **6.8** | +1.3 | No | Article reader dialog is real; tabs/topics still shallow |
| **CMDB** | 5.0 | **6.0** | +1.0 | No | Map nodes + footer labels i18n’d; map still CSS toy; stats feel static |
| **Assets** | 4.0 | **6.0** | +2.0 | No | Search/filters/density/ErrorState/`scope=col`; no detail, dead Add |
| **Problems** | 4.0 | **6.0** | +2.0 | No | Same list-template uplift |
| **Changes** | 4.0 | **6.0** | +2.0 | No | Same list-template uplift |
| **Settings** | 5.5 | **6.2** | +0.7 | No | Density context now product-wide via hook (good); still light admin surface |
| **WorkItemDetail** | 5.5 | **7.8** | +2.3 | Yes | Assign/Escalate/Resolve + activity + 2-pane workbench + i18n SLA policy/targets/banners — real workbench skeleton; mock-only persistence |
| **Shell (sidebar/header)** | 6.5 | **7.8** | +1.3 | Yes | Workspace i18n, platform ⌘/Ctrl, language ≤768, CommandPalette wired; fake Reports remains |

**AAA requires ≥9 on Overview, Queues, WorkItemDetail, Shell, Catalog → all fail (best critical = 7.8).**

| Aggregate | R1 | R2 | Δ |
|-----------|---:|---:|--:|
| Average (all surfaces) | ~5.3 | **~6.9** | **+1.6** |
| Average (critical only) | ~6.0 | **~7.6** | **+1.6** |

**How much improved:** ~**+1.6** average points product-wide; Queues **+2.8**, WorkItemDetail **+2.3**, MyWork/secondary lists **+2.0**. Trajectory is correct; distance to AAA (≥9 critical) is still **~1.2–1.5 points** plus residual critical defects.

---

## C1–C8 verification (code spot-check)

### C1 Typography floor — **PARTIAL (operator lists CLOSED; global residue OPEN)**

**Verified closed on operator scan surfaces**

- `design-system/tokens.css`: `--text-table-header` 11px, `--text-row-primary` 13px, `--text-meta` / `--text-chip` 11px; base `--text-2xs`/`--text-xs` raised to 11px.
- `global.css` `.table-head`, `.wi-row*`, `.status-chip`, `.priority`, `.data-table th/td` use semantic tokens — **not** the old 8px headers / 10px rows.

**Still open**

- Dozens of **8–10px** hardcodes remain in `global.css` outside the list floor: `.metric-card__label` 10px, `.metric-card__detail` 9px, `.service-card p` 9px, `.category small` 8px, `.article-body p` 9px, `.side-dl dt` 9px, `.timeline small` 9px, `.sla-card span` 9px, `.avatar--sm` **9px**, dialog audit text **8px**, etc.
- Claims “avatar sm ≥9px” is literally true but **still illegible** for RU initials on dense rows.

**Harsh take:** C1 is fixed where agents scan tickets; the product still *looks* like a Figma export on marketing/metric surfaces. Not a pure FAIL on the original defect, not a clean CLOSE either.

### C2 Accessible tables — **PARTIAL (OperatorGrid CLOSED; Overview + mobile holes OPEN)**

**Verified closed (Queues / My Work path)**

- `OperatorGrid.tsx`: `role="table" | rowgroup | row | columnheader | cell"`; headers **not** `aria-hidden`; sticky head; keyboard focus index; bulk selection labels.
- Mobile ≤768: `.table-head { display: none }`, card grid reflow, **no** `min-width: 690px` trap; `.wi-row__meta-line` for chips.

**Still open**

1. **Overview “table” structure is still wrong** (`OverviewPage.tsx`):  
   - Header row is a sibling *outside* the `role="table"` container.  
   - Body wraps only rows; columnheaders are not associated with the same table.  
   - `WorkItemRow` is a `<Link role="row">` — better than R1, still not a clean table pattern (interactive row as link + nested cells).
2. **Mobile a11y regression risk:** at ≤768, priority / person / updated cells use `display: none` (removed from a11y tree), while `.wi-row__meta-line` is `aria-hidden`. Sighted users see chips; **AT users may lose priority/SLA/updated on mobile rows.**
3. Not real `<table>` — acceptable if roles are complete; they are only complete on OperatorGrid desktop.

### C3 i18n completeness — **CLOSED**

| Claim | Code evidence |
|-------|----------------|
| Header workspace | `Header.tsx` → `t('header.workspace')` (not `"Northstar"`) |
| Sidebar nav label | `Sidebar.tsx` → `aria-label={t('app.primaryNav')}` |
| SLA policy / targets | `WorkItemDetailPage.tsx` → `sla.policyP1|P2|P3`, `sla.responseTarget`, `sla.resolutionTarget` |
| Platform shortcut | Mac/Win keys via `header.searchShortcutMac` / `Win` |
| CMDB map nodes | `t('cmdb.mapNode*')` (proper nouns still EN across locales — acceptable) |
| SLA abbreviations | `sla.tomorrow`, `sla.days`, etc. in OperatorGrid / WorkItemRow |

Brand `vox` / `ITSM` still hard-coded — intentional brand, fine.

### C4 Error states — **CLOSED**

- `components/ui/ErrorState.tsx` exists (`role="alert"`, retry, default `app.error` / `app.errorHint`).
- Wired on Overview (metrics + queue), Queues, My Work, WorkItemDetail, Catalog, Knowledge, CMDB, Assets, Problems, Changes.

### C5 Queues depth — **CLOSED at claimed P0 scope; not AAA depth**

- Tabs: Unassigned / My group / Escalated / Breached / All with counts (`QueuesPage.tsx`).
- SLA filter select; filters + tab in URL (`tab`, `priority`, `type`, `status`, `sla`).
- Bulk assign/priority via `OperatorGrid` sticky bulk bar.
- Urgency: icon + state text + time (C8).

**Still short of Naumen/SN:** no saved views, no column picker, no real assignment-group model, bulk is toast-local, escalated heuristic is priority/tags not a first-class state.

### C6 WorkItemDetail workbench — **CLOSED at claimed P0 scope**

- Assign / Escalate / Resolve: handlers, disabled after use, activity + toast.
- Details tab: 2-pane workbench (fields left / activity + comment right).
- Impact / urgency / service editable (mock save toast).
- SLA i18n policy + response/resolution targets + progress bars + breach/at-risk banners with icon + text.
- Comment uses `t('workItem.commentSent')`.

**Still mock:** no API persistence, Resolve does not drive real status model end-to-end, no assignment group / child tasks / linked change workflow. Good skeleton, not SN Agent Workspace.

### C7 Mobile language switcher — **CLOSED**

- No `.language { display: none }` at ≤768.
- Compact control retained (`.language > button` padding/min-width only).
- Escape closes language menu (`Header.tsx`).

### C8 Color-only urgency — **CLOSED**

- SLA cells: Clock / Alert / Shield / Check icon + `t('sla.*')` label + time (`OperatorGrid`, `WorkItemRow`).
- Map footer: color dot **and** `map-footer__label` text.
- Detail banners: icon + copy + color.
- Priority continues icon+text pattern.

---

## Spot-check notes (CSS / pages / a11y)

| Check | Result |
|-------|--------|
| Typography floor tokens | Present and used on operator lists |
| Residual 8–10px | Widespread outside lists (metrics, catalog, KB, detail side chrome) |
| ErrorState usage | Broad and correct pattern |
| OperatorGrid a11y | Desktop roles solid; keyboard ↑↓/Enter/Space/Escape present |
| Queues tabs + URL | Present and functional in code |
| Mobile language | Visible |
| Mobile work-item cards | Visual reflow OK; **aria-hidden meta-line + display:none cells = defect** |
| Command palette | Real component; header search opens it — kills old “⌘ K is a lie” major for shell |
| Fake Reports | Still `sidebar__fake` non-link |
| Overview copilot | Still non-functional decoration |
| Knowledge reader | Modal reader with focus trap — real upgrade |
| Catalog drawer | Service drawer → create — real upgrade |

---

## Which C# still open

| ID | Status | Blocking AAA? |
|----|--------|:-------------:|
| **C1** | **PARTIAL** — list floor done; micro-type residue on non-list chrome | Soft block (scan surfaces OK; craft/consistency no) |
| **C2** | **PARTIAL** — OperatorGrid OK; Overview table + mobile AT hole | **Yes** (a11y) |
| **C3** | **CLOSED** | — |
| **C4** | **CLOSED** | — |
| **C5** | **CLOSED** (scope); depth backlog remains | Depth blocks ≥9, not as “C5 defect” |
| **C6** | **CLOSED** (scope); mock depth backlog remains | Depth blocks ≥9 |
| **C7** | **CLOSED** | — |
| **C8** | **CLOSED** | — |

**Open critical set for Round 3:** finish C2 completely; finish C1 residue on critical chrome (metrics, catalog cards, detail meta); then product depth on Queues + Detail + kill demo chrome.

---

## Remaining backlog if FAIL

### P0 — must close before any PASS attempt

1. **Finish C2 mobile a11y**  
   - Do **not** `aria-hidden` the only visible meta strip when desktop cells are `display: none`.  
   - Prefer: hide desktop cells with CSS that keeps one SR-visible structure, or drop `aria-hidden` on `.wi-row__meta-line` at mobile.  
   - Files: `global.css` (≤768 block), `OperatorGrid.tsx`, `WorkItemRow.tsx`.

2. **Fix Overview queue semantics**  
   - Put headers *inside* the same `role="table"` (or use OperatorGrid / real `<table>`).  
   - File: `OverviewPage.tsx`.

3. **C1 residue on critical surfaces**  
   - Metric labels, catalog service/category copy, detail `side-dl` / timeline / sla-card labels → ≥11px meta / ≥12–13px body.  
   - Avatar sm ≥10–11px.  
   - Files: `global.css`, optionally tokens for `--text-metric-label`.

### P1 — required to push critical scores toward ≥9

4. **Queues product depth**  
   - Real queue membership (not string-includes heuristics).  
   - Bulk that mutates shared mock/API state visible across My Work / Overview.  
   - At least one of: saved view, sort persistence in URL, sticky SLA summary strip (breached/at-risk counts).  

5. **WorkItemDetail product depth**  
   - Persist assign/resolve/status via mock store; Resolve → status `resolved` consistently.  
   - Assignment group field; primary action hierarchy already OK (Resolve primary).  
   - Linked tasks / knowledge from data, not “first 2 articles”.  

6. **Kill or wire demo chrome**  
   - Sidebar Reports: route + empty state **or** remove.  
   - Overview copilot: preview badge or remove.  
   - CMDB map: related-CI list until real graph.  

7. **Secondary modules drill-down**  
   - Assets/Problems/Changes row → detail (even thin) so craft parity holds past the demo path.

### P2 — polish

8. Self-host Manrope (air-gap).  
9. Enforce spacing tokens (odd px rhythm).  
10. Sidebar badge from live My Work count.  
11. High-contrast / density operator theme.  
12. Visual regression + contrast CI per `docs/ux/quality-gates.md`.

---

## What improved (credit)

- **Operator list typography** is no longer a WCAG-practice failure at the table layer.  
- **Error recovery** is productized (`ErrorState` + reload) across modules.  
- **Queues** went from a filtered stub to a multi-tab operator list with URL state — largest single craft jump.  
- **OperatorGrid** is the first surface that feels like a real agent tool (sort, bulk, keyboard, roles).  
- **WorkItemDetail** primary actions are no longer dead chrome; workbench layout matches SN-ish 2-pane intent.  
- **Shell** gained a real Command Palette and kept language on mobile.  
- **SLA** is no longer color-only.  
- **Catalog drawer + Knowledge reader** make consumer surfaces less of a dead end.  
- **Assets/Problems/Changes** are no longer empty table stubs — filters + density + errors.

These are **foundations of a product**, not yet an AAA operator desk.

---

## Viewport notes (R2)

| Viewport | Assessment |
|----------|------------|
| **1440** | Best impression; Queues/Detail feel intentional; residual micro-type on rails still “SaaS demo.” |
| **1024** | Workbench stacks reasonably; sidebar width still tax. |
| **768** | Language **kept** (C7 fixed); card reflow for rows (C2 visual fixed); AT meta-line issue remains. |
| **320** | Usable triage layout vs R1 horizontal 690px trap; search still tight; cards OK. |

---

## Final call

| Gate | R1 | R2 |
|------|----|----|
| Critical surfaces ≥9 | FAIL (best ~6.5) | **FAIL** (best **7.8**) |
| No critical a11y/usability defects | FAIL (C1–C8) | **FAIL** (C2 residue + C1 partial) |
| Blind A/B vs Naumen/SN/JSM | They win | **They still win** |
| Score trajectory | — | **+1.6 avg / critical** |
| **AAA verdict** | **FAIL** | **FAIL** |

**Round 3 should only re-score after:** (1) C2 fully closed including mobile AT, (2) Overview table fixed, (3) C1 residue cleaned on metrics/catalog/detail meta, (4) Queues + Detail show shared mutable mock state (not toast theatre), (5) fake Reports/copilot either wired or removed.

Cosmetic gradients will not change this verdict. The R1→R2 delta is **real engineering**; AAA still requires **operator product depth + zero open critical a11y**.
