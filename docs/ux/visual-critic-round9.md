# Vox ITSM — Visual / Operator UX Critic Round 9 (Wave 3: bulk gates · human CI labels · ModuleBulkBar · Reports 8.5+ · CMDB 9.0)

**Date:** 2026-07-31  
**Scope:** Live code under `G:\ITSM\frontend\src` after R8 PASS + Wave 3 (`docs/ux/wave3-r8-fixes.md`, `docs/ux/wave3-reports-cmdb.md`).  
**Inputs:** `docs/ux/visual-critic-round8.md`, wave3 docs, live pages/store/components/i18n/CSS. Backend notification wiring noted only (not a surface score gift).

**Focus this wave:**  
S13 Changes bulk = single transition gates · S12 conflict human CI names · S3b partial ModuleBulkBar · Reports honesty/load/compliance/print/dark · CMDB full rel set + type edit + focus + CSV + legend  

**Regression scan:** Overview · Queues · WorkItemDetail · Shell · Catalog · Knowledge · Problems process · Changes CAB · Assets bulk  

**Viewports mentally reviewed:** 1440 · 1024 · 768 · 320

**Elevation bar (this round — non-negotiable):**  
- **No regressions** on critical five (≥ 9.0) or secondary process paths (Problems RCA/KE, Changes CAB, KB votes).  
- **R8 residuals addressed:** S13 closed, S12 closed, S3b at least partially closed as claimed (not silently “done”).  
- **Reports ≥ 8.5** as honest ops console — not BI product; refuse 8.6+ gift if residuals remain.  
- **CMDB ≥ 8.9** (wave self 9.0). Preferring 9.0 only if relation-ops path is complete; refuse suite inflation.  
- Self-score inflation ≤ 0.2 vs critic.  
- **No** multi-module bake-off victory claim.

---

## Verdict: **PASS**

Wave 3 is **real residual work**, not score cosplay. S13 is closed in store code with shared helpers — bulk no longer secretly weaker than drawer schedule. S12 conflict banner renders human CI names via `resolveRelatedLabel`. ModuleBulkBar / ModuleKbdHint extract shared chrome without claiming Queues OperatorGrid parity. Reports clears the **8.5** gate with honest synthetic policy, assignee load, SLA compliance KPI, print CSS, and dark contrast — **not** 8.6. CMDB earns **9.0 process-path preferred** on full relation vocabulary + type edit + focus + export + text legend — **not** discovery suite. Critical path held. Multi-module 8h still loses to enterprise desks.

### Why PASS (checklist)

| Requirement | Result | Evidence |
|-------------|:------:|----------|
| No critical / process regressions | **Yes** | Queues OperatorGrid, CAB chair API, RCA bulk skip, KB surfaces, durable store paths intact in spot-check |
| S13 bulk = single gates | **Yes (CLOSED)** | `changeTransitionBlockReason` shared by `transitionChange` + `bulkSetChangeStatus` (plan + backout + cabRejected + NORMAL cabApproved + draft→scheduled block) |
| S12 human CI conflict labels | **Yes (CLOSED)** | `changes.conflict.cis` + `resolveRelatedLabel` → e.g. **PostgreSQL Cluster**, not `ci-pg-cluster` |
| S3b shared bulk chrome | **Partial (as claimed)** | `ModuleBulkBar` + `ModuleKbdHint` on Assets/Problems/Changes; tables still module-local; Space ≠ Queues X |
| Reports ≥ 8.5 | **Yes (8.5)** | Store-only trend when any real day; synthetic only empty week + banner/hatch; assignee top-5; SLA % labeled; print + dark |
| CMDB ≥ 8.9 | **Yes (9.0 process)** | Full `EDITABLE_REL_TYPES`; `updateCiRelation`; dblclick focus; CI CSV; text+swatch health legend |
| Self-score honesty | **Mostly** | Reports self **8.6** over-claims by **0.1** (critic **8.5**); CMDB self **9.0** accepted only as process-path |
| Multi-module 8h honesty | **Enterprise still wins** | Residual depth (KB CMS, discovery, CAB product, BI) unchanged |

### Why this is not a rubber stamp

