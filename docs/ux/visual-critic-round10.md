# Vox ITSM — Visual / Operator UX Critic Round 10 (Wave 4: ModuleGrid · Settings tabs · Catalog form · Automation admin)

**Date:** 2026-07-31  
**Scope:** Live code under `G:\ITSM\frontend\src` after R9 PASS + Wave 4 (`docs/ux/wave4-module-grid.md`, `docs/ux/wave4-settings-catalog.md`).  
**Inputs:** `docs/ux/visual-critic-round9.md`, wave4 docs, live pages/components/CSS/i18n/mock.

**Focus this wave:**  
S3b ModuleGrid close · Settings section nav + appearance + notif persistence · Catalog DynamicForm + toast link · Admin Metadata depth · Automation admin inspector  

**Regression scan:** Overview · Queues · WorkItemDetail · Shell · Catalog · Knowledge · Problems · Changes CAB · Assets · Reports · CMDB  

**Viewports mentally reviewed:** 1440 · 1024 · 768 · 320

**Elevation bar (this round — non-negotiable):**  
- **No regressions** on critical five (≥ 9.0).  
- **S3b closed** as claimed — shared ModuleGrid with sticky head, bulk, aria-sort, kbd — not ModuleBulkBar-only cosplay.  
- **Settings ≥ 8.0** as organized admin surface — refuse self **8.2** if residuals keep it gate-floor.  
- Self-score inflation ≤ 0.2 vs critic on every claimed surface; list-craft self-scores must not launder into suite scores.  
- **No** multi-module bake-off victory claim.

---

## Verdict: **PASS**

Wave 4 is **real residual craft**, not score theatre. **S3b is CLOSED**: Assets / Problems / Changes no longer ship bespoke tables; `ModuleGrid` is a genuine OperatorGrid-class shared list (sticky head, bulk bar, aria-sort, J/K · Space · Enter · Ctrl+A · Esc, density). **Settings clears 8.0** with vertical section nav, appearance cards, density radios, and localStorage notification prefs — **not** 8.2. Catalog request path gains real DynamicForm + toast deep-link without regressing the critical browse surface. Admin Metadata and Automation admin move from “dump pages” toward inspectable mock consoles. Critical five held. Multi-module 8h still loses to enterprise desks.

### Why PASS (checklist)

| Requirement | Result | Evidence |
|-------------|:------:|----------|
| No critical five regressions | **Yes** | Queues OperatorGrid, Overview, WorkItemDetail, Shell, Catalog browse intact in spot-check |
| S3b ModuleGrid closed | **Yes (CLOSED)** | `ModuleGrid.tsx` + Assets/Problems/Changes consumers; sticky / bulk / aria-sort / kbd / density |
| Settings ≥ 8.0 | **Yes (8.0)** | Section tabs, appearance cards, density, notif localStorage, focus rings |
| Catalog depth without regression | **Yes (hold 9.0)** | DynamicForm drawer + toast `Link` to work item; empty filtered states |
| Self-score honesty | **Mostly — inflation refused** | Settings self **8.2** → **8.0**; Metadata **7.7** → **7.5**; list craft 9.2/9.3 refused as overall gifts |
| Multi-module 8h honesty | **Enterprise still wins** | KB CMS, discovery, real automation engine, server prefs, BI depth unchanged |

### Why this is not a rubber stamp

1. **Settings self 8.2 is inflation.** Critic **8.0**. Notification prefs write `localStorage` and **nothing else** — no shell bell, no delivery path, no server. Profile is still read-only mock. Translation admin is a sample table. “Add language” is disabled theatre. Global **Save** toasts while theme/density/locale already apply live — Save is mostly notif re-persist cosplay. Appearance cards hardcode light surfaces with **no** `data-theme` dark/HC overrides (Reports got that craft in R9; Settings did not).
2. **List-craft self-scores must not inflate module totals.** Wave claims Assets list **9.2**, Problems **9.3**, Changes **9.1**. Critic accepts list craft ~**9.0–9.1** vs OperatorGrid reference. **Overall** Assets only **+0.1 → 9.0**. Problems / Changes **hold 9.1** — process preferred already; grid is shared chrome, not RCA/CAB depth.
3. **S3b closed ≠ OperatorGrid clone.** Space select only (OperatorGrid also binds **X**). No predicate / saved-view chrome. Mobile is horizontal min-width scroll, not OperatorGrid meta-line collapse. Wave doc is honest about X optional; critic **closes S3b** on shared grid residual, not “Queues parity forever.”
4. **Catalog drawer success state is dead code path.** `setDone(true)` then parent immediately unmounts drawer via `onCreated` — success panel rarely/never seen; toast carries the UX. Form depth is real; choreography is slightly sloppy.
5. **Automation is a pretty inspector, not an engine UI.** 3 seed rules, enable toggle (session), WHEN/IF/THEN read-only. No create, edit, test-fire, run log, or API. Score **7.3**, not 8.x.
6. **S6 Metadata crumb is CLOSED** (was open R9) — `/admin/metadata` and `/admin/automation` both in `AppShell` `crumbMap`. Good. Not a score gift beyond residual close.
7. **Open residuals hold:** S7 deep routes, S8 free-text asset assignee, S9 CAB quorum, S10 KB editorial, S11 notif center, S14/S15 Reports honesty, S16 bulk skip reasons.

