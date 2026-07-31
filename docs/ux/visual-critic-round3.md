# Vox ITSM — Visual / Operator UX Critic Round 3 (Final Gate)

**Date:** 2026-07-30  
**Scope:** `G:\ITSM\frontend\src` (live code after R2 fixes)  
**Inputs:**  
- `docs/ux/visual-critic-round2.md`  
- `docs/ux/visual-critic-fixes-round2.md`  
- `docs/ux/visual-critic-round1.md` (context only)  
**Bar:** Triple-A operator workspace vs modern **Naumen ITSM / ServiceNow / Jira Service Management**  
**Viewports mentally reviewed:** 1440 · 1024 · 768 · 320  
**AAA gate:** ≥9 on **Overview, Queues, WorkItemDetail, Shell, Catalog** **and** no open critical a11y/usability defects.

---

## Verdict: **FAIL**

Round 3 is the first time this product **clears the R1 critical-defect set** on fundamentals (C1 typography floor, C2 table/mobile AT). Shared mock mutability, real Reports route, functional Overview copilot shortcuts, and Command Palette make the **operator path** feel like a product triage loop rather than toast theatre.

It is **still not AAA**. Critical surfaces land in the **high 8s (8.1–8.6)**, not ≥9. Depth vs a current Naumen agent desk or ServiceNow Workspace remains the gap: heuristic queues, incomplete workbench field persistence, thin secondary modules, placeholder Reports, residual demo chrome on Catalog/KB.

**Does not meet AAA.** Zero open *critical* C1/C2-class defects; **scores fail the ≥9 gate** and product-depth backlog still blocks an honest PASS.

---

## Blind A/B winner

**Winner: ServiceNow / Naumen / JSM operator UI** (narrower margin than R1/R2).

| Dimension | Winner | Why |
|-----------|--------|-----|
| Premium polish (chrome, marketing) | **Vox slight edge** | Catalog hero, shell, copilot, violet/navy system still more “designed SaaS” |
| Operator density & scan speed | **Tie → Vox close** | Type floor holds (≥11 meta / 13 row); OperatorGrid sort/bulk/keyboard is real |
| Hierarchy & IA consistency | **Naumen / SN / JSM** | Assets/Problems/Changes still list shells; no module drill-down craft parity |
| SLA urgency choreography | **Vox competitive / slight SN edge** | Icon + state + time + mini bar + banners; no enterprise breach workflow / clock product |
| Consistency across modules | **Naumen / SN / JSM** | Operator path coherent; secondary + Reports shallow |
| Russian typography at operator sizes | **Vox competitive** | List + metrics + catalog body on tokens; only decorative micro leftovers |
| Demo vs product feel | **Naumen / SN / JSM** | Mock store closes list↔detail loop; impact/urgency/comments/queue model still demo-thin |

**Honest summary:** Unlabeled Overview + Queues + Detail, Vox no longer loses on “illegible toy + dead buttons.” It still loses a blind **8-hour shift** A/B on **operational depth and consistency**, not paint.

---

## Score table

| Surface | R1 | R2 | R3 | Δ R2→R3 | Critical for AAA? | Notes |
|---------|---:|---:|---:|--------:|:-----------------:|-------|
| **Overview** | 6.5 | 7.5 | **8.4** | +0.9 | Yes | Table semantics fixed; store sync; functional copilot jumps; type floor on metrics |
| **MyWork** | 5.5 | 7.5 | **8.3** | +0.8 | No | Same OperatorGrid + shared bulk mutations |
| **Queues** | 5.0 | 7.8 | **8.6** | +0.8 | Yes | Best critical surface; bulk/list↔detail consistency; still heuristic tabs / no saved views |
| **Catalog** | 6.5 | 7.3 | **8.1** | +0.8 | Yes | Type floor + drawer→create; aside “ask assistant” still dead CTA |
| **Knowledge** | 5.5 | 6.8 | **7.2** | +0.4 | No | Reader exists; residual **7px** `.article-score small`; shallow tabs |
| **CMDB** | 5.0 | 6.0 | **6.2** | +0.2 | No | Map still CSS illustration |
| **Assets** | 4.0 | 6.0 | **6.3** | +0.3 | No | Filters/density/error; Add dead; no detail |
| **Problems** | 4.0 | 6.0 | **6.3** | +0.3 | No | Same list-template tier |
| **Changes** | 4.0 | 6.0 | **6.3** | +0.3 | No | Same list-template tier |
| **Settings** | 5.5 | 6.2 | **6.5** | +0.3 | No | Density hook product-wide; still light admin |
| **WorkItemDetail** | 5.5 | 7.8 | **8.5** | +0.7 | Yes | Assign/Escalate/Resolve → store; workbench; impact/urgency still local toast; comments local |
| **Shell (sidebar/header)** | 6.5 | 7.8 | **8.6** | +0.8 | Yes | Palette + Reports NavLink + language ≤768; **badge: 3** hardcoded; `/reports` missing from crumb map |
| **Reports** | — | — | **7.0** | n/a | No | Honest live metrics + SLA deep-links; not analytics product |