1. **Reports self 8.6 is mild inflation.** Critic **8.5**. Filters still only type + priority. CSAT still `metrics.satisfaction` mock (seed **96%**), not filtered-set truth. SLA “history” path reads **global** `seedActivities`, not the filtered work-item list — labeled mock, still filter-blind. No date range, no queue filter, no scheduled delivery. Print is `window.print()` + CSS, not PDF/export product.
2. **CMDB 9.0 is process-path, not suite.** R8 said 9.0 needed broader lifecycle/class craft “not one form.” Wave3 is more than one form (vocab + type edit + CSV + legend + focus) and **closes the type-subset residual**, so preferred **relation-ops** bar is reachable. It is still **not** discovery, reconciliation, CI class model, multi-map, or force-directed layout. Do not score 9.2.
3. **S3b is partial — wave honesty is correct; residual remains.** ModuleBulkBar is chrome extraction (~80 LOC shared). No shared ModuleGrid row model, no sticky virtual head, select key still Space vs OperatorGrid X. Calling S3b “closed” would fail this round.
4. **Bulk skip UX is silent.** `bulkSetChangeStatus` skips blocked rows and returns `n` of successes — correct honesty on count — but operator gets no per-row “why skipped” list when n < selection. Acceptable residual; not a score gift either way.
5. **CAB quorum still open (S9).** Chair can still approve without member votes; two hard-coded seats. Wave3 did not touch this. Changes holds **9.1**, not 9.2.
6. **Backend notifications exist** (`NotificationService` on assign/transition, `InMemoryNotificationStore`) — engineering depth. Frontend shell still a shallow bell (S11). **No Shell +0.x.**
7. **S6 Metadata crumb still missing** — `/admin/metadata` absent from `crumbMap` in `AppShell.tsx`.

**What FAIL would look like:** bulk still skipping only CAB but not plan/backout; conflict banner still printing `ci-*`; Reports still 8.1 with always-on synthetic mix; CMDB form still missing `uses`/`connects_to`; critical regression; self-scores claiming Reports 9.x / CMDB suite / bake-off flip.

---

## Wave 3 verification — live code

### S13 Changes bulk policy — **CLOSED**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Shared block helper | **PASS** | `changeTransitionBlockReason` in `mock/store.ts` |
| → scheduled needs plan | **PASS** | `implementationPlan` trim check |
| → scheduled needs backout | **PASS** | `backoutPlan` trim check |
| cabRejected blocks schedule | **PASS** | returns `changes.validation.cabRejected` |
| NORMAL needs cabApproved | **PASS** | `cabApprovalRequired` |
| NORMAL draft → scheduled blocked | **PASS** | `cabRequired` |
| → cab_review needs plan | **PASS** | same as single |
| bulk uses same helper | **PASS** | `if (changeTransitionBlockReason(c, next)) return c` |
| toast count = actual n | **PASS** | only successful apply increments `n` |
| Single path unchanged | **PASS** | `transitionChange` → same helper → `applyChangeStatus` |

**Files:** `frontend/src/mock/store.ts` (~1266–1424), `api/changes.ts`, `pages/Changes/ChangesPage.tsx` bulk handler.

**Score impact:** Honesty ding from R8 removed. **Does not** invent CAB suite — **Changes holds 9.1**.

### S12 Conflict human CI labels — **CLOSED**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Banner uses human names | **PASS** | `t('changes.conflict.cis', { names: c.ciIds.map(resolveRelatedLabel).join(', ') })` |
| Seed CI resolves | **PASS** | `ci-pg-cluster` → **PostgreSQL Cluster** via `getConfigurationItem` |
| Soft fallback if unknown | **PASS** | `ci-*` → `CI · …` (not bare id in happy path) |
| Heuristic unchanged | **PASS** | same-day NORMAL ∩ related CIs |

**Files:** `ChangesPage.tsx` conflict list; `lib/resolveRelated.ts`; i18n `changes.conflict.cis` en/ru/de.

### S3b ModuleBulkBar — **PARTIAL (honest)**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Shared sticky bulk bar | **PASS** | `ModuleBulkBar.tsx` → `.bulk-bar` sticky under header |
| Assign / clear / status slot | **PASS** | Assets / Problems / Changes wired |
| Shared kbd hint + Esc clear | **PASS** | `ModuleKbdHint` documents Esc (`grid.kbdClear`) |
| Full ModuleGrid / OperatorGrid parity | **FAIL residual** | Per-page tables remain; no X-select; no predicate chrome |