**What FAIL would look like:** S3b still ModuleBulkBar-only with per-page tables; Settings still 7.x long form; critical regression on Queues/Catalog; self-scores claiming Settings 8.5+ / Assets 9.2 overall / bake-off flip.

---

## Wave 4 verification — live code

### S3b ModuleGrid — **CLOSED**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Shared ModuleGrid component | **PASS** | `components/modules/ModuleGrid.tsx` |
| Sticky header | **PASS** | `table-head--sticky table-head--module-grid` |
| Bulk checkboxes + select-all indeterminate | **PASS** | header checkbox + `indeterminate` ref |
| Sticky bulk bar | **PASS** | integrated `ModuleBulkBar` when `onBulkAssign` |
| `aria-sort` sortable columns | **PASS** | per-column `sortKey` + parent `onSort` |
| J/K · ↑↓ navigate | **PASS** | `onListKeyDown` |
| Space select focused row | **PASS** | Space → `toggleOne` |
| Enter open | **PASS** | Enter → `onRowOpen` |
| Ctrl/Cmd+A select all | **PASS** | meta/ctrl+a → `toggleAll` |
| Esc clear focus + selection | **PASS** | Escape clears both |
| Density compact / comfortable | **PASS** | `useDensity` + `.is-compact` / dense rows |
| Empty / loading / error slots | **PASS** | defaults + overrides |
| Assets consumer | **PASS** | columns tag·name·status·location·type·assignee |
| Problems consumer | **PASS** | number·title·status·priority·KE·incidents·assignee·updated |
| Changes consumer | **PASS** | number·title·type·status·risk·window·assignee; CAB/calendar above grid |
| X-select = Queues | **FAIL residual (accepted)** | ModuleGrid Space only; OperatorGrid Space **and** X |
| Predicate / saved views | **FAIL residual** | page filters only |
| Mobile meta-line collapse | **FAIL residual** | `min-width: 720px` horizontal scroll |

**Files:** `ModuleGrid.tsx`, `ModuleBulkBar.tsx`, Assets/Problems/Changes pages, `global.css` `.module-grid*`.

**Score impact:** S3b residual **CLOSED**. Assets overall **8.9 → 9.0**. Problems/Changes list craft uplift **does not** bump process preferred scores.

### Settings — **8.0 (self 8.2 refused)**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Section nav tabs | **PASS** | Profile · Language · Appearance · Notifications · API · Integrations · Demo |
| Vertical rail → horizontal wrap | **PASS** | `tabs--vertical` + `@media (max-width: 960px)` |
| Theme icon cards | **PASS** | Light / Dark / High contrast radios |
| Density radio cards | **PASS** | comfortable / compact (not bare toggle) |
| Notif prefs localStorage | **PASS craft / ding truth** | `vox-notification-prefs` auto-persist |
| Notif prefs affect product | **FAIL residual** | no consumer outside Settings |
| Focus rings | **PASS craft** | cards, tabs, density |
| Dark theme card tokens | **FAIL residual** | hardcoded light `#fff` / `#293148` on cards |
| Editable profile / server prefs | **FAIL** | mock read-only |
| Translation admin product | **FAIL** | sample rows + disabled add language |

**Files:** `pages/Settings/SettingsPage.tsx`, `global.css` settings/appearance blocks, i18n en/ru/de.

**R9 7.0 → R10 8.0.** Gate **≥8.0 PASS**. Self **8.2** refused: prefs theatre + light-locked cards + non-editable profile.

