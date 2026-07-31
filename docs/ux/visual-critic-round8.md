# Vox ITSM — Visual / Operator UX Critic Round 8 (Wave 2: bulk · CMDB relations · Reports · CAB week · code-split)

**Date:** 2026-07-31  
**Scope:** Live code under `G:\ITSM\frontend\src` after R7 PASS + Wave 2 (`docs/ux/wave2-bulk-cmdb.md`, `docs/ux/wave2-reports-cab.md`).  
**Inputs:** `docs/ux/visual-critic-round7.md`, wave2 docs, live pages/store/components/router/i18n/CSS.

**Focus surfaces this wave:**  
Assets / Problems / Changes list bulk · CMDB relation edit · Reports trend/CSV/KPIs · Changes week calendar + CAB board + conflict banner · route code-split  

**Regression scan:** Overview · Queues · WorkItemDetail · Shell · Catalog · Knowledge · Problems process · Settings  

**Viewports mentally reviewed:** 1440 · 1024 · 768 · 320

**Elevation bar (this round):**  
- **No regressions** on critical five (≥ 9.0) or secondary process paths (Problems/Changes CAB gates, KB votes).  
- **Secondary list craft** must leave plain-table humiliation (R7 **S3**) — multi-select + bulk bar with honest skips.  
- **CMDB** must support live relation add/remove that rebinds graph + impact (not wallpaper).  
- **Reports** must clear **8.0** as a usable ops snapshot (filters + trend + export + KPIs) — not BI product.  
- **Changes** craft must improve (week calendar / CAB board / conflict) without score inflation into “enterprise CAB suite.”  
- Self-score inflation ≤ 0.2 vs critic.  
- **No** multi-module bake-off victory claim unless product depth actually beats enterprise desks.

---

## Verdict: **PASS**

Wave 2 is **real code**, not slideware. Secondary bulk exists and mutates the durable mock store with transition-edge honesty (mostly). CMDB relations are mutable and durable. Reports leaves the dead heat-map floor. Changes gains a visible ops strip (week calendar + board + freeze heuristic). Code-split is engineering hygiene with a credible `PageLoader`. Critical path held. Multi-module 8h still loses honestly to enterprise desks — margin narrower again, still not flipped.

### Why PASS (non-negotiable checklist)

| Requirement | Result | Evidence |
|-------------|:------:|----------|
| No critical / process regressions | **Yes** | Queues OperatorGrid, CAB schedule gates, KB votes, durable store paths intact |
| Secondary bulk craft (S3) | **Yes (closed for bulk class)** | Assets / Problems / Changes: checkboxes, select-all indeterminate, bulk-bar assign + status, Space / Ctrl+A / Esc, `aria-sort` |
| CMDB relation mutability | **Yes** | `addCiRelation` / `removeCiRelation` → `relationItems` + persist `ciRelations`; detail form; impact BFS uses live relations |
| Reports ≥ 8.0 | **Yes (8.1)** | Type/priority filters, 7-day open/resolved bars, resolution rate, MTTR mock, client CSV of filtered set, ru/en/de |
| Changes craft improved | **Yes (9.0 → 9.1)** | Week strip + nav, CAB board Approve/Reject via `setChangeCabDecision`, conflict banner + day highlight for CI∩day normals |
| Code-split without UX hole | **Yes** | `lazy` routes + `PageLoader` skeleton chrome |
| New C1–C8 critical defects | **None blocking** | Residual: conflict banner still prints raw `ci-*` ids (P2 craft ding, not critical) |
| Multi-module 8h honesty | **Enterprise still wins** | Bulk + light calendar ≠ CAB meeting product / discovery / BI warehouse |

### Why this is not a rubber stamp