**Files:** `components/modules/ModuleBulkBar.tsx`; Assets/Problems/Changes pages.

### Reports ops console — **8.5 earned (not 8.6)**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Store-only trend when any real day | **PASS** | `buildTrend`: `hasStoreActivity` short-circuits synthetic |
| Synthetic only empty week + labeled | **PASS** | banner + `*` tag + hatched `.is-synthetic` |
| Never mix synthetic with real days | **PASS** | all-or-nothing policy (stronger than R8 per-day fill) |
| Assignee load top 5 | **PASS** | filtered set, horizontal bars |
| SLA compliance % mock | **PASS / ding** | history from **seed** activities OR slaState snapshot; **labeled**; history **filter-blind** |
| Print-friendly | **PASS craft** | Print button + `@media print` hide shell/chrome, stamp title |
| Dark/HC chart contrast | **PASS craft** | theme overrides tracks/fills/KPI/banner |
| CSV export filtered | **HOLD** | unchanged wave2 utility |
| Type + priority filters | **HOLD** | still only two axes |
| CSAT from filtered truth | **FAIL residual** | still dashboard `satisfaction` mock |
| Date range / queue / saved report | **FAIL** | out of scope; caps score |

**Files:** `pages/Reports/ReportsPage.tsx`, `styles/global.css` (`.reports-*` + print), i18n `reports.*`.

**R8 8.1 → R9 8.5.** Gate **≥8.5 PASS**. Self **8.6** refused: residual filter thinness + CSAT theatre + filter-blind SLA history keep the ceiling honest.

### CMDB relation-ops preferred — **9.0 process (not suite)**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Full form type set | **PASS** | `depends_on`, `hosted_on`, `runs_on`, `connects_to`, `uses` |
| `hosts` → `hosted_on` normalize | **PASS** | store + display |
| Edit relation type | **PASS** | `updateCiRelation` + inline Edit type / dblclick row |
| Dblclick graph → focus detail | **PASS** | `onFocus` / `focusCi` + `detailRef` scroll |
| Export CI CSV | **PASS** | filtered or full → `itsm-cmdb-cis-YYYY-MM-DD.csv` |
| Health legend text + swatch | **PASS** | `map-footer--legend` operational / degraded·maintenance / retired |
| Impact rebinds live edges | **HOLD** | BFS selection-aware (R8) |
| Discovery / class model / multi-map | **FAIL residual** | fixed layout; users heuristic remains |

**Files:** `pages/CMDB/CmdbPage.tsx`, `mock/store.ts` `updateCiRelation`, `api/cmdb.ts`, types `hosted_on`, CSS legend, i18n.

**R8 8.9 → R9 9.0.** Preferred **relation-ops path**, same honesty frame as Problems process preferred — **not** enterprise CMDB suite.

### Backend notifications — **ENGINEERING HOLD**

| Claim | Verdict | Note |
|-------|:-------:|------|
| Server-side notify on assign/transition | **Present** | `AssignWorkItem` / `TransitionWorkItem` + `NotificationService` |
| In-memory demo store | **Present** | `InMemoryNotificationStore` |
| Frontend center depth (S11) | **Open** | Shell bell residual; **no Shell score lift** |

---

## Score table R8 → R9

