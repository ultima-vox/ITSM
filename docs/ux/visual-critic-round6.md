# Vox ITSM — Visual / Operator UX Critic Round 6 (Secondary AAA Re-gate after R5 P0)

**Date:** 2026-07-31  
**Scope:** Live code under `G:\ITSM\frontend\src` after R5 P0 fixes documented in `docs/ux/visual-critic-fixes-round5.md`.  
**Inputs:** `docs/ux/visual-critic-round5.md`, `docs/ux/visual-critic-fixes-round5.md`, live pages/components/store/i18n.

**Surfaces inspected (live code):**  
CMDB · Knowledge · Assets · Problems · Changes  
Regression scan: Overview · Queues · WorkItemDetail · Shell · Catalog  
Spot: Settings · Admin Metadata · Reports

**Viewports mentally reviewed:** 1440 · 1024 · 768 · 320

**Secondary AAA bar (this round — same as R5):**  
Assets, Problems, Changes, CMDB, Knowledge each **≥ 8.5**.  
**Prefer ≥ 9** on Problems or Changes (true craft near Queues-class, not self-marketing).  
Zero new C1–C8-class critical a11y/usability defects.  
Critical five (Overview / Queues / WorkItemDetail / Shell / Catalog) must stay **≥ 9.0**.

---

## Verdict: **PASS**

Round 6 re-gates the **R5 P0 floor blockers**. They are **closed in live code**, not claimed-only.

### Why PASS (non-negotiable checklist)

| Requirement | Result | Evidence |
|-------------|:------:|----------|
| CMDB ≥ 8.5 | **Yes (8.8)** | Live stats; selection-aware impact; orphans on graph |
| Assets / Problems / Changes / Knowledge ≥ 8.5 | **Yes** | All clear floor |
| Related human labels (no raw-ID theatre) | **Yes** | `resolveRelatedLabel` + `Link` + `?ci=` |
| Prefer ≥ 9 on Problems or Changes | **Yes (Problems 9.0)** | Process path + related + activity/history craft |
| Critical five ≥ 9.0 | **Held** | No regression found |
| New C1–C8 critical defects | **None** | — |

### Why this is not a rubber stamp

1. **Preferred bar is barely earned** — Problems hits **9.0** on *problem-manager process path* (RCA/KE gates, ranked workflow, human related graph, activity/history chronology). It is **not** Queues-class list craft (no bulk, no multi-select). Do not rewrite history as “secondary suite = Queues.”
2. **CMDB is honest, not enterprise.** Selection BFS + live operational % clear the integrity failure. Estimated users remain a **criticality heuristic** (transparent tilde copy, not fixed PG-upgrade vanity). Discovery / relation editing / CI lifecycle still absent.
3. **Changes stays 8.8** — CAB is still a status transition + auto-`cabApproved` on schedule, not a CAB product. Preferred ≥9 on Changes is **missed**.
4. **Knowledge holds 8.5 floor only** — feedback/contribute still toast theatre (honest residual from R5 S5).
5. **Multi-module 8h blind still goes to enterprise desks** — narrower margin, not closed. PASS here is **secondary AAA floor + preferred on one path**, not “Vox wins unlabeled full-desk bake-off.”

**What FAIL would still look like:** CMDB vanity % back, impact still single seed, Related still `ci-portal` / `wi-1842` labels, or critical-path regression. Those are gone.

---

## P0 verification (R5 → R6) — live code

### S1 CMDB honesty + impact + graph orphans — **CLOSED**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Vanity `97.8%` / `+2.4%` removed | **PASS** | `CmdbPage` `liveStats`: total, `operational/total` %, relations length; captions `statFromStore` / `statOperationalDetail` (`{n} of {total}`) |
| Impact selection-aware | **PASS** | `computeImpactFromSelection` BFS 1–2 hop over live `ciRelations`; strip `impactReadyFor` / `impactTextForCi`; drawer `impactScenarioFor` + `impactSelectionNote` |
| Empty / orphan impact | **PASS** | `impactTextNoNeighbors` + empty drawer hint |
| New CIs on map | **PASS** | `buildGraphLayout` + `ORPHAN_SLOTS`; `is-orphan` dashed style; aria + subtitle `orphanNode` (“New · not linked”) |
| Deep-link related → CMDB | **PASS** | `/cmdb?ci=` honored in `useEffect` |
| Seed `fetchCiImpact` still operator path? | **PASS (unused)** | Function remains in `api/cmdb.ts` for mock/API stub; **CmdbPage does not call it** |

