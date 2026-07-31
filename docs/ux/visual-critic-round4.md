# Vox ITSM — Visual / Operator UX Critic Round 4 (AAA Gate after Depth Pass)

**Date:** 2026-07-31  
**Scope:** `G:\ITSM\frontend\src` (live code after `docs/ux/aaa-depth-pass.md`)  
**Inputs:**  
- `docs/ux/aaa-depth-pass.md`  
- `docs/ux/visual-critic-round3.md`  
- Prior R1/R2 critic + fix notes (context only)  
**Bar:** Triple-A operator workspace vs modern **Naumen ITSM / ServiceNow Agent Workspace / Jira Service Management**  
**Viewports mentally reviewed:** 1440 · 1024 · 768 · 320  
**AAA gate:** ≥9 on **Overview, Queues, WorkItemDetail, Shell, Catalog** **and** zero open critical a11y/usability defects.

---

## Verdict: **PASS**

Round 4 is the first time critical surfaces clear the **≥9** bar **and** the original C1–C8 critical-defect set remains closed. This is not paint: the depth pass closed the exact P0 list R3 named as the path to AAA — real queue predicates, saved views, workbench field honesty, resolve-with-notes, catalog assistant + create-into-store, shell badge/crumb integrity, secondary drawers.

**Harsh honesty:** Self-score in `aaa-depth-pass.md` is **slightly inflated at the top of the band** (Queues 9.3 → **9.1**, Detail 9.2 → **9.0**, Overview 9.1 → **9.0**). It is **not** fabricated. Critical average is a genuine **~9.0**, not a marketing 9.5. Secondary modules and Reports remain sub-AAA and would still lose a multi-module enterprise bake-off — but they are **not** in the AAA gate set.

**Meets AAA** as defined: five critical surfaces ≥9 + zero critical defects.

---

## Verification checklist (live code)

| Claim | Result | Evidence |
|-------|:------:|----------|
| Shared store mutations persist across Queues / Detail / Overview | **PASS** | `mock/store.ts` mutates assign / escalate / resolve / priority / impact / urgency / comments / watchers / create; `subscribeWorkItems` + `useWorkItemsSync` on Overview, Queues, My Work, Detail, Reports; Sidebar badge subscribes live |
| Saved views exist | **PASS** | `QueuesPage.tsx`: 2 builtins + custom save → `localStorage` key `vox-queue-saved-views`; apply restores URL filters |
| Resolve requires notes | **PASS** | `WorkItemDetailPage.handleResolve` blocks empty notes (`resolutionRequired`); `resolveWorkItem(id, notes)` → store `resolutionNotes` + `status: resolved` |
| Catalog creates work items | **PASS** | Drawer `createWorkItem` → `addWorkItem` store → toast + `navigate('/queues?tab=unassigned')` |
| Secondary detail drawers | **PASS** | Assets / Problems / Changes: row → `ModuleDetailDrawer` with serial/model, root cause/workaround, implementation/backout |
| Typography / a11y not regressed | **PASS** | C1 floor tokens intact (11/13 meta/row); operator lists still on semantic tokens; C2 OperatorGrid + mobile meta-line still exposed; only residual **7px** Knowledge `.article-score small` (decorative, non-blocking) |

### Store honesty caveats (not critical defects)

- Mutability is **in-session mock store**, not durable multi-tenant backend. Hard browser reload re-seeds from `mock/data.ts`. “Sticks when you leave Detail and open Queues” is true; “survives F5” is not claimed and not true.
- `isEscalated` still ORs `priority === 'critical'` with first-class `escalated` flag — workable operator filter, not pure process-state purity.
- Builtin “Unassigned high+” applies exact `priority=high` URL filter (not high **and** critical). Craft nit, not a critical defect.

---

## Blind A/B winner

**Overall product-wide winner: still ServiceNow / Naumen / JSM** — but the margin is the **narrowest of all four rounds**, and **Vox can now win or tie several dimensions**.

| Dimension | Winner | Why |
|-----------|--------|-----|
| Premium polish (chrome, marketing) | **Vox wins** | Catalog hero, violet/navy system, shell, assistant panels still more “designed SaaS” than enterprise chrome |
| Operator density & scan speed | **Tie → Vox slight edge** | Type floor holds; OperatorGrid sort/bulk/keyboard + queue column + SLA strip is shift-scan competitive |
| Hierarchy & IA consistency | **Naumen / SN / JSM** | Secondary modules have drawers now (~7.6) but not full module craft parity; CMDB map still illustration |
| SLA urgency choreography | **Tie** | Icon + state + time + mini bar + banners + queue SLA strip; no enterprise policy-engine breach workflow |
| Consistency across modules | **Naumen / SN / JSM** | Operator path (Overview↔Queues↔Detail) is coherent product; Assets/Problems/Changes/Reports still one tier down |
| Russian typography at operator sizes | **Vox competitive / slight win** | List + metrics + catalog body on ≥11/12/13 tokens; residual decorative micro only |
| Demo vs product feel (operator path) | **Tie** | List↔detail↔queue truth loop is real mock product; enterprise governance / multi-queue ownership still thinner |
| Demo vs product feel (whole suite) | **Naumen / SN / JSM** | Full desk still deeper |