1. **S3 is bulk-closed, not OperatorGrid-parity.** Secondary lists remain module `<table>`s bolted with checkboxes — no shared ModuleGrid, no sticky virtual head like OperatorGrid, no saved views / predicate chrome, select key is **Space** vs Queues **X**. Wave doc “OperatorGrid-class bulk” is **directionally fair for multi-select ops**; claiming list craft == Queues would be inflation.
2. **Bulk change schedule is thinner than single-row policy.** `bulkSetChangeStatus` skips normal → scheduled without `cabApproved`, but **does not** enforce plan/backout (single `transitionChange` does). Store comment even admits “without … plan validation.” Wave2 “allowed edges” self-claim is **slightly oversold** on Changes bulk.
3. **Reports 8.1 is ops-snapshot, not analytics.** Synthetic fill on empty days (honestly labeled in `trendHint`) still paints bars when the store is sparse — better than blank, still demo physics. No date-range control, no multi-period compare, no scheduled delivery, CSAT still dashboard mock.
4. **CAB calendar is a week strip, not a change calendar product.** No drag-reschedule, no month, no freeze windows from CMDB maintenance, no quorum (S9 only partially reduced). Board is a useful **queue of undecided `cab_review`**, not a meeting agenda with minutes.
5. **Conflict banner leaks raw CI ids** (`CI ci-pg-cluster`) after R6 spent political capital killing raw-ID theatre in Related tabs. Functional heuristic is real; label craft is sloppy.
6. **CMDB 8.9 not preferred 9.0 suite.** Relation form types are a subset (`depends_on` / `hosts` / `runs_on`); seed also has `uses` / `connects_to`. No discovery, no CI class model, no bulk graph import. Impact users still criticality heuristic.
7. **Code-split is not a surface score.** Do not +0.3 Shell because chunks load. Loader is correct craft; hold shell.

**What FAIL would still look like:** bulk that silently schedules normal changes without CAB; relation UI that does not write store / does not move impact; Reports still 7.x wallpaper; Changes calendar pure CSS with no data binding; critical-path regression; self-scores claiming 9.2 Changes suite or multi-module bake-off win.

---

## Wave 2 verification — live code

### S3 Secondary bulk — **CLOSED (bulk class)** · residual **S3b** OperatorGrid parity

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Multi-select + select-all (indeterminate) | **PASS** | Assets / Problems / Changes leading `grid-check` + header checkbox |
| Sticky bulk bar when selection > 0 | **PASS** | Shared `.bulk-bar` toolbar pattern |
| Assign to me → store | **PASS** | `bulkAssignAssets` / `Problems` / `Changes` → activity + notify |
| Bulk status respects edges | **PASS / partial** | Assets: skip invalid + `in_use` without assignee. Problems: skip resolve without RCA. Changes: skip normal schedule without CAB; **misses plan/backout** |
| Toast reports actual `n` | **PASS** | `module.bulk.assigned` / `statusChanged` with count |
| `aria-sort` on sorted headers | **PASS** | All three module tables |
| Space / Ctrl+A / Esc | **PASS** | Documented kbd hints on lists |
| Shared ModuleGrid / OperatorGrid row model | **FAIL (S3b)** | Still per-page table duplication |
| Queues parity (predicates, sticky head, X-select) | **FAIL** | Intentional scope; do not score as Queues twin |

**Files:** `pages/{Assets,Problems,Changes}/*Page.tsx`, `mock/store.ts` bulk\*, `api/{cmdb,problems,changes}.ts`, `styles/global.css` `.module-table .grid-check`.

**Score impact:** list craft was the last honest embarrassment on secondary desks. Closed. Process preferred scores on Problems/Changes were never about the grid alone.

### CMDB relation edit — **REAL**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| List relations on selected CI | **PASS** | Detail pane `selectedRelations` + type labels |
| Remove control | **PASS** | `deleteCiRelation` / `removeCiRelation` |
| Add form target + type | **PASS** | Select CI (exclude self) + `EDITABLE_REL_TYPES` |
| Validation (required, self, unknown, dup) | **PASS** | Store error keys + field alert |
| Persist durable | **PASS** | `snapshotStore.ciRelations` / hydrate |
| Graph + impact rebind | **PASS** | Local relations state + `computeImpactFromSelection` over live edges |
| Discovery / auto-rel / full type set in form | **FAIL (residual)** | Form subset; seed has more types |

**Files:** `mock/store.ts` (`list/add/removeCiRelation`), `api/cmdb.ts`, `pages/CMDB/CmdbPage.tsx`, `.ci-rel-row` / `.ci-rel-form` CSS, i18n `cmdb.relForm.*`.

**Inflation guard:** Wave self **8.9** on relation edit is fair for that feature. Surface CMDB overall **8.9** (R7 8.8 → +0.1). Preferring **9.0** would require broader CI lifecycle / class craft, not one form.