**Residual honesty ding (non-blocking):** `usersAffected` is derived from criticality buckets (900/420/180/40 × hop). Copy uses “~{users} users in **estimated** blast radius” — acceptable heuristic, not R5-class vanity. Class filters remain icon heuristics. No relation create/edit.

**CMDB R5 8.4 → R6 8.8.**

### S2 Related raw IDs — **CLOSED**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Human labels | **PASS** | `lib/resolveRelated.ts`: WI → `INC-1842 · {title}`; CI → store name (`Northstar Portal`, `VPN Gateway AMS`, …); assets/problems/changes when applicable |
| Router links | **PASS** | `ModuleDetailDrawer` `RelatedList` uses `react-router` `Link` for internal `/…` |
| CMDB selection deep-link | **PASS** | `resolveRelatedHref` → `/cmdb?ci={id}` |
| Empty related CTA | **PASS** | Problems → queues; Assets/Changes → CMDB |
| Soft fallback | **OK** | Unknown `ci-*` soft-labels rather than bare mono dump |

Seeded related graphs resolve correctly (`pr-88` → INC-1842 + VPN GW; assets → Northstar Portal; changes → PG / VPN / Portal).

**Assets 8.5 → 8.7 · Problems/Changes related craft contributes to score lift.**

### S3 / preferred craft (Problems · Changes) — **PARTIAL → enough for Problems 9.0**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Activity icons by kind | **PASS** | `activityIcon` status/field/comment/system |
| Relative vs absolute timestamps | **PASS** | Activity `formatRelative`; History `formatDateTime` + `title`/`dateTime` |
| Actor on entries | **PASS** | `module-activity-item__actor` |
| History chronological | **PASS** | oldest → newest + timeline rail |
| Workflow primary/secondary stack | **PASS** | `module-workflow__stack` + ranked `problemActionVariant` / `changeActionVariant` |
| Enriched problem seeds | **PASS** | `store.ts` activities for `pr-76`, `pr-61`, etc. |
| Bulk / multi-select secondary tables | **FAIL (residual P1)** | Still module `<table>`, not OperatorGrid |
| `aria-sort` on secondary headers | **FAIL (residual)** | Sort buttons + icons only; note: OperatorGrid itself also lacks `aria-sort` — secondary is still thinner on bulk/checkboxes |
| CAB product depth | **FAIL (residual S4)** | `transitionChange` still status + auto `cabApproved` |
| Assignment control in drawer | **FAIL (residual)** | Display only |
| Knowledge helpful score mutation | **FAIL (residual S5)** | Toast only |

**Problems earns preferred 9.0** on process/workbench path, not on list-grid parity.  
**Changes stays 8.8** — same drawer craft without CAB substance.

### Knowledge 7px — **HOLD CLOSED**

- No `font-size: 7px` under `frontend/src`.
- `.article-score small` still `var(--text-meta)`.

---

## Score table R5 → R6

| Surface | R4 | R5 | **R6** | Δ R5→R6 | Gate role | Notes |
|---------|---:|---:|-------:|--------:|-----------|-------|
| **Overview** | 9.0 | 9.0 | **9.0** | 0 | Critical | Held |
| **MyWork** | 8.7 | 8.7 | **8.7** | 0 | — | Held |
| **Queues** | 9.1 | 9.1 | **9.1** | 0 | Critical | Still best surface |
| **Catalog** | 9.0 | 9.0 | **9.0** | 0 | Critical | Held |
| **Knowledge** | 7.3 | 8.5 | **8.5** | 0 | Secondary AAA | Floor; feedback still toast |
| **CMDB** | 6.3 | 8.4 | **8.8** | **+0.4** | Secondary AAA | P0 honesty/impact/orphans closed |
| **Assets** | 7.6 | 8.5 | **8.7** | **+0.2** | Secondary AAA | Related labels |
| **Problems** | 7.6 | 8.6 | **9.0** | **+0.4** | Secondary AAA | **Preferred bar** |
| **Changes** | 7.6 | 8.6 | **8.8** | **+0.2** | Secondary AAA | Related + workflow stack; CAB thin |
| **Settings** | 6.6 | 6.8 | **6.8** | 0 | — | Save toast |
| **WorkItemDetail** | 9.0 | 9.0 | **9.0** | 0 | Critical | Held |
| **Shell** | 9.0 | 9.0 | **9.0** | 0 | Critical | Metadata crumb still missing |
| **Reports** | 7.2 | 7.3 | **7.3** | 0 | — | Snapshot |
| **Admin Metadata** | — | 6.7 | **6.7** | 0 | — | Read-only; crumb gap |