### Catalog request path — **depth held at surface 9.0**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| DynamicForm from work-item metadata | **PASS** | `fetchFormDefinition('work-item')` + `catalogFormDefinition` |
| Impact optional | **PASS** | required patched false + empty option |
| Service prefilled | **PASS** | values + option list |
| Toast → work item link | **PASS** | `useToast` action `{ label, href }` + `Link` |
| Empty filtered results | **PASS** | distinct copy + clear CTA + “still N services” hint |
| Multi-item cart / approval workflow UI | **FAIL residual** | approval flag display only |
| Drawer success panel | **Ding** | `done` set then drawer unmounted immediately |

**Files:** `CatalogPage.tsx`, `useToast.tsx`, CSS service-drawer form / toast link, i18n.

**Critical Catalog holds 9.0** — request path was the soft underbelly; form + deep-link toast close it without inventing portal suite. Wave self “request path 8.6” is dimensional; **do not** gift surface 9.1.

### Admin Metadata — **7.5 (self 7.7 refused)**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Object search/filter | **PASS** | key / labels / attribute keys |
| Dense attribute table + empties | **PASS** | `data-table--dense` + empty attrs/rels/filter |
| Workflow states mock | **PASS craft / ding depth** | enum pills from state/status — not a graph |
| Form preview read-only | **PASS** | `DynamicForm` modal when def exists |
| Editable metadata / real engine | **FAIL residual** | read-only mock objects |
| Crumb map (S6) | **CLOSED** | `AppShell` `/admin/metadata` |

**R9 6.7 → R10 7.5.** Target 7.5+ met. Self 7.7 refused: workflow strip is enum theatre; form preview only for objects that return a def.

### Admin Automation — **7.3 (new scored surface)**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Rule list + detail | **PASS** | two-pane inspector |
| WHEN / IF / THEN presentation | **PASS craft** | event · conditions · actions + JSON params |
| Enable toggle (session) | **PASS honest** | `setAutomationRuleEnabled` + mockHint / toggleHint |
| Create / edit / test / history | **FAIL** | not present |
| API-backed rules | **FAIL** | mock seed only (3 rules) |
| Demo reset wires rules | **PASS** | `resetDemoData` → `resetAutomationRules` |
| Crumb + sidebar | **PASS** | `/admin/automation` |

**Not suite automation.** Honest mock catalog browser with enable flips.

---

## Score table R9 → R10

| Surface | R7 | R8 | R9 | **R10** | Δ R9→R10 | Gate role | Notes |
|---------|---:|---:|---:|--------:|---------:|-----------|-------|
| **Overview** | 9.1 | 9.1 | 9.1 | **9.1** | 0 | Critical | Held |
| **MyWork** | 8.7 | 8.7 | 8.7 | **8.7** | 0 | — | Held |
| **Queues** | 9.1 | 9.1 | 9.1 | **9.1** | 0 | Critical | Still best surface; OperatorGrid reference |
| **Catalog** | 9.0 | 9.0 | 9.0 | **9.0** | 0 | Critical | Form + toast link; no 9.1 gift |
| **Knowledge** | 8.8 | 8.8 | 8.8 | **8.8** | 0 | Secondary AAA | No wave4 work |
| **CMDB** | 8.8 | 8.9 | 9.0 | **9.0** | 0 | Secondary AAA | Held process preferred |
| **Assets** | 8.7 | 8.9 | 8.9 | **9.0** | **+0.1** | Secondary AAA | ModuleGrid list craft; **not** 9.2 overall |
| **Problems** | 9.0 | 9.1 | 9.1 | **9.1** | 0 | Secondary AAA | Grid chrome only |
| **Changes** | 9.0 | 9.1 | 9.1 | **9.1** | 0 | Secondary AAA | Grid chrome only; CAB residuals hold |
| **Settings** | 7.0 | 7.0 | 7.0 | **8.0** | **+1.0** | Admin | Gate cleared; self 8.2 refused |
| **WorkItemDetail** | 9.0 | 9.0 | 9.0 | **9.0** | 0 | Critical | Held |
| **Shell** | 9.1 | 9.1 | 9.1 | **9.1** | 0 | Critical | S6 crumbs fixed; S11 still open |
| **Reports** | 7.3 | 8.1 | 8.5 | **8.5** | 0 | Ops | Held |
| **Admin Metadata** | 6.7 | 6.7 | 6.7 | **7.5** | **+0.8** | Admin | Filter + dense + workflow + form preview |
| **Admin Automation** | — | — | — | **7.3** | new | Admin | Mock WHEN/IF/THEN inspector |