### Reports ops console — **8.0+ earned (8.1)**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Type / priority filters recompute | **PASS** | `filtered` → metrics, bars, trend, urgent, CSV scope |
| 7-day open/resolved trend | **PASS** | `last7DayKeys` + bar pairs; counts under days |
| Synthetic fill disclosed | **PASS (honesty)** | `reports.trendHint` “store + light synthetic fill”; code seeds empty days |
| Resolution rate | **PASS** | resolved ÷ (resolved + active) |
| MTTR mock | **PASS** | avg `updatedAt − createdAt` hours; label says mock |
| Export CSV filtered | **PASS** | Blob download `itsm-work-items-YYYY-MM-DD.csv` |
| SLA cards deep-link queues | **PASS** | Links to breached / at_risk / unassigned |
| BI / warehouse / scheduled reports | **FAIL** | Out of scope — correctly not claimed as 9 |

**Files:** `pages/Reports/ReportsPage.tsx`, `.reports-trend*` / `.reports-kpi*` CSS, i18n en/ru/de.

**R7 7.3 → R8 8.1.** Clears wave gate. Not 8.5: synthetic fill + single-filter axis + mock CSAT keep it honest.

### Changes CAB week calendar + board — **REAL (light product)**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| 7-day week strip prev/next/today | **PASS** | `weekOffset`, Monday start, chips by type |
| Chips open drawer | **PASS** | `openRow(c)` |
| Status-ish presence (draft/CAB/scheduled/IP) | **PASS** | Calendar maps schedule date; board filters `cab_review` undecided |
| CAB board Approve / Reject | **PASS** | `setChangeCabDecision` — same chair API as drawer |
| Freeze / CI conflict heuristic | **PASS** | ≥2 normal same local day ∩ `relatedCiIds`; banner + `is-conflict` day |
| Seed demo conflict | **PASS** | CHG-422 + CHG-430 on 2026-08-02 + `ci-pg-cluster` |
| Human CI labels in banner | **FAIL (craft)** | Renders raw ids |
| Month / drag / CMDB freeze windows / quorum | **FAIL** | Residual S9 partial |

**Files:** `ChangesPage.tsx` (`findNormalScheduleConflicts`, calendar, board), `mock/data.ts` CHG-430/435, CSS `.changes-calendar*` / `.changes-cab-board*` / `.changes-conflict-banner*`.

**Score:** Changes **9.0 → 9.1**. Preferred process already closed in R7; this wave adds **visible change-manager ops chrome** without inventing enterprise suite. +0.1 only — calendar is light.

### Code-split — **ENGINEERING HOLD (no surface inflation)**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Route-level `React.lazy` | **PASS** | All main pages in `app/router.tsx` |
| Suspense fallback craft | **PASS** | `PageLoader` mirrors head + filters + panel skeleton |
| Eager shell/auth | **PASS** | `AppShell`, `AuthCallbackPage` not lazy |

No score delta for Overview/Queues/Shell beyond “no regression / no blank flash theatre.” Good.

---

## Score table R7 → R8

| Surface | R5 | R6 | R7 | **R8** | Δ R7→R8 | Gate role | Notes |
|---------|---:|---:|---:|-------:|--------:|-----------|-------|
| **Overview** | 9.0 | 9.0 | 9.1 | **9.1** | 0 | Critical | Held; code-split loader only |
| **MyWork** | 8.7 | 8.7 | 8.7 | **8.7** | 0 | — | Held |
| **Queues** | 9.1 | 9.1 | 9.1 | **9.1** | 0 | Critical | Still best surface; secondary bulk does not surpass it |
| **Catalog** | 9.0 | 9.0 | 9.0 | **9.0** | 0 | Critical | Held |
| **Knowledge** | 8.5 | 8.5 | 8.8 | **8.8** | 0 | Secondary AAA | No wave2 work |
| **CMDB** | 8.4 | 8.8 | 8.8 | **8.9** | **+0.1** | Secondary AAA | Live relation add/remove + durable graph |
| **Assets** | 8.5 | 8.7 | 8.7 | **8.9** | **+0.2** | Secondary AAA | Bulk + aria-sort; drawer unchanged |
| **Problems** | 8.6 | 9.0 | 9.0 | **9.1** | **+0.1** | Secondary AAA | Process held; list bulk + honest RCA skip |
| **Changes** | 8.6 | 8.8 | 9.0 | **9.1** | **+0.1** | Secondary AAA | Calendar + board + conflict + bulk |
| **Settings** | 6.8 | 6.8 | 7.0 | **7.0** | 0 | — | Held |
| **WorkItemDetail** | 9.0 | 9.0 | 9.0 | **9.0** | 0 | Critical | Held |
| **Shell** | 9.0 | 9.0 | 9.1 | **9.1** | 0 | Critical | Code-split does not inflate |
| **Reports** | 7.3 | 7.3 | 7.3 | **8.1** | **+0.8** | Ops | **Largest absolute lift this wave** |
| **Admin Metadata** | 6.7 | 6.7 | 6.7 | **6.7** | 0 | — | Read-only; **crumbMap still missing (S6)** |