**AAA requires ≥9 on Overview, Queues, WorkItemDetail, Shell, Catalog → all fail (best critical = 8.6).**

| Aggregate | R1 | R2 | R3 | Δ R2→R3 |
|-----------|---:|---:|---:|--------:|
| Average (scored surfaces) | ~5.3 | ~6.9 | **~7.5** | **+0.6** |
| Average (critical only) | ~6.0 | ~7.6 | **~8.4** | **+0.8** |

**How much improved R2→R3:** ~**+0.8** on critical average. Trajectory R1→R3 ≈ **+2.4** critical. Distance to AAA (≥9) is still **~0.4–0.9 points** of product depth on every critical surface — not another typography pass.

---

## C1 / C2 verification (must fully close)

### C1 Typography floor — **CLOSED**

| Check | Evidence |
|-------|----------|
| Semantic tokens | `design-system/tokens.css`: `--text-table-header` 11px, `--text-row-primary` 13px, `--text-meta` / `--text-chip` 11px; base `--text-2xs`/`--text-xs` = 11px |
| Operator lists | `.table-head`, `.wi-row*`, `.status-chip`, `.priority`, `.data-table th/td` on semantic tokens |
| Metrics | `.metric-card__label` / `__detail` → `var(--text-meta)` |
| Catalog | `.service-card p` → `var(--text-sm)` (12px); `.category small` → `var(--text-meta)` |
| Detail meta | `.side-dl dt` → meta; `dd` 12px; avatar sm → `var(--text-meta)` (11px) |
| Hardcoded 8–9px operator body | **None found** under `frontend/src` |

**Allowed decorative only (not operator content):**

- `.brand em` **10px**
- `.eyebrow` **10px**
- `kbd` chrome (command palette / search shortcut)

**Non-blocking nit (not C1 reopen):** `.article-score small { font-size: 7px }` in `global.css` — Knowledge decorative score chrome. Fix in polish; does **not** reopen C1 on operator scan surfaces.

**Harsh take:** C1 as originally filed (list illegibility + enterprise RU floor) is **done**. Product no longer looks like a 0.75× Figma export on the agent path.

### C2 Accessible tables — **CLOSED**

| Check | Evidence |
|-------|----------|
| Overview table | `OverviewPage.tsx`: single `role="table"` wraps header **and** body rowgroups; `role="columnheader"` **not** `aria-hidden`; `aria-rowcount` set |
| OperatorGrid | Full `table` / `rowgroup` / `row` / `columnheader` / `cell`; sticky head; keyboard j/k/↑↓/Enter/Space/X; bulk labels |
| Mobile meta AT | `WorkItemRow.tsx` + `OperatorGrid.tsx`: `.wi-row__meta-line` **without** `aria-hidden` |
| Mobile CSS | ≤768: desktop cells (non-ticket/check) `display: none`; meta-line `display: flex` as mobile source of truth (`global.css`) |

**Acceptable residual pattern (not critical):** Overview rows remain `<Link role="row">` (interactive container). Preferable long-term is OperatorGrid-style row + navigate, but headers are associated and cells expose roles — original C2 defect is satisfied.

**Harsh take:** C2 is **fully closed** for the R2 must-close definition. No open critical table/mobile AT defect blocks PASS on a11y grounds alone.

---

## Spot-check notes (R3 live code)

### Overview table
- Headers **inside** `role="table"` with dual `rowgroup`s — **PASS**.
- ErrorState + store sync + runtime greeting date + SLA urgency strip — solid.
- Copilot: suggestions navigate Queues (`tab=breached` / `sla=at_risk` / `tab=unassigned`); ask opens Command Palette — **functional**, not dead chrome.

### OperatorGrid mobile meta
- Meta-line exposed; desktop column cells hidden at ≤768 — **PASS** for AT parity.
- Bulk assign/priority → `bulkAssignWorkItems` / `bulkSetPriority` → `mock/store.ts` + `useWorkItemsSync` — **shared mutability PASS**.

### Typography tokens
- Floor tokens present and used on operator + metric + catalog + detail meta surfaces — **PASS**.
- Residual 7px Knowledge score — polish only.