### List craft (dimensional — not surface totals)

| Module | Wave self | **R10 critic list craft** | Inflated? |
|--------|----------:|--------------------------:|:---------:|
| Assets | **9.2** | **9.0** | **Yes (−0.2)** — no X, no predicates, mobile scroll tax |
| Problems | **9.3** | **9.1** | **Yes (−0.2)** — richer columns ≠ 9.3 vs OperatorGrid |
| Changes | **9.1** | **9.0** | **Yes (−0.1)** — grid solid; CAB/calendar separate |

### Aggregates

| Aggregate | R9 | **R10** | Δ |
|-----------|---:|--------:|--:|
| Average (prior 14 surfaces) | ~8.74 | **~8.85** | **+0.11** |
| Average (critical five) | ~9.06 | **~9.06** | 0 |
| Average (secondary five*) | ~9.0 | **~9.0** | Assets +0.1 only |
| Secondary five min | 8.8 | **8.8** | Knowledge floor |
| Secondary preferred (≥9 count) | 3/5 | **4/5** | +Assets preferred on **list-ops path** (not process suite) |
| Settings gate ≥8.0 | Fail (7.0) | **Pass (8.0)** | |
| S3b | Partial | **CLOSED** | |

\*Assets, Problems, Changes, CMDB, Knowledge

### Secondary AAA checklist

| Module | R10 | ≥8.5? | ≥9 preferred? |
|--------|----:|:-----:|:-------------:|
| Assets | **9.0** | Yes | **Yes (list-ops craft preferred)** |
| Problems | **9.1** | Yes | **Yes (process)** |
| Changes | **9.1** | Yes | **Yes (process + light ops)** |
| CMDB | **9.0** | Yes | **Yes (relation-ops process)** |
| Knowledge | **8.8** | Yes | No |
| **Suite secondary AAA** | — | **PASS** | **PASS (4 preferred; Knowledge still under)** |

### Critical five hold

| Surface | R10 | ≥9? |
|---------|----:|:---:|
| Overview | 9.1 | Yes |
| Queues | 9.1 | Yes |
| WorkItemDetail | 9.0 | Yes |
| Shell | 9.1 | Yes |
| Catalog | 9.0 | Yes |

---

## Self-score honesty (wave docs vs critic)

| Claim | Wave self | **R10 critic** | Inflated? |
|-------|----------:|---------------:|:---------:|
| Settings | **8.2** | **8.0** | **Yes (−0.2)** — gate met, ceiling not 8.2 |
| Admin Metadata | **7.7** | **7.5** | **Yes (−0.2)** — real lift, enum workflow ≠ product |
| Catalog request path | **8.6** | dimensional OK / surface **9.0 hold** | Acceptable path score; refuse surface +0.1 |
| Assets list craft | **9.2** | **9.0** list / **9.0** overall | **Yes** on list self; overall only +0.1 from 8.9 |
| Problems list craft | **9.3** | **9.1** list / **9.1** overall | **Yes** on list self |
| Changes list craft | **9.1** | **9.0** list / **9.1** overall | Mild list overclaim |
| S3b closed | closed | **CLOSED** | Honest — with documented residuals |
| ModuleGrid = OperatorGrid predicates | not claimed | **Correct non-claim** | Good |

Inflation discipline enforced: **no surface self-score above critic by >0.2 accepted without cut.** Settings and Metadata both hit the 0.2 refusal line.

---

## Blind A/B — unlabeled operator UX

Compare **Vox** vs latest **Naumen ITSM** / **ServiceNow Agent Workspace** class desks without brand labels.

### (a) L1 triage — 2-hour shift

**Task:** Claim queue, sort by SLA urgency, work breached → at-risk → unassigned, brief assist, open detail, update fields, notifications. Optional catalog request mid-shift.

| Dimension | Winner | Why |
|-----------|--------|-----|
| Queue scan density + bulk | **Vox** | OperatorGrid still cleaner |
| Live SLA urgency | **Vox slight** | Unchanged |
| Workbench field edit | **Tie / Vox polish** | DynamicForm craft vs enterprise rule depth |
| Catalog request mid-shift | **Tie / Vox improved** | Full form + toast deep-link; enterprise portal still deeper for multi-step |
| Notification interrupt | **Enterprise slight** | Prefs localStorage ≠ center (S11) |
| **2h L1 desk pick** | **Vox** | Wave4 does not hurt L1; slight catalog assist |