### Aggregates

| Aggregate | R7 | **R8** | Δ |
|-----------|---:|-------:|--:|
| Average (all scored) | ~8.6 | **~8.7** | **+0.1** |
| Average (critical five) | ~9.06 | **~9.06** | 0 |
| Average (secondary five*) | ~8.86 | **~9.0** | **+0.14** |
| Secondary five min | 8.7 | **8.8** | +0.1 (Knowledge floor) |
| Secondary preferred (≥9 count) | 2/5 | **3/5** | Problems + Changes; **CMDB still under 9** |
| Reports gate ≥8.0 | Fail (7.3) | **Pass (8.1)** | |

\*Assets, Problems, Changes, CMDB, Knowledge

### Secondary AAA checklist

| Module | R8 | ≥8.5? | ≥9 preferred? |
|--------|---:|:-----:|:-------------:|
| Assets | **8.9** | Yes | No |
| Problems | **9.1** | Yes | **Yes** |
| Changes | **9.1** | Yes | **Yes** |
| CMDB | **8.9** | Yes | No |
| Knowledge | **8.8** | Yes | No |
| **Suite secondary AAA** | — | **PASS** | **PASS (3 preferred process/list mix)** |

### Critical five hold

| Surface | R8 | ≥9? |
|---------|---:|:---:|
| Overview | 9.1 | Yes |
| Queues | 9.1 | Yes |
| WorkItemDetail | 9.0 | Yes |
| Shell | 9.1 | Yes |
| Catalog | 9.0 | Yes |

---

## Self-score honesty (wave docs vs critic)

| Claim | Wave self | **R8 critic** | Inflated? |
|-------|----------:|--------------:|:---------:|
| Assets list craft | 8.7 | **8.9 surface** | No — self was modest; surface includes existing drawer |
| Problems list craft | 8.7 | List craft ~8.8; surface **9.1** | No — process already 9.0; +bulk |
| Changes list craft | 8.8 | List ~8.8; surface **9.1** | No |
| CMDB relation edit | 8.9 | Feature **8.9** | Matches |
| Reports target | 8.0+ | **8.1** | Honest (not 8.5) |
| Changes process “slightly lifted” | vague | **+0.1** | Fair |
| S3 “closed” | closed | **Bulk closed; S3b open** | Mild overclaim on “OperatorGrid-class” wording |
| Changes bulk “allowed edges” | full | **CAB yes; plan/backout no** | **−0.1 honesty ding on claim** |

No surface self-score above critic by >0.2. Wave prose is mostly disciplined; the OperatorGrid-class and bulk-policy slogans are the soft spots.

---

## Blind A/B — unlabeled operator UX

Compare **Vox** vs latest **Naumen ITSM** / **ServiceNow Agent Workspace** (peer enterprise desks) without brand labels.

### (a) L1 triage — 2-hour shift

**Task:** Claim queue, sort by SLA urgency, work breached → at-risk → unassigned, brief assist, open detail, update fields, notifications. Secondary modules optional.

| Dimension | Winner | Why |
|-----------|--------|-----|
| Queue scan density + bulk | **Vox** | OperatorGrid still cleaner than typical enterprise list chrome |
| Live SLA urgency | **Vox slight** | 30s tick + breach notifs (R7) unchanged |
| Workbench field edit | **Tie / Vox polish** | DynamicForm craft vs enterprise rule depth |
| Shift briefing assist | **Tie** | Scripted live-stats brief vs heavier enterprise AI |
| Notification interrupt | **Enterprise slight** | Vox bell still shallow center |
| Premium chrome | **Vox** | Unchanged |
| Reports glance mid-shift | **Tie / Vox improved** | Export + trend now usable; enterprise PA still deeper |
| **2h L1 desk pick** | **Vox** | Wave2 does not hurt L1; Reports micro-helps |