### Reports page
- Real route `/reports`, Sidebar `NavLink` (no `sidebar__fake`), live derived counts from store, deep-links into Queues, honest placeholder copy — **demo-chrome honesty PASS**.
- Depth: metrics strip + links, not reporting product → score **7.0**.
- Shell crumb map omits `/reports` → breadcrumb falls through to Overview label (**shell nit**).

### Mock store mutability
- `mock/store.ts`: assign / priority / escalate / resolve / patch / create + `subscribeWorkItems`.
- API mock mode writes store; Overview / Queues / My Work / Detail / Reports resync — **PASS** for list↔detail consistency after triage actions.
- Gaps: comments still **local state** on Detail; impact/urgency **local + toast** (only service patches store).

### CommandPalette
- Wired in `AppShell`; header search opens it; global hotkey; focus trap; nav + create + work-item search + recent — **real operator chrome**.
- Missing Reports in static nav commands (minor).

### Queues
- Tabs Unassigned / My group / Escalated / Breached / All + counts; SLA/priority/type/status filters; URL state; OperatorGrid bulk/sort/density.
- **Depth shortfall:** `mygroup` / `escalated` are string/priority heuristics, not first-class queue membership or escalation state model. No saved views, column picker, sort-in-URL.

### WorkItemDetail
- Assign / Escalate / Resolve hit API → store; disabled states after resolve/assign/escalated tag; breach/at-risk banners with icon+text; 2-pane workbench; i18n SLA policy keys.
- **Depth shortfall:** impact/urgency toast-only; comments local; assignment group absent; escalate activity text reuses generic status key; linked KB still “nearby data” not case-linked workflow.

### Shell
- i18n workspace crumb, platform shortcut, language Escape + visible ≤768, Command Palette, Reports link, skip link.
- **Nits:** My Work `badge: 3` hardcoded; Reports crumb missing; workspace switcher non-functional (acceptable v1).

### Catalog
- Search, category chips, service cards ≥12px body, drawer with focus trap → create incident/request path — real.
- Aside `catalog.askAssistant` button has **no handler** — residual decorative CTA (major craft, not C1/C2).

---

## C1–C8 status board (R3)

| ID | Status | Blocking AAA? |
|----|--------|:-------------:|
| **C1** | **CLOSED** (decorative 10px brand/eyebrow OK; 7px KB score = polish) | No |
| **C2** | **CLOSED** (Overview table + mobile meta AT) | No |
| **C3** | **CLOSED** (R2) | No |
| **C4** | **CLOSED** (R2) | No |
| **C5** | **CLOSED** at claimed P0 scope; depth backlog | Depth blocks ≥9 |
| **C6** | **CLOSED** at claimed P0 scope; mock field gaps | Depth blocks ≥9 |
| **C7** | **CLOSED** (R2) | No |
| **C8** | **CLOSED** (R2) | No |

**Open critical a11y/usability defects from original C1–C8 set: none.**  
**AAA still fails on score threshold + product-depth bar vs Naumen/SN/JSM.**

---

## 8-hour shift question

> If I put Vox and a current Naumen agent desk side by side blind, which would an ITSM agent pick for an 8-hour shift and why?

**Pick: Naumen (or ServiceNow Agent Workspace / JSM queues) — still.**

**Why an agent would choose Naumen over Vox today**

1. **Queue truth** — Real assignment groups, multi-queue ownership, escalation as process state — not `queue.includes('network')` heuristics and priority-as-escalated.
2. **Workbench completeness** — Fields that stick (impact, urgency, assignment group, CI links, related records) without “saved” toasts that don’t survive reload of local-only controls.
3. **Shift muscle memory** — Saved views, bulk that agents trust across modules, column layouts, SLA clocks tied to policy engine, predictable secondary modules (Assets/Problems/Changes with drill-down).
4. **Reporting & governance** — Reports that managers and agents use mid-shift; Vox Reports is an honest placeholder, not a desk tool.

**Why Vox would still get respect in the same blind test**

- Faster visual scan once type floor is honest; SLA cells are not color-only toys.
- Keyboard-forward OperatorGrid + Command Palette feel modern.
- Assign/Resolve that updates lists is a real triage loop (mock store) — R1 would have lost in five minutes; R3 survives a demo shift hour.

**Bottom line:** Agents pick the desk that won’t strand them at hour six. Vox is now a **credible modern prototype of that desk**; Naumen is still the desk.

---

## Remaining backlog if FAIL (precise)

### P0 — required to push critical scores to ≥9 (AAA attempt)