**Honest summary (2h):** Unlabeled L1 still **picks Vox**.

### (b) Multi-module — 8-hour full desk

**Task:** Queues + incidents + problems (RCA/KE) + normal change through CAB + ModuleGrid bulk on Assets/Problems/Changes + knowledge + CMDB + assets + Reports + Settings prefs + Automation inspect + Catalog request + refresh mid-shift.

| Dimension | Winner | Why |
|-----------|--------|-----|
| Premium polish | **Vox** | System chrome still ahead |
| L1 queue path | **Vox** | Same as (a) |
| Secondary list bulk | **Vox competitive / slight Vox** | Shared ModuleGrid finally real; still no predicate chrome |
| Change / CAB process | **Tie / Enterprise slight** | Honest bulk gates (R9); still no quorum / full calendar |
| Knowledge authoring | **Enterprise** | Votes ≠ CMS |
| CMDB / relations | **Enterprise (narrower)** | Vox relation-ops; no discovery |
| Settings / admin prefs | **Enterprise** | Vox organized shell; server prefs + real notif routing win 8h |
| Automation rules | **Enterprise** | Vox mock inspector vs Flow Designer / real rule engines |
| Reports / export | **Enterprise (narrower)** | Vox 8.5 printable snapshot |
| Hierarchy / deep links | **Enterprise slight** | S7 still open |
| **8h multi-module desk pick** | **Enterprise desks** | Margin **narrower than R9** (ModuleGrid + Settings IA); **not flipped** |

### Which looks better unlabeled

| Scenario | Blind winner | Confidence |
|----------|--------------|------------|
| **(a) L1 triage 2h** | **Vox** | High |
| **(b) Multi-module 8h** | **Enterprise (Naumen / ServiceNow Workspace class)** | High — margin reduced by ModuleGrid + Settings 8.0 + Catalog form |
| **Guided secondary walkthrough** | **Vox competitive / demo-tie** | Medium-high |
| **Polish-only screenshot A/B** | **Vox** | High |
| **Admin metadata + automation 45m** | **Enterprise** | High — Vox is mock-honest; enterprise owns schema + flow products |
| **Settings prefs 20m** | **Tie / Enterprise slight** | Medium — Vox IA is clean; prefs that don’t route notifications lose trust |

**Why enterprise still wins 8h:** Knowledge workflow, discovery-fed CMDB, multi-week change calendars, real reporting authority, multi-user servers, real automation execution, server-backed notification prefs. Wave4 closed **list craft** and **admin IA** gaps; it did not invent suite depth.

---

## Per-surface harsh notes (focus + hold)

### Settings — **8.0** (was 7.0) ★ largest absolute lift this wave

**Credit:**  
- Sectioned nav ends the “one long settings wall” insult.  
- Appearance theme + density as cards is real craft, not a lonely toggle.  
- Auto-persist note is honest about localStorage.  
- Focus rings on interactive cards meet the keyboard bar claimed.

**Ding (why not 8.1 / 8.2+):**  
- Self **8.2** refused hard.  
- Notification toggles do not wire to `NotificationMenu`, desktop permission, or backend.  
- Profile non-editable; OIDC block is status-only.  
- Translation admin sample + disabled “Add language”.  
- Appearance CSS is light-theme-locked (Reports got dark overrides; Settings appearance did not).  
- Tabs are click-only; no arrow-key tablist pattern beyond basic buttons.  
- Global Save implies multi-section persistence that mostly isn’t multi-section.

**8.0 means:** organized, focusable, demo-credible preferences shell that clears the gate. **Not** “enterprise user administration.”

### ModuleGrid / secondary lists — S3b **CLOSED**

**Credit:** One component, three consumers, OperatorGrid-class kbd and bulk. This is the residual R9 said would fail if falsely claimed closed. Wave delivered the actual grid.

**Ding:** Space≠X dual; no predicates; mobile min-width; page-level hard errors still full-page outside grid slots. **Assets 9.0 preferred is list-ops craft**, not asset lifecycle suite (S8 free-text assignee still open).

### Catalog — **9.0** hold

**Credit:** DynamicForm in drawer, optional impact, toast deep-link, filtered empty polish. Request path was the residual risk under a 9.0 critical score.