**Honest summary:** Unlabeled Overview + Queues + Detail for an L1 triage hour, Vox no longer automatically loses. Unlabeled full ITSM suite for an 8-hour multi-module shift, enterprise desks still win — but **Vox wins polish and can tie density / SLA / demo-feel on the agent path**.

---

## Score table (R1–R4)

| Surface | R1 | R2 | R3 | R4 | Δ R3→R4 | Critical for AAA? | Notes |
|---------|---:|---:|---:|---:|--------:|:-----------------:|-------|
| **Overview** | 6.5 | 7.5 | 8.4 | **9.0** | +0.6 | Yes | Live store metrics + OperatorGrid parity (`showQueue`, bulk, limit 8) + functional copilot; still no personalization |
| **MyWork** | 5.5 | 7.5 | 8.3 | **8.7** | +0.4 | No | Same OperatorGrid + live badge source; inherits store truth |
| **Queues** | 5.0 | 7.8 | 8.6 | **9.1** | +0.5 | Yes | **Best critical surface.** Real predicates, saved views, SLA strip, queue column, per-tab empty — self 9.3 slightly hot |
| **Catalog** | 6.5 | 7.3 | 8.1 | **9.0** | +0.9 | Yes | Ask assistant panel (3 suggestions → drawers) + create-into-store; still one-click request, not full form product |
| **Knowledge** | 5.5 | 6.8 | 7.2 | **7.3** | +0.1 | No | Reader exists; **7px** score chrome remains polish-only |
| **CMDB** | 5.0 | 6.0 | 6.2 | **6.3** | +0.1 | No | Map still CSS illustration |
| **Assets** | 4.0 | 6.0 | 6.3 | **7.6** | +1.3 | No | Row → drawer with serial/model/vendor; Add = honest mock toast |
| **Problems** | 4.0 | 6.0 | 6.3 | **7.6** | +1.3 | No | Drawer: root cause / workaround |
| **Changes** | 4.0 | 6.0 | 6.3 | **7.6** | +1.3 | No | Drawer: implementation / backout |
| **Settings** | 5.5 | 6.2 | 6.5 | **6.6** | +0.1 | No | Density still product-wide; light admin |
| **WorkItemDetail** | 5.5 | 7.8 | 8.5 | **9.0** | +0.5 | Yes | Impact/urgency/priority/service → store; comments store; resolve notes required; watchers; macro; child tasks — self 9.2 slight inflate |
| **Shell (sidebar/header)** | 6.5 | 7.8 | 8.6 | **9.0** | +0.4 | Yes | Live My Work badge; `/reports` crumb + palette; Command Palette complete for nav |
| **Reports** | — | — | 7.0 | **7.2** | +0.2 | No | Live aggregates + deep-links; still not analytics product |

**AAA requires ≥9 on Overview, Queues, WorkItemDetail, Shell, Catalog → all clear (best critical = 9.1, floor = 9.0).**

| Aggregate | R1 | R2 | R3 | R4 | Δ R3→R4 |
|-----------|---:|---:|---:|---:|--------:|
| Average (scored surfaces) | ~5.3 | ~6.9 | ~7.5 | **~8.1** | **+0.6** |
| Average (critical only) | ~6.0 | ~7.6 | ~8.4 | **~9.0** | **+0.6** |

**How much improved R3→R4:** ~**+0.6** critical average — smaller jump than R1→R2 or R2→R3 because foundations were already closed; this round bought **product depth**, not legibility.

### Self-score vs critic (inflation callout)

| Surface | Depth-pass self | R4 critic | Inflated? |
|---------|----------------:|----------:|:---------:|
| Overview | 9.1 | **9.0** | Mild |
| Queues | 9.3 | **9.1** | Mild (still best surface) |
| WorkItemDetail | 9.2 | **9.0** | Mild |
| Shell | 9.0 | **9.0** | No |
| Catalog | 9.0 | **9.0** | No |
| Critical avg | ~9.1 | **~9.0** | **~0.1 overclaim** |

Self-score is **directionally correct and within honest rounding**. Not a rubber stamp of 9.5 theatre.

---