### Aggregates

| Aggregate | R5 | **R6** | Δ |
|-----------|---:|-------:|--:|
| Average (all scored) | ~8.4 | **~8.5** | **+0.1** |
| Average (critical five) | ~9.0 | **~9.0** | 0 |
| Average (secondary five*) | ~8.5 | **~8.8** | **+0.3** |
| Secondary five min | 8.4 | **8.5** | +0.1 |
| Secondary five preferred (≥9 count) | 0/5 | **1/5** | Problems |

\*Assets, Problems, Changes, CMDB, Knowledge

### Secondary AAA checklist

| Module | R6 | ≥8.5? | ≥9 preferred? |
|--------|---:|:-----:|:-------------:|
| Assets | **8.7** | Yes | No |
| Problems | **9.0** | Yes | **Yes** |
| Changes | **8.8** | Yes | No |
| CMDB | **8.8** | Yes | No |
| Knowledge | **8.5** | Yes | No |
| **Suite secondary AAA** | — | **PASS** | **PASS (1 preferred)** |

### Critical five hold

| Surface | R6 | ≥9? |
|---------|---:|:---:|
| Overview | 9.0 | Yes |
| Queues | 9.1 | Yes |
| WorkItemDetail | 9.0 | Yes |
| Shell | 9.0 | Yes |
| Catalog | 9.0 | Yes |

---

## Self-score honesty (R6)

| Surface | R5 critic | Fix-doc expected | **R6 critic** | Inflated? |
|---------|----------:|-----------------:|--------------:|:---------:|
| CMDB | 8.4 | ~8.8–9.0 | **8.8** | No (low end of band) |
| Assets | 8.5 | ~8.6–8.7 | **8.7** | No |
| Problems | 8.6 | ~9.0 | **9.0** | Borderline — accept process-path 9.0, not list-parity 9.x |
| Changes | 8.6 | ~8.8–9.0 | **8.8** | Fix-doc high end overclaimed |
| Knowledge | 8.5 | hold | **8.5** | Honest |

R5 self-docs overclaimed ~0.5–0.6. **R6 fix doc is mostly aligned** (~0.0–0.2). Credit for that.

---

## Blind A/B — multi-module 8-hour shift

**Unlabeled Vox vs Naumen ITSM / ServiceNow Agent Workspace / Jira Service Management** for a full-desk agent (queues + assets + problems + changes + CMDB + knowledge).

| Dimension | Winner | Why |
|-----------|--------|-----|
| Premium polish | **Vox** | System chrome, drawers, density aesthetic still ahead of most enterprise skins |
| L1 queue triage (1–2h) | **Vox ties / slight win** | Unchanged: OperatorGrid, predicates, saved views, workbench |
| Secondary process depth | **Enterprise (narrower)** | Vox: real RCA/KE/plan gates + selection impact. Enterprise: CAB calendars, assignment groups, discovery, authoring CMS |
| Cross-module graph | **Enterprise (narrower)** | Vox related now **human-named** with `/cmdb?ci=` and `/work-items/…` — no longer toy IDs. Still seeded shallow graph, no KE→KB publish |
| Hierarchy / IA consistency | **Enterprise slight** | Critical path AAA; secondary list craft still below Queues |
| Russian typography | **Vox competitive** | Floor held; KB 7px remains closed |
| Demo vs product (L1) | **Tie / Vox** | R4/R5 stand |
| Demo vs product (whole suite) | **Enterprise** | Session-memory mock; no durable ownership model |
| **8h multi-module desk pick** | **Enterprise desks** | Margin **narrower than R5**; still not Vox’s shift home |

### Honest summary