**Honest summary (2h):** Unlabeled L1 still **picks Vox**. Wave2 bulk/CMDB/CAB are mostly out of path; they do not regress L1.

### (b) Multi-module — 8-hour full desk

**Task:** Queues + incidents + problems (RCA/KE) + normal change through CAB + week plan conflict awareness + knowledge vote/contribute + CMDB relation edit + impact + assets bulk assign + refresh mid-shift + export a CSV for the stand-up.

| Dimension | Winner | Why |
|-----------|--------|-----|
| Premium polish | **Vox** | System chrome still ahead of most enterprise skins |
| L1 queue path | **Vox** | Same as (a) |
| Secondary list bulk ops | **Tie / Vox competitive** | S3 bulk closed — enterprise no longer auto-wins on multi-select humiliation |
| Change / CAB process | **Tie / Enterprise slight** | Vox: drawer CAB + **board** + **week strip** + conflict heuristic + schedule gates. Enterprise: meetings, calendars, risk models, freeze windows, assignment groups |
| Knowledge authoring | **Enterprise** | Votes + pending contribute ≠ CMS |
| CMDB / relations | **Enterprise (narrower)** | Vox can finally **edit** edges and recompute impact; still no discovery / CI lifecycle product |
| Reports / export | **Enterprise (narrower)** | Vox CSV + 7d trend is real desk utility; not Performance Analytics |
| Persistence across refresh | **Tie / Vox demo win** | localStorage durability still beats amnesia demos; loses to multi-user servers |
| Hierarchy / deep links | **Enterprise slight** | S7 routes still open; drawer-only secondary |
| **8h multi-module desk pick** | **Enterprise desks** | Margin **narrower than R7**; still not Vox’s unlabeled full-shift home |

### Which looks better unlabeled — operator UX call

| Scenario | Blind winner | Confidence |
|----------|--------------|------------|
| **(a) L1 triage 2h** | **Vox** | High |
| **(b) Multi-module 8h** | **Enterprise (Naumen / ServiceNow Workspace class)** | High — margin reduced by bulk + CAB week + CMDB edit + Reports CSV |
| **Guided secondary walkthrough (bulk + CHG board + CMDB rel + CSV)** | **Vox competitive / demo-tie** | Medium-high |
| **Polish-only screenshot A/B** | **Vox** | High |
| **Change manager 90-minute CAB prep** | **Tie / Enterprise slight** | Medium — Vox board+week+conflict is credible prep theatre; not a CAB product |

**Why enterprise still wins 8h:** An unlabeled operator who must live in knowledge workflow, discovery-fed CMDB, multi-week change calendars, and real reporting still feels Vox secondary as **excellent modular demos stitched by a durable mock**, not a full suite. Wave2 closed the “secondary tables are toys” insult and the “CMDB is read-only wallpaper” insult. It did not invent multi-tenant authority or CMS.

**Why Vox is stronger than R7 on Changes desk:** Open `/changes`, see conflict banner, jump week to maintenance night, click chip → drawer, or Approve from **board** without opening drawer, bulk-assign a set of standard changes — that is **operator choreography**, not a status chip.

---

## Per-surface harsh notes (focus + hold)

### Reports — **8.1** (was 7.3) ★ largest lift

**Credit:** Filters actually recompute everything that matters. CSV is a real operator action with scoped rows. Trend panel has legend, day labels, counts. Resolution rate + MTTR are labeled honestly. SLA cards still deep-link into Queues. Page stops feeling like a static marketing dashboard.

**Ding (why not 8.4+):**  
- Synthetic fill keeps the sparkline “alive” when data is empty — disclosed, still theatre-adjacent.  
- Only type + priority filters (no queue, assignee, date range, service).  
- CSAT metric still not derived from the same filtered work-item truth.  
- No print/PDF, no saved report, no comparison period.

**8.1 means:** ops snapshot with actions. **Not** “we shipped analytics.”

### Changes — **9.1** (was 9.0)

**Credit:** Ops grid (calendar | board) above the table is the right IA. Board reuses real chair API. Conflict heuristic is demo-useful and seeded. Bulk coexists without breaking CAB panel. Schedule policy for single transitions still honest.

**Ding (why not 9.2+):**  
- Week strip only; seed windows may sit on “next week” relative to “today.”  
- Conflict shows raw CI ids.  
- Bulk schedule skips plan/backout gates.  
- Still drawer-only (S7); no quorum (S9).  
- Two hard-coded CAB members.