**Ding:** No cart, no approval chain UI, drawer success state race. Refuse 9.1.

### Admin Metadata — **7.5** / Automation — **7.3**

**Credit:** Search, density, workflow strip, form preview, automation WHEN/IF/THEN with honest mock labels.

**Ding:** Read-only. No engine. No schema edit. Automation cannot create rules. Self Metadata 7.7 refused.

### Critical path + held secondaries

| Check | Result |
|-------|:------:|
| Queues OperatorGrid + bulk + predicates | **HOLD** |
| Overview live metrics + copilot | **HOLD** |
| WorkItemDetail workbench + DynamicForm | **HOLD** |
| Shell notifications UI depth (S11) | **HOLD residual** |
| Catalog browse polish | **HOLD** |
| Reports 8.5 honesty | **HOLD** |
| CMDB 9.0 relation-ops | **HOLD** |
| Knowledge 8.8 | **HOLD** |
| Changes CAB / bulk gates (S13) | **HOLD** |
| S6 metadata crumb | **CLOSED** |
| C1 type floor | **HOLD** |

---

## Critical / craft defect register

### C1–C8 class

**None new. None reopened.**

### Residual register

| ID | R9 | R10 |
|----|----|-----|
| **S3** Secondary bulk class | CLOSED | **CLOSED** |
| **S3b** ModuleGrid / OperatorGrid list craft | Partial | **CLOSED** (shared grid + kbd + bulk + aria-sort; X dual + predicates remain optional polish, not open S3b) |
| **S4** CAB silent approve | CLOSED | **CLOSED** |
| **S5** Knowledge toast theatre | CLOSED | **CLOSED** |
| **S6** Metadata crumb | Open | **CLOSED** |
| **S7** Deep-link module detail routes | Open | **Open** |
| **S8** Asset free-text assignee | Open | **Open** |
| **S9** CAB quorum / full calendar | Partial | **Partial** |
| **S10** KB editorial queue / use-in-ticket | Open | **Open** |
| **S11** Notification center depth | Open | **Open** (prefs localStorage ≠ center) |
| **S12** Conflict raw CI ids | CLOSED | **CLOSED** |
| **S13** Bulk change plan/backout | CLOSED | **CLOSED** |
| **S14** Reports CSAT not filtered-set truth | Open P2 | **Open P2** |
| **S15** Reports SLA history filter-blind | Open P2 | **Open P2** |
| **S16** Bulk skip: no per-row block reasons | Open P2 | **Open P2** |
| **S17** (new) Settings notif prefs have no product consumer | — | **Open P2** |
| **S18** (new) Settings appearance cards lack dark/HC token overrides | — | **Open P2** |
| **S19** (new) Catalog drawer success UI unmounted before view | — | **Open P3** |

---

## Remaining backlog (post-PASS → multi-module *tie* closer)

### P1 — bake-off margin

1. Knowledge: change-vote / use-in-ticket / pending review queue (**S10**).  
2. CAB quorum (≥1 member vote before chair approve) (**S9**).  
3. Notification center route; wire Settings prefs + backend events (**S11** + **S17**).  
4. Problem/Change reassignment in drawer (not only bulk).  
5. Optional ModuleGrid polish: accept **X** as select alias (match OperatorGrid dual bind).

### P2 — polish

6. Routes `/problems/:id`, `/changes/:id`, `/assets/:id` (**S7**).  
7. Asset assignee picker not free-text (**S8**).  
8. Reports: queue + date-range; CSAT honesty (**S14**); SLA history filter scope (**S15**).  
9. Bulk status: “skipped N: reason” (**S16**).  
10. Settings appearance dark/HC tokens (**S18**).  
11. Catalog: keep drawer open on success *or* drop dead `done` branch (**S19**).  
12. Visual regression CI per `quality-gates.md`.

---

## What improved R9 → R10 (credit — real)

- **S3b closed** — shared `ModuleGrid` on Assets / Problems / Changes.  
- **Settings 7.0 → 8.0** — section nav, appearance cards, density, notif localStorage, focus.  
- **Admin Metadata 6.7 → 7.5** — filter, dense tables, workflow strip, form preview.  
- **Admin Automation scored 7.3** — mock WHEN/IF/THEN inspector + session enable.  
- **Catalog request form + toast deep-link** under held 9.0 critical.  
- **S6 crumb closed** for metadata (+ automation).  
- **Secondary preferred count 3 → 4** (Assets joins on list-ops craft preferred).  
- **Critical five held ≥9** with zero regression found in spot-check.