| Surface | R6 | R7 | R8 | **R9** | Δ R8→R9 | Gate role | Notes |
|---------|---:|---:|---:|-------:|--------:|-----------|-------|
| **Overview** | 9.0 | 9.1 | 9.1 | **9.1** | 0 | Critical | Held |
| **MyWork** | 8.7 | 8.7 | 8.7 | **8.7** | 0 | — | Held |
| **Queues** | 9.1 | 9.1 | 9.1 | **9.1** | 0 | Critical | Still best surface |
| **Catalog** | 9.0 | 9.0 | 9.0 | **9.0** | 0 | Critical | Held |
| **Knowledge** | 8.5 | 8.8 | 8.8 | **8.8** | 0 | Secondary AAA | No wave3 work |
| **CMDB** | 8.8 | 8.8 | 8.9 | **9.0** | **+0.1** | Secondary AAA | Full rel vocab + type edit + CSV + legend + focus |
| **Assets** | 8.7 | 8.7 | 8.9 | **8.9** | 0 | Secondary AAA | ModuleBulkBar only — no surface inflation |
| **Problems** | 9.0 | 9.0 | 9.1 | **9.1** | 0 | Secondary AAA | ModuleBulkBar only |
| **Changes** | 8.8 | 9.0 | 9.1 | **9.1** | 0 | Secondary AAA | S13/S12 honesty closes; **not** +0.1 suite gift |
| **Settings** | 6.8 | 7.0 | 7.0 | **7.0** | 0 | — | Held |
| **WorkItemDetail** | 9.0 | 9.0 | 9.0 | **9.0** | 0 | Critical | Held |
| **Shell** | 9.0 | 9.1 | 9.1 | **9.1** | 0 | Critical | Backend notifs ≠ shell center |
| **Reports** | 7.3 | 7.3 | 8.1 | **8.5** | **+0.4** | Ops | Gate cleared; self 8.6 refused |
| **Admin Metadata** | 6.7 | 6.7 | 6.7 | **6.7** | 0 | — | crumbMap still missing (S6) |

### Aggregates

| Aggregate | R8 | **R9** | Δ |
|-----------|---:|-------:|--:|
| Average (all scored) | ~8.7 | **~8.74** | **+0.04** |
| Average (critical five) | ~9.06 | **~9.06** | 0 |
| Average (secondary five*) | ~9.0 | **~9.0** | ~0 (CMDB +0.1 only) |
| Secondary five min | 8.8 | **8.8** | Knowledge floor |
| Secondary preferred (≥9 count) | 2/5 | **3/5** | Problems + Changes + **CMDB** |
| Reports gate ≥8.5 | Fail (8.1) | **Pass (8.5)** | |

\*Assets, Problems, Changes, CMDB, Knowledge

### Secondary AAA checklist

| Module | R9 | ≥8.5? | ≥9 preferred? |
|--------|---:|:-----:|:-------------:|
| Assets | **8.9** | Yes | No |
| Problems | **9.1** | Yes | **Yes** (process) |
| Changes | **9.1** | Yes | **Yes** (process + light ops) |
| CMDB | **9.0** | Yes | **Yes (relation-ops process)** |
| Knowledge | **8.8** | Yes | No |
| **Suite secondary AAA** | — | **PASS** | **PASS (3 preferred; Knowledge still under)** |

### Critical five hold

| Surface | R9 | ≥9? |
|---------|---:|:---:|
| Overview | 9.1 | Yes |
| Queues | 9.1 | Yes |
| WorkItemDetail | 9.0 | Yes |
| Shell | 9.1 | Yes |
| Catalog | 9.0 | Yes |

---

## Self-score honesty (wave docs vs critic)

| Claim | Wave self | **R9 critic** | Inflated? |
|-------|----------:|--------------:|:---------:|
| Reports | **8.6** | **8.5** | **Yes (−0.1)** — real lift, gate cleared, ceiling not 8.6 |
| CMDB | **9.0** | **9.0 process** | Acceptable **only** with process-path frame; suite claim would fail |
| S13 closed | closed | **CLOSED** | Honest |
| S12 closed | closed | **CLOSED** | Honest |
| S3b partial | partial | **Partial** | Honest — wave prose disciplined |
| ModuleBulkBar = OperatorGrid | not claimed | **Correct non-claim** | Good |
| Bulk schedule policy parity | claimed | **Verified** | Fixes R8 honesty ding |

No surface self-score above critic by >0.2. Reports is the only soft overclaim.

---

## Blind A/B — unlabeled operator UX

Compare **Vox** vs latest **Naumen ITSM** / **ServiceNow Agent Workspace** class desks without brand labels.

### (a) L1 triage — 2-hour shift

**Task:** Claim queue, sort by SLA urgency, work breached → at-risk → unassigned, brief assist, open detail, update fields, notifications. Secondary optional.

| Dimension | Winner | Why |
|-----------|--------|-----|
| Queue scan density + bulk | **Vox** | OperatorGrid still cleaner than typical enterprise list chrome |
| Live SLA urgency | **Vox slight** | Unchanged from R7/R8 |
| Workbench field edit | **Tie / Vox polish** | DynamicForm craft vs enterprise rule depth |
| Notification interrupt | **Enterprise slight** | Backend path exists; UI center still shallow (S11) |
| Reports glance mid-shift | **Tie / Vox improved** | Print + honest trend + load bars; enterprise PA still deeper |
| **2h L1 desk pick** | **Vox** | Wave3 does not hurt L1 |