**9.1 means:** change-manager process **plus** light planning chrome. **Not** ServiceNow Change / CAB suite.

### CMDB — **8.9** (was 8.8)

**Credit:** Add/remove relations is the residual operators actually notice after impact honesty. Graph edges and blast radius move. Durable. Validations are real.

**Ding:** Type subset; no reverse-edge editor sophistication; estimated users heuristic remains; no discovery.

### Assets — **8.9** (was 8.7)

**Credit:** Bulk assign + status with edge honesty; aria-sort; keyboard select. Closes “Assets is a prettier Excel without multi-select.”

**Ding:** Free-text assignee residual (S8); still not OperatorGrid; drawer assignment model thin.

### Problems — **9.1** (was 9.0)

**Credit:** Bulk respects RCA gate (skips resolve without root cause — correct). List craft no longer embarrasses the preferred process path.

**Ding:** +0.1 only — bulk does not deepen RCA/KE authoring.

### Knowledge — **8.8** hold

No wave2. S10 editorial queue / use-in-ticket still open. Do not touch score.

### Critical path + Shell

| Check | Result |
|-------|:------:|
| Queues OperatorGrid + bulk + predicates | **HOLD** |
| Overview live metrics + copilot | **HOLD** |
| WorkItemDetail workbench + DynamicForm | **HOLD** |
| Shell notifications + My Work badge | **HOLD** |
| Catalog | **HOLD** |
| Lazy routes + PageLoader | **NEW HOLD (craft)** |
| `/admin/metadata` crumbMap | **Still missing (S6)** |
| C1 type floor | **HOLD** |

---

## Critical / craft defect register

### C1–C8 class

**None new. None reopened.**

### Residual register

| ID | R7 | R8 |
|----|----|----|
| **S3** Secondary bulk / multi-select | Open | **CLOSED (bulk class)** |
| **S3b** (new) Not shared ModuleGrid / OperatorGrid parity | — | **Open P1** |
| **S4** CAB thin (silent approve) | CLOSED | **CLOSED** |
| **S5** Knowledge toast theatre | CLOSED | **CLOSED** |
| **S6** Metadata crumb | Open | **Open** |
| **S7** No deep-link module detail routes | Open | **Open** |
| **S8** Asset free-text assignee | Open | **Open** |
| **S9** CAB no quorum / full calendar | Open | **Partial** — light week + board; quorum/month/freeze still open |
| **S10** KB editorial queue / use-in-ticket | Open | **Open** |
| **S11** Notifications not full center; no CAB/KB events | Open | **Open** |
| **S12** (new) Conflict banner raw CI ids | — | **Open P2** |
| **S13** (new) Bulk change status skips plan/backout | — | **Open P1** (honesty) |

---

## Remaining backlog (post-PASS → multi-module *tie* closer)

### P1 — bake-off margin

1. Extract **ModuleGrid** (shared bulk + aria-sort + kbd) — close **S3b**; align select key with Queues.  
2. **S13:** bulk change status must call same validators as `transitionChange` (plan/backout/cabRejected).  
3. Knowledge: change-vote / use-in-ticket / pending review queue (**S10**).  
4. CAB quorum (≥1 member vote before chair approve) + human CI labels on conflict (**S9/S12**).  
5. Problem/Change reassignment control in drawer (not only bulk assign).

### P2 — polish

6. `crumbMap` for `/admin/metadata` (**S6**).  
7. Routes `/problems/:id`, `/changes/:id`, `/assets/:id`, `/cmdb` already has `?ci=` (**S7**).  
8. Notification center route; emit CAB/KB/relation events (**S11**).  
9. Reports: date range + queue filter; drop or flag synthetic fill when any real data exists for the day.  
10. Visual regression CI per `quality-gates.md`.  
11. Optional: month toggle on Changes calendar; drag is **not** required for next score step.

---

## What improved R7 → R8 (credit — real)

- **Secondary bulk** — Assets / Problems / Changes multi-select ops with mostly honest transition skips.  
- **CMDB graph is editable** — relations mutate, persist, and drive impact/map.  
- **Reports 7.3 → 8.1** — filters, trend, KPIs, CSV export; first time Reports clears ops usefulness bar.  
- **Changes 9.0 → 9.1** — week calendar + CAB board + freeze heuristic on top of R7 CAB policy.  
- **Secondary preferred count 2 → 3** if counting Problems + Changes process/list; CMDB still 8.9.  
- **Code-split** with non-embarrassing loader — shipping hygiene.  
- **Critical five held ≥9** with zero regression found in spot-check.