1. **Queues product depth (largest single score lever)**  
   - Real queue / assignment-group model in mock data (not string includes).  
   - Escalated as first-class flag/state written by escalate action (already tags — use consistently in filters + UI).  
   - At least one of: **saved view**, **sort+filters fully in URL**, sticky **SLA summary strip** (breached / at-risk / unassigned counts).  
   - Files: `QueuesPage.tsx`, `mock/data.ts`, `mock/store.ts`, i18n.

2. **WorkItemDetail field honesty**  
   - Persist impact / urgency / assignment group via `updateWorkItem` (extend store patch types).  
   - Persist comments in store (or disable compose with honest empty).  
   - Fix escalate/resolve activity copy keys to real event strings.  
   - Files: `WorkItemDetailPage.tsx`, `mock/store.ts`, `api/workItems.ts`, locales.

3. **Catalog dead CTA**  
   - Wire `catalog.askAssistant` → Command Palette or Knowledge; or remove button.  
   - File: `CatalogPage.tsx`.

4. **Shell integrity nits that read as unfinished**  
   - My Work badge from live open-assigned count.  
   - Add `/reports` to `AppShell` crumb map (+ Command Palette nav entry).  
   - Files: `Sidebar.tsx`, `AppShell.tsx`, `CommandPalette.tsx`.

### P1 — consistency so blind A/B stops losing on “unfinished product”

5. Secondary modules: Assets / Problems / Changes **row → detail** (even thin) + kill or wire primary Add buttons.  
6. CMDB: related-CI list as primary; demote CSS map to “illustration” label or remove.  
7. Knowledge: kill **7px** score chrome; tabs that change query.  
8. Reports: one real breakdown table (by priority/queue) from store — still mock-ok.

### P2 — polish / enterprise

9. Self-host Manrope (air-gap).  
10. Spacing token cleanup (odd px rhythm).  
11. High-contrast / density operator theme.  
12. Visual regression + contrast CI per `docs/ux/quality-gates.md`.  
13. Prefer non-link table rows on Overview (reuse OperatorGrid subset) for a11y purity.

---

## What improved R2→R3 (credit)

- **C1 closed** on operator-readable content — metrics, catalog, detail meta, avatars.  
- **C2 closed** — Overview table structure + mobile meta AT hole fixed.  
- **Shared mock store** — bulk and detail mutations visible across Overview / Queues / My Work / Reports.  
- **Reports** is a real route with live numbers, not `sidebar__fake`.  
- **Copilot** is a shortcut rail into Queues + Command Palette, not wallpaper.  
- Critical average **7.6 → 8.4** without score inflation.

These close the **“foundations of a product”** chapter. AAA is **shift-grade depth**, not another defect laundry list.

---

## Viewport notes (R3)

| Viewport | Assessment |
|----------|------------|
| **1440** | Strongest: Queues/Detail/Shell read intentional; residual secondary-module thinness still visible if you leave the happy path. |
| **1024** | Workbench stacks; sidebar width tax remains; usable. |
| **768** | Language kept; card reflow + meta-line AT OK; search kbd hidden — acceptable. |
| **320** | Triage cards usable; no 690px trap; header search tight for long RU placeholder — known P2. |

---

## Final call

| Gate | R1 | R2 | R3 |
|------|----|----|----|
| Critical surfaces ≥9 | FAIL (best ~6.5) | FAIL (best 7.8) | **FAIL (best 8.6)** |
| No critical a11y/usability defects (C1–C8) | FAIL | FAIL (C1 partial, C2 partial) | **PASS (C1–C8 closed)** |
| Blind A/B vs Naumen/SN/JSM | They win | They win | **They still win (narrower)** |
| Score trajectory (critical avg) | ~6.0 | ~7.6 | **~8.4** |
| **AAA verdict** | **FAIL** | **FAIL** | **FAIL** |

### PASS/FAIL + scores (executive)

**Verdict: FAIL**

| Critical surface | R3 |
|------------------|---:|
| Overview | **8.4** |
| Queues | **8.6** |
| WorkItemDetail | **8.5** |
| Shell | **8.6** |
| Catalog | **8.1** |
| **Critical average** | **~8.4** |

| All surfaces (R3) | Score |
|-------------------|------:|
| MyWork | 8.3 |
| Knowledge | 7.2 |
| Reports | 7.0 |
| Settings | 6.5 |
| Assets / Problems / Changes | 6.3 |
| CMDB | 6.2 |

**Why not PASS despite closed C1/C2:** AAA gate is explicit — **≥9 on five critical surfaces**. High-8 craft with a working mock triage loop is success for R3 engineering; it is not Naumen-class shift readiness. Next round should only re-score after P0 depth items land — not more gradient work.