- **L1 triage hour:** Vox can still win or tie.  
- **Guided secondary walkthrough:** Vox is now **credible** — open PRB-88 related → INC-1842 by name; CMDB impact follows selected CI; create orphan CI appears dim on map; operational % moves with data.  
- **Unlabeled 8h multi-module desk:** **Enterprise still wins.** Related honesty + selection impact close theatre objections; they do not replace CAB boards, bulk secondary ops, durable persistence, or knowledge authoring.  
- R1: five-minute reject. R4: L1 survives. R5: secondary walkthrough survives, suite AAA fails. **R6: secondary AAA floor + preferred Problems path PASS; multi-module bake-off still enterprise.**

---

## Per-surface harsh notes

### CMDB — **8.8** (clears floor; not 9.0)

**Credit:** Killed the integrity regression (vanity %). Impact is real topology from selection. Orphans land on the map with honest “not linked” labeling. Stats recompute with store. Deep-link selection works for Related navigation.

**Ding:** Estimated users are formulaic. No mutate-CI / edit-relations UI. Filters = icon buckets. `fetchCiImpact` seed API still exists (dead weight). Map layout is fixed slots, not layout engine. This is a **strong demo CMDB**, not discovery CMDB.

### Assets — **8.7**

**Credit:** Related CI shows human names + `Link` to selected CI. Lifecycle + create + density/sort/kbd unchanged solid base.

**Ding:** Free-text assignee, no bulk retire/assign, drawer-only, no post-create location edit. Floor cleared with room; not preferred.

### Problems — **9.0** (preferred path)

**Credit:** Best secondary. RCA required to resolve; known-error gate; ranked primary (Start/Resolve) vs secondary/danger actions; human related INC + CI; incident count phrase; activity icons + actors; history chronology; enriched thin seeds; empty related CTA into queues.

**Ding (why not 9.1+ / why not Queues twin):** No bulk, no multi-select lifecycle, no reassignment control, priority not editable post-create, no shareable `/problems/:id`, History = filtered activity. **9.0 is process-path AAA, not grid AAA.** Inflating to 9.2 would repeat R5 marketing error.

### Changes — **8.8**

**Credit:** Plan/backout schedule gates, normal/emergency CAB path in store, type/risk/CAB chips, related labels, workflow stack, activity craft shared with Problems.

**Ding:** CAB meeting/vote/calendar absent; `cabApproved` flips on schedule; window/risk not editable in drawer; no CMDB conflict check wired from change record. Preferred ≥9 **not** earned.

### Knowledge — **8.5**

**Credit:** Tabs/topics/reader/print/type floor still real.

**Ding:** Helpful Yes/No does not mutate `helpfulScore`; contribute mock toast; body fallback often summary. Floor only — no R6 elevation work.

### Critical path / shell

| Check | Result |
|-------|:------:|
| Queues OperatorGrid + bulk + predicates | **HOLD** |
| Overview live metrics + grid | **HOLD** |
| WorkItemDetail workbench | **HOLD** |
| Shell live My Work badge | **HOLD** |
| C1 type floor | **HOLD** |
| `/admin/metadata` crumbMap | **Still missing (P2)** |

---

## Critical / craft defect register

### C1–C8 class

**None new. None reopened.**

### Gate-blocking from R5

| ID | R5 | R6 |
|----|----|----|
| **S1** CMDB vanity + seed impact + off-map CI | **P0 open** | **CLOSED** |
| **S2** Related raw IDs | **P0 open** | **CLOSED** |
| **S3** Secondary ≠ OperatorGrid | P1 | **Open** (caps Assets/Changes; Problems still 9.0 via process) |
| **S4** CAB thin | P1 | **Open** |
| **S5** Knowledge feedback toast | P1 | **Open** |
| **S6** Metadata crumb | P2 | **Open** |
| **S7** No deep-link module detail routes | P2 | **Open** |
| **S8** Asset free-text assignee | P2 | **Open** |

---

## Remaining backlog (post-PASS polish → multi-module tie)

### P1 — multi-module margin / Changes preferred

1. Shared **ModuleGrid** (bulk optional, checkbox column, mobile meta-line) — close S3.  
2. Explicit CAB approve action (no silent `cabApproved` on schedule); editable window/risk.  
3. Knowledge: persist helpful vote → score; optional “use in ticket” deep-link.  
4. Problem/Change reassignment control in drawer.