## Critical ≥9 check

| Surface | R4 | ≥9? |
|---------|---:|:---:|
| Overview | **9.0** | Yes |
| Queues | **9.1** | Yes |
| WorkItemDetail | **9.0** | Yes |
| Shell | **9.0** | Yes |
| Catalog | **9.0** | Yes |
| **Critical average** | **~9.02** | **Pass threshold** |

---

## C1 / C2 + critical defects

### C1 Typography floor — **CLOSED (no regression)**

- Semantic tokens still: `--text-table-header` 11px, `--text-row-primary` 13px, `--text-meta` / `--text-chip` 11px.
- Operator lists, metrics, catalog body remain on floor tokens.
- Allowed decorative: `.brand em` 10px, `.eyebrow` 10px, kbd chrome.
- Residual: `.article-score small { font-size: 7px }` — Knowledge decorative only; **does not reopen C1**.

### C2 Accessible tables — **CLOSED (no regression)**

- OperatorGrid: full `table` / `rowgroup` / `row` / `columnheader` / `cell`; sticky head; keyboard; bulk labels.
- Mobile: meta-line remains AT source of truth; desktop non-ticket cells hidden ≤768.
- Overview work queue now **is** OperatorGrid (stronger than R3 Link-table pattern for the main queue panel).

### Open critical defects

**None.**

No new C1–C8-class critical a11y/usability defects introduced by the depth pass. No reopen of closed fundamentals.

| ID | Status | Blocking AAA? |
|----|--------|:-------------:|
| C1–C8 | **CLOSED** (R3 + R4 re-verify) | No |
| Product-depth score gap | **CLOSED for critical five** | No |
| Secondary / Reports depth | Open backlog (non-gated) | No |

---

## Spot-check notes (R4 live code)

### Shared mock store
- `assignWorkItems`, `escalateWorkItem` (`escalated: true` + tags), `resolveWorkItem(notes)`, `updateWorkItem` (impact/urgency/status/service/priority), `addComment`, `addWatcher` / `removeWatcher`, `addWorkItem`.
- Queue predicates centralized: `isUnassigned`, `isMyGroup(teamId)`, `isEscalated`, `isBreached`.
- API mock mode writes store; pages resync via `useWorkItemsSync`.
- **Cross-surface loop:** Detail assign → Queues Unassigned count drops; escalate → Escalated tab; resolve → lists drop open items; Catalog create → Unassigned; Overview metrics recompute.

### Queues
- Tabs + counts from real predicates (not string-includes heuristics).
- Saved views menu: builtins + prompt-save custom → localStorage.
- Sticky SLA summary strip (breached / unassigned / escalated / my group).
- `OperatorGrid showQueue` + density + URL filters.
- Per-queue empty copy + reset CTA.

### WorkItemDetail
- Impact / urgency / priority / service patch store (useEffect resyncs local drafts from `wi`).
- Comments append store + activity (`comment_added`).
- Resolve modal requires non-empty notes.
- Child tasks + watchers sections from item fields.
- Macro “Request more info” → `status: waiting` + public comment template.
- Escalate disabled once `wi.escalated`; activity key `escalated` / field keys exist in i18n en/ru/de.

### Catalog
- `askAssistant` opens assistant panel (focus trap) with 3 smart service suggestions → service drawer.
- Secondary path: open Command Palette from panel.
- Request → `createWorkItem` → store → Queues unassigned.

### Shell
- My Work badge = `countMyOpenAssigned()` + subscribe (not hardcoded `3`).
- `/reports` in `AppShell` crumb map + Command Palette nav + Sidebar `NavLink`.
- Command Palette still real (focus trap, recent, create, WI search).

### Secondary modules
- Assets/Problems/Changes: filters + density + ErrorState + **detail drawers** + honest Add mock toasts.
- Scores jump ~6.3 → ~7.6; still not AAA-gated module products.

### Overview
- Metrics from live store aggregates.
- Work queue = OperatorGrid (parity with Queues, not a weaker Link table).
- Copilot suggestions navigate real queue filters.

---

## 8-hour shift question

> If I put Vox and a current Naumen agent desk side by side blind, which would an ITSM agent pick for an 8-hour shift and why?

**Pick: Split by role — pure L1 triage can pick Vox; full multi-module shift still picks Naumen / ServiceNow / JSM.**

### Why Vox is now a credible shift pick for L1 queue work

1. **Queue truth** — Unassigned / My group (`teamId`) / Escalated (flag) / Breached are real filters; counts move when you act.
2. **Workbench honesty** — Impact, urgency, comments, resolve notes stick across list navigation; no more toast theatre for core fields.
3. **Shift muscle (partial)** — Saved views, bulk on OperatorGrid, Command Palette, keyboard j/k/Enter/X, density toggle.
4. **Scan speed** — Type floor + SLA cells + queue column make the desk feel faster than older enterprise skins.