**Honest summary (2h):** Unlabeled L1 still **picks Vox**.

### (b) Multi-module — 8-hour full desk

**Task:** Queues + incidents + problems (RCA/KE) + normal change through CAB (bulk schedule honesty) + conflict banner with human CI names + knowledge + CMDB full relation edit/type + impact + assets bulk + Reports print/CSV + refresh mid-shift.

| Dimension | Winner | Why |
|-----------|--------|-----|
| Premium polish | **Vox** | System chrome still ahead |
| L1 queue path | **Vox** | Same as (a) |
| Secondary list bulk | **Tie / Vox competitive** | Shared ModuleBulkBar; still not ModuleGrid |
| Change / CAB process | **Tie / Enterprise slight** | Bulk gates now honest; still no quorum / full calendar / freeze windows from CMDB |
| Knowledge authoring | **Enterprise** | Votes ≠ CMS |
| CMDB / relations | **Enterprise (narrower)** | Vox full vocab + type edit; still no discovery product |
| Reports / export / print | **Enterprise (narrower)** | Vox is a usable printable ops snapshot at 8.5; not PA warehouse |
| Hierarchy / deep links | **Enterprise slight** | S7 still open |
| **8h multi-module desk pick** | **Enterprise desks** | Margin **slightly narrower than R8**; still not flipped |

### Which looks better unlabeled

| Scenario | Blind winner | Confidence |
|----------|--------------|------------|
| **(a) L1 triage 2h** | **Vox** | High |
| **(b) Multi-module 8h** | **Enterprise (Naumen / ServiceNow Workspace class)** | High — margin reduced by bulk honesty + CMDB 9.0 process + Reports 8.5 |
| **Guided secondary walkthrough** | **Vox competitive / demo-tie** | Medium-high |
| **Polish-only screenshot A/B** | **Vox** | High |
| **Change manager CAB prep 90m** | **Tie / Enterprise slight** | Medium — human CI conflict + honest bulk schedule; still not CAB product |
| **CMDB relation-ops 45m** | **Tie / Vox competitive** | Medium — full type edit path is credible; discovery still decides enterprise win on full CMDB shift |

**Why enterprise still wins 8h:** Knowledge workflow, discovery-fed CMDB, multi-week change calendars, real reporting authority, multi-user servers. Wave3 closed residual **honesty** and **relation vocabulary** gaps; it did not invent suite depth.

---

## Per-surface harsh notes (focus + hold)

### Reports — **8.5** (was 8.1) ★ largest lift this wave

**Credit:**  
- Synthetic fill is no longer mixed with real store days — the single biggest honesty upgrade from R8.  
- Assignee load is operator-useful for stand-up staffing glance.  
- SLA compliance KPI with source line is better than a naked percentage.  
- Print path + dark contrast are real craft, not wallpaper.

**Ding (why not 8.6 / 8.7+):**  
- Self-score **8.6** refused.  
- CSAT metric still dashboard mock, not filtered work-item truth.  
- History SLA path is seed-global, ignores type/priority filters.  
- Only two filter axes; no date range; no queue; no saved report; no multi-period.  
- MTTR still `updatedAt − createdAt` mock.  
- Empty-week synthetic still exists (honestly labeled — kept as residual theatre-adjacent).

**8.5 means:** honest printable ops snapshot that clears the elevation gate. **Not** “we shipped analytics.”

### CMDB — **9.0** (was 8.9)

**Credit:** Full relation vocabulary closes the R8 form-subset insult. Inline type edit is a real operator action. CSV + focus path + text legend complete the desk choreography for a **relation-ops preferred** score.

**Ding (why not 9.1+ / why process not suite):**  
- No discovery / reconciliation.  
- Graph layout still seed slots + orphans.  
- Impact users still criticality heuristic.  
- No CI class / lifecycle product beyond create + status fields.  
- Live API PATCH best-effort; mock is demo authority.