### P2 — bake-off margin

5. `crumbMap` entry for `/admin/metadata`.  
6. Optional routes `/problems/:id`, `/changes/:id`, `/assets/:id`.  
7. SessionStorage (or durable mock) persistence.  
8. Cross-module: open change → related CI → auto impact; KE → KB draft.  
9. Remove or clearly quarantine unused `fetchCiImpact` seed path.  
10. Visual regression CI per `quality-gates.md`.

---

## What improved R5 → R6 (credit)

- **CMDB integrity restored** — live operational %, selection BFS impact, orphan nodes on graph, `?ci=` deep-link. Largest honesty fix since critical-path toast purge.  
- **Related graph became operator-readable** — names and numbers, not opaque keys; router Links.  
- **Problems crossed preferred 9.0** on process craft (workflow hierarchy + activity/history + related).  
- **Secondary average ~8.5 → ~8.8**; min **8.4 → 8.5**.  
- **Critical five held ~9.0** with zero C1–C8 reopen.  
- Fix documentation expectations largely matched critic scores (unlike R5 self-AAA fiction).

PASS is “secondary AAA claim is now honest.” PASS is **not** “enterprise loses the 8h desk.”

---

## Viewport notes (R6)

| Viewport | Assessment |
|----------|------------|
| **1440** | Secondary tables + drawers readable; CMDB workspace + impact strip clear; related names improve scan |
| **1024** | Module tables horizontal-scroll; CMDB two-column tight but usable |
| **768** | Filters stack; create forms collapse; module tables still tax scroll (no OperatorGrid card meta-line) |
| **320** | Drawer usable; multi-column tables painful; CMDB map cramped; orphans still keyboard-reachable via list |

---

## Final call

| Gate | R5 | **R6** |
|------|----|--------|
| Critical surfaces ≥9 | **PASS** | **PASS (held)** |
| No C1–C8 critical defects | **PASS** | **PASS** |
| Secondary five each ≥8.5 | **FAIL (CMDB 8.4)** | **PASS** |
| Secondary preferred ≥9 | **FAIL (0/5)** | **PASS (Problems 9.0)** |
| Related human labels | **FAIL** | **PASS** |
| Blind A/B multi-module 8h | Enterprise wins | **Enterprise still wins (narrower)** |
| Blind A/B L1 path | Vox can tie/win | **Unchanged — Vox can tie/win** |
| Self-score honesty | Material inflate | **Mostly honest** |
| **Secondary elevation verdict** | **FAIL** | **PASS** |

---

### PASS/FAIL + scores (executive)

**Verdict: PASS** — secondary AAA floor met; preferred bar met on Problems; critical five held; R5 P0s verified closed in live code.

| Secondary (gate set) | R5 | **R6** | ≥8.5 | ≥9 |
|----------------------|---:|-------:|:----:|:--:|
| Assets | 8.5 | **8.7** | Yes | No |
| Problems | 8.6 | **9.0** | Yes | **Yes** |
| Changes | 8.6 | **8.8** | Yes | No |
| CMDB | 8.4 | **8.8** | Yes | No |
| Knowledge | 8.5 | **8.5** | Yes | No |
| **Secondary average** | ~8.5 | **~8.8** | Pass | 1/5 preferred |

| Critical five | R6 |
|---------------|---:|
| Overview | **9.0** |
| Queues | **9.1** |
| WorkItemDetail | **9.0** |
| Shell | **9.0** |
| Catalog | **9.0** |
| **Critical average** | **~9.0** |

| Other | R6 |
|-------|---:|
| MyWork | 8.7 |
| Reports | 7.3 |
| Settings | 6.8 |
| Admin Metadata | 6.7 |

**Can Vox win or tie multi-module desk?**  
**No win. Partial demo-tie on guided paths only.** Enterprise desks still take the unlabeled 8-hour multi-module shift. Vox wins polish, can win/tie L1, and now **honestly claims secondary AAA (≥8.5 all, Problems ≥9)** without the R5 integrity failures.

**Why PASS is honest:** CMDB stats and impact are selection/live-data true; Related tabs resolve to human names; Problems process craft clears preferred; critical path did not regress. Residuals (OperatorGrid gap, CAB thin, KB toast, session mock) are **P1/P2 polish**, not floor breach.