### Why a full-desk agent still picks Naumen/SN/JSM for eight hours

1. **Assignment group as process UI** — `teamId` exists in data; Detail does not offer reassignment / group picker as operator control.
2. **Saved views are filter snapshots** — not column layouts, shared team views, or multi-queue boards.
3. **Secondary modules** — drawers close the “dead list” insult but Assets/Problems/Changes are not shift homes (~7.6).
4. **Reports & governance** — still operational snapshot (~7.2), not mid-shift analytics or CAB/approval depth.
5. **Persistence model** — in-memory mock; enterprise desks don’t re-seed on reload.

**Bottom line:** R1 lost in five minutes. R3 survived a demo hour. **R4 survives a focused L1 triage shift.** Full-service 8-hour multi-module work still goes to enterprise desks — but the gap is **depth of suite**, not “illegible prototype.”

---

## Remaining backlog (non-blocking for AAA gate)

### P1 — suite consistency (blind A/B on “whole product”)

1. Assignment group / reassignment control on Detail (write `teamId` + assignee picker beyond “Assign to me”).
2. Saved views: high+ means ≥ high; optional column visibility.
3. Knowledge: kill 7px score chrome; topic tabs that change query.
4. CMDB: related-CI list as primary; label map as illustration.
5. Reports: one breakdown table (priority/queue) from store.
6. Secondary Add → real create forms (or keep toast but link to Catalog).

### P2 — polish / enterprise

7. Self-host Manrope (air-gap).
8. High-contrast / density operator theme polish.
9. Visual regression + contrast CI per `docs/ux/quality-gates.md`.
10. Child-task status toggles that write store.
11. Durable mock persistence (sessionStorage) if demo reloads matter.

---

## What improved R3→R4 (credit)

- **Queues** became a real operator surface: predicates, saved views, SLA strip, queue column.
- **WorkItemDetail** became field-honest: impact/urgency/comments/resolve notes/watchers/macros.
- **Catalog** dead CTA removed; assistant + create-into-store closes the request path.
- **Shell** finished-product nits (live badge, reports crumbs/palette).
- **Secondary** drawers lift Assets/Problems/Changes out of pure list-shell insult.
- Critical average **8.4 → ~9.0** without reopening C1/C2.

---

## Viewport notes (R4)

| Viewport | Assessment |
|----------|------------|
| **1440** | AAA-credible: Queues/Detail/Shell/Overview read as intentional operator product. |
| **1024** | Workbench stacks; usable; sidebar tax remains. |
| **768** | Language kept; card reflow + meta-line AT OK; queue strip + filters stack. |
| **320** | Triage cards usable; header search still tight for long RU placeholder — P2. |

---

## Final call

| Gate | R1 | R2 | R3 | R4 |
|------|----|----|----|----|
| Critical surfaces ≥9 | FAIL (best ~6.5) | FAIL (best 7.8) | FAIL (best 8.6) | **PASS (floor 9.0 / best 9.1)** |
| No critical a11y/usability defects (C1–C8) | FAIL | FAIL (partial) | PASS | **PASS (no regression)** |
| Blind A/B vs Naumen/SN/JSM | They win | They win | They win (narrower) | **They win suite-wide; Vox wins polish / can tie operator path** |
| Score trajectory (critical avg) | ~6.0 | ~7.6 | ~8.4 | **~9.0** |
| **AAA verdict** | **FAIL** | **FAIL** | **FAIL** | **PASS** |

### PASS/FAIL + scores (executive)

**Verdict: PASS**

| Critical surface | R4 |
|------------------|---:|
| Overview | **9.0** |
| Queues | **9.1** |
| WorkItemDetail | **9.0** |
| Shell | **9.0** |
| Catalog | **9.0** |
| **Critical average** | **~9.0** |

| All surfaces (R4) | Score |
|-------------------|------:|
| MyWork | 8.7 |
| Assets / Problems / Changes | 7.6 |
| Knowledge | 7.3 |
| Reports | 7.2 |
| Settings | 6.6 |
| CMDB | 6.3 |

**Why PASS is honest:** R3 named concrete P0 depth items as the only path to ≥9. Those items landed in code and were re-verified (store cross-surface mutability, saved views, resolve notes, catalog create, drawers, no type/AT regression). Critic trims ~0.1–0.2 of self-score heat but **does not drop any critical surface below 9.0**. AAA as defined is met; enterprise suite depth beyond the gate remains open backlog, not a silent redefinition of the gate.