PASS is “wave2 craft is real + no regression + Reports/Changes/secondary improved.”  
PASS is **not** “Vox wins unlabeled 8h multi-module desk.”

---

## Viewport notes (R8)

| Viewport | Assessment |
|----------|------------|
| **1440** | Changes ops grid (calendar + board) readable; bulk bar fits; Reports trend seven columns OK |
| **1024** | Ops grid stacks at ≤960px — correct; secondary tables still horizontal-scroll tax |
| **768** | Week strip 7 cols get tight chip overflow; conflict banner full-width OK; bulk status chips wrap |
| **320** | Calendar painful (expected); board actions stack; bulk bar priority chips wrap; drawer CAB still usable |

---

## Final call

| Gate | R7 | **R8** |
|------|----|--------|
| Critical surfaces ≥9 | **PASS** | **PASS (held)** |
| No C1–C8 critical defects | **PASS** | **PASS** |
| Secondary five each ≥8.5 | **PASS** | **PASS** (min 8.8) |
| Secondary preferred ≥9 | **PASS (2)** | **PASS (Problems + Changes; both 9.1)** |
| S3 secondary bulk | Open | **PASS / CLOSED (bulk)** |
| CMDB relation edit | Residual | **PASS** |
| Reports ≥8.0 | Fail (7.3) | **PASS (8.1)** |
| Changes craft lift | Preferred process | **PASS (+calendar/board)** |
| Blind A/B L1 2h | **Vox wins** | **Vox wins** |
| Blind A/B multi-module 8h | Enterprise wins | **Enterprise still wins (narrower)** |
| Self-score honesty | Honest | **Mostly honest (bulk policy / OperatorGrid wording soft)** |
| **Elevation verdict** | R7 PASS | **PASS (Wave 2 real; no inflation)** |

---

### PASS/FAIL + scores (executive)

**Verdict: PASS** — No regressions; secondary bulk real; CMDB relations mutable; Reports **8.1**; Changes **9.1** with week calendar + CAB board; code-split clean. Multi-module 8h still enterprise. Critic did not gift 9.2 suite scores or bake-off victory.

| Secondary (gate set) | R7 | **R8** | ≥8.5 | ≥9 |
|----------------------|---:|-------:|:----:|:--:|
| Assets | 8.7 | **8.9** | Yes | No |
| Problems | 9.0 | **9.1** | Yes | **Yes** |
| Changes | 9.0 | **9.1** | Yes | **Yes** |
| CMDB | 8.8 | **8.9** | Yes | No |
| Knowledge | 8.8 | **8.8** | Yes | No |
| **Secondary average** | ~8.86 | **~9.0** | Pass | **2/5 preferred (process)** |

| Critical five | R8 |
|---------------|---:|
| Overview | **9.1** |
| Queues | **9.1** |
| WorkItemDetail | **9.0** |
| Shell | **9.1** |
| Catalog | **9.0** |
| **Critical average** | **~9.06** |

| Other | R7 | **R8** |
|-------|---:|-------:|
| MyWork | 8.7 | **8.7** |
| Reports | 7.3 | **8.1** |
| Settings | 7.0 | **7.0** |
| Admin Metadata | 6.7 | **6.7** |

### Blind winners (unlabeled)

| Scenario | Winner |
|----------|--------|
| **L1 triage 2h** | **Vox** |
| **Multi-module 8h** | **Enterprise (Naumen / ServiceNow Workspace class)** |
| **Polish screenshot** | **Vox** |
| **Guided bulk + CHG board + CMDB rel + CSV** | **Vox competitive / demo-tie** |

**Can Vox win multi-module desk?**  
**No.** Closer than R7: bulk secondary, editable CMDB edges, light CAB week/board, and a CSV that a stand-up can use. Enterprise desks still take the unlabeled 8-hour multi-module shift.

**Which looks better unlabeled?**  
- **2h L1:** **Vox** looks better.  
- **8h multi-module:** **Enterprise** still looks better as a full desk.  
- **Screenshot polish:** **Vox**.  

**Why PASS is honest:** Wave2 features are wired to store/API/UI with mostly correct honesty frames; scores move only where craft moved; Reports is no longer a zombie surface; critic refused suite inflation and refused the bake-off flip.