**9.0 means:** preferred **relation-ops process path** (parity honesty with Problems process preferred). **Not** ServiceNow CMDB / discovery suite.

### Changes — **9.1** hold

**Credit:** S13 removes the R8 bulk-policy honesty ding. S12 restores human CI labels after R6 political capital. ModuleBulkBar wired.

**Ding:** No +0.1 gift for residual closes. Quorum (S9), drawer-only (S7), week strip only — still cap at 9.1.

### Assets / Problems — hold **8.9 / 9.1**

ModuleBulkBar is shared craft, not feature depth. No score moves.

### Knowledge — **8.8** hold

S10 editorial queue / use-in-ticket still open.

### Critical path + Shell

| Check | Result |
|-------|:------:|
| Queues OperatorGrid + bulk + predicates | **HOLD** |
| Overview live metrics + copilot | **HOLD** |
| WorkItemDetail workbench + DynamicForm | **HOLD** |
| Shell notifications UI depth | **HOLD residual S11** (backend path ≠ center) |
| Catalog | **HOLD** |
| Lazy routes + PageLoader | **HOLD** |
| `/admin/metadata` crumbMap | **Still missing (S6)** |
| C1 type floor | **HOLD** |

---

## Critical / craft defect register

### C1–C8 class

**None new. None reopened.**

### Residual register

| ID | R8 | R9 |
|----|----|----|
| **S3** Secondary bulk class | CLOSED | **CLOSED** |
| **S3b** ModuleGrid / OperatorGrid parity | Open | **Partial** — ModuleBulkBar + ModuleKbdHint; full grid residual **open** |
| **S4** CAB silent approve | CLOSED | **CLOSED** |
| **S5** Knowledge toast theatre | CLOSED | **CLOSED** |
| **S6** Metadata crumb | Open | **Open** |
| **S7** Deep-link module detail routes | Open | **Open** |
| **S8** Asset free-text assignee | Open | **Open** |
| **S9** CAB quorum / full calendar | Partial | **Partial** (unchanged) |
| **S10** KB editorial queue / use-in-ticket | Open | **Open** |
| **S11** Notification center depth | Open | **Open** (backend wiring ≠ UX center) |
| **S12** Conflict raw CI ids | Open P2 | **CLOSED** |
| **S13** Bulk change plan/backout | Open P1 | **CLOSED** |
| **S14** (new) Reports CSAT not filtered-set truth | — | **Open P2** |
| **S15** (new) Reports SLA history filter-blind | — | **Open P2** |
| **S16** (new) Bulk skip: no per-row block reasons | — | **Open P2** |

---

## Remaining backlog (post-PASS → multi-module *tie* closer)

### P1 — bake-off margin

1. Finish **S3b**: shared ModuleGrid (checkbox column, aria-sort, sticky head, align select key with Queues **X**).  
2. Knowledge: change-vote / use-in-ticket / pending review queue (**S10**).  
3. CAB quorum (≥1 member vote before chair approve) (**S9**).  
4. Problem/Change reassignment in drawer (not only bulk).  
5. Notification center route; surface CAB/KB/relation/backend events (**S11**).

### P2 — polish

6. `crumbMap` for `/admin/metadata` (**S6**).  
7. Routes `/problems/:id`, `/changes/:id`, `/assets/:id` (**S7**).  
8. Reports: queue + date-range filters; derive CSAT or mark permanently as dashboard-only (**S14**); scope SLA history to filtered items (**S15**).  
9. Bulk status: toast/detail “skipped N: reason” (**S16**).  
10. Visual regression CI per `quality-gates.md`.  
11. Optional: month toggle on Changes calendar (drag still not required).

---

## What improved R8 → R9 (credit — real)

- **S13 closed** — bulk change transitions share single-path policy (plan / backout / CAB).  
- **S12 closed** — conflict banner human CI names.  
- **ModuleBulkBar / ModuleKbdHint** — shared secondary bulk chrome (S3b partial).  
- **Reports 8.1 → 8.5** — honest trend policy, assignee load, SLA compliance KPI, print, dark contrast.  
- **CMDB 8.9 → 9.0** — full relation vocabulary, type edit, focus, CSV, accessible legend (process preferred).  
- **Secondary preferred count 2 → 3** (Problems + Changes + CMDB).  
- **Critical five held ≥9** with zero regression found in spot-check.