PASS is “S3b closed + Settings ≥8.0 + no critical regression + inflation refused.”  
PASS is **not** “Vox wins unlabeled 8h multi-module desk.”

---

## Viewport notes (R10)

| Viewport | Assessment |
|----------|------------|
| **1440** | Settings two-column shell solid; ModuleGrid sticky panel OK; Automation two-pane readable |
| **1024** | Settings may still show vertical tabs; ModuleGrid columns dense but usable |
| **768** | Settings nav stacks horizontal; ModuleGrid horizontal scroll (min 720px); Automation stacks |
| **320** | Settings cards stack; ModuleGrid painful scroll; Catalog form drawer full-width OK; Automation list-first |

---

## Final call

| Gate | R9 | **R10** |
|------|----|---------|
| Critical surfaces ≥9 | **PASS** | **PASS (held)** |
| No C1–C8 critical defects | **PASS** | **PASS** |
| Secondary five each ≥8.5 | **PASS** | **PASS** (min 8.8) |
| Secondary preferred ≥9 | **PASS (3)** | **PASS (4: Assets + Problems + Changes + CMDB)** |
| S3b ModuleGrid | Partial | **CLOSED** |
| Settings ≥8.0 | Fail (7.0) | **PASS (8.0)** |
| Catalog critical hold | 9.0 | **PASS (9.0)** |
| Blind A/B L1 2h | **Vox wins** | **Vox wins** |
| Blind A/B multi-module 8h | Enterprise wins | **Enterprise still wins (narrower)** |
| Self-score honesty | Mostly (Reports −0.1) | **Harsh cuts: Settings −0.2, Metadata −0.2, list craft −0.1–0.2** |
| **Elevation verdict** | R9 PASS | **PASS (Wave 4 real; inflation refused)** |

---

### PASS/FAIL + scores (executive)

**Verdict: PASS** — S3b CLOSED; Settings **8.0** (self 8.2 refused); no critical five regressions; Catalog hold **9.0**; Assets **9.0**; Metadata **7.5**; Automation **7.3**; multi-module 8h still enterprise.

| Secondary (gate set) | R9 | **R10** | ≥8.5 | ≥9 |
|----------------------|---:|--------:|:----:|:--:|
| Assets | 8.9 | **9.0** | Yes | **Yes (list-ops)** |
| Problems | 9.1 | **9.1** | Yes | **Yes** |
| Changes | 9.1 | **9.1** | Yes | **Yes** |
| CMDB | 9.0 | **9.0** | Yes | **Yes (process)** |
| Knowledge | 8.8 | **8.8** | Yes | No |
| **Secondary average** | ~9.0 | **~9.0** | Pass | **4/5 preferred** |

| Critical five | R10 |
|---------------|----:|
| Overview | **9.1** |
| Queues | **9.1** |
| WorkItemDetail | **9.0** |
| Shell | **9.1** |
| Catalog | **9.0** |
| **Critical average** | **~9.06** |

| Other | R9 | **R10** |
|-------|---:|--------:|
| MyWork | 8.7 | **8.7** |
| Reports | 8.5 | **8.5** |
| Settings | 7.0 | **8.0** |
| Admin Metadata | 6.7 | **7.5** |
| Admin Automation | — | **7.3** |

### Blind winners (unlabeled)

| Scenario | Winner |
|----------|--------|
| **L1 triage 2h** | **Vox** |
| **Multi-module 8h** | **Enterprise (Naumen / ServiceNow Workspace class)** |
| **Polish screenshot** | **Vox** |
| **Guided ModuleGrid + Catalog form + Settings tabs** | **Vox competitive / demo-tie** |

**Can Vox win multi-module desk?**  
**No.** Closer than R9: shared ModuleGrid, Settings gate, Catalog form depth, admin inspectors. Enterprise still takes the unlabeled 8-hour multi-module shift.

**Which looks better unlabeled?**  
- **2h L1:** **Vox**.  
- **8h multi-module:** **Enterprise**.  
- **Screenshot polish:** **Vox**.

**Why PASS is honest:** ModuleGrid is wired in three live consumers with verified kbd/bulk/aria-sort; Settings meets 8.0 without gift to 8.2; critic refused list-craft laundering and Metadata overclaim; bake-off flip refused.