PASS is “R8 residuals addressed + Reports gate + CMDB preferred process + no regression + no inflation.”  
PASS is **not** “Vox wins unlabeled 8h multi-module desk.”

---

## Viewport notes (R9)

| Viewport | Assessment |
|----------|------------|
| **1440** | Reports load bars + KPI row readable; CMDB legend full text OK; ModuleBulkBar fits |
| **1024** | Reports KPI row wraps at ≤980px; CMDB workspace still two-pane tax |
| **768** | Print still useful; CMDB map cramped; bulk chips wrap via ModuleBulkBar |
| **320** | Calendar still painful; Reports stacks; legend wraps; drawer CAB usable |

---

## Final call

| Gate | R8 | **R9** |
|------|----|--------|
| Critical surfaces ≥9 | **PASS** | **PASS (held)** |
| No C1–C8 critical defects | **PASS** | **PASS** |
| Secondary five each ≥8.5 | **PASS** | **PASS** (min 8.8) |
| Secondary preferred ≥9 | **PASS (2)** | **PASS (3: Problems + Changes + CMDB)** |
| S13 bulk policy | Open | **CLOSED** |
| S12 conflict labels | Open | **CLOSED** |
| S3b shared bulk chrome | Open | **PARTIAL** |
| Reports ≥8.5 | Fail (8.1) | **PASS (8.5)** |
| CMDB ≥8.9 | 8.9 | **PASS (9.0 process)** |
| Blind A/B L1 2h | **Vox wins** | **Vox wins** |
| Blind A/B multi-module 8h | Enterprise wins | **Enterprise still wins (slightly narrower)** |
| Self-score honesty | Mostly honest | **Mostly honest (Reports −0.1)** |
| **Elevation verdict** | R8 PASS | **PASS (Wave 3 real; inflation refused)** |

---

### PASS/FAIL + scores (executive)

**Verdict: PASS** — No regressions; S13/S12 closed; S3b partial as claimed; Reports **8.5** (self 8.6 refused); CMDB **9.0** process-path preferred; critical five held; multi-module 8h still enterprise.

| Secondary (gate set) | R8 | **R9** | ≥8.5 | ≥9 |
|----------------------|---:|-------:|:----:|:--:|
| Assets | 8.9 | **8.9** | Yes | No |
| Problems | 9.1 | **9.1** | Yes | **Yes** |
| Changes | 9.1 | **9.1** | Yes | **Yes** |
| CMDB | 8.9 | **9.0** | Yes | **Yes (process)** |
| Knowledge | 8.8 | **8.8** | Yes | No |
| **Secondary average** | ~9.0 | **~9.0** | Pass | **3/5 preferred** |

| Critical five | R9 |
|---------------|---:|
| Overview | **9.1** |
| Queues | **9.1** |
| WorkItemDetail | **9.0** |
| Shell | **9.1** |
| Catalog | **9.0** |
| **Critical average** | **~9.06** |

| Other | R8 | **R9** |
|-------|---:|-------:|
| MyWork | 8.7 | **8.7** |
| Reports | 8.1 | **8.5** |
| Settings | 7.0 | **7.0** |
| Admin Metadata | 6.7 | **6.7** |

### Blind winners (unlabeled)

| Scenario | Winner |
|----------|--------|
| **L1 triage 2h** | **Vox** |
| **Multi-module 8h** | **Enterprise (Naumen / ServiceNow Workspace class)** |
| **Polish screenshot** | **Vox** |
| **Guided bulk + CHG + CMDB rel + Reports print/CSV** | **Vox competitive / demo-tie** |

**Can Vox win multi-module desk?**  
**No.** Closer than R8: bulk policy honesty, human conflict labels, CMDB preferred relation-ops, Reports printable 8.5. Enterprise still takes the unlabeled 8-hour multi-module shift.

**Which looks better unlabeled?**  
- **2h L1:** **Vox**.  
- **8h multi-module:** **Enterprise**.  
- **Screenshot polish:** **Vox**.

**Why PASS is honest:** Wave3 residuals are wired in live store/UI with verified shared gates and human labels; Reports meets 8.5 without gift to 8.6; CMDB 9.0 framed as process preferred not suite; critic refused bake-off flip and suite inflation.
