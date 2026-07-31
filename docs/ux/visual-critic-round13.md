# Vox ITSM — Visual / Operator UX Critic Round 13 (Wave 7: Resolve matrix · Problem/Change bind · Overview personalize)

**Date:** 2026-07-31  
**Scope:** Live code under `G:\ITSM\frontend\src` after R12 PASS + Wave 7 (`docs/ux/wave7-resolve-matrix.md`, `docs/ux/wave7-problem-change-workflow.md`, `docs/ux/wave7-overview-personalize.md`).  
**Inputs:** `docs/ux/visual-critic-round12.md`, wave7 docs, live pages/components/lib/CSS/i18n.

**Focus this wave:**  
**S27/S28** sticky Resolve + `requiredPermissions` honesty · **Problem/Change** workflow runtime bind · Overview personalization (widgets / greeting / queue chips)

**Regression scan:** Overview · Queues · WorkItemDetail · Shell · Catalog · Knowledge · Problems · Changes · Assets · CMDB · Reports · Settings · Metadata · Automation · Workflow · SLA · Audit · Search · RBAC

**Viewports mentally reviewed:** 1440 · 1024 · 768 · 320

**Elevation bar (this round — non-negotiable):**  
- **S27 CLOSED** — `requiredPermissions` gate runtime transitions (and sticky action stubs) against mock RBAC principal.  
- **S28 CLOSED** — sticky Resolve only when matrix has RESOLVED edge; disabled + tooltip for fields/perms.  
- **S21 partial → stronger** — problem + change drawers bound to active workflow defs (or hard-coded fallback).  
- **Overview hold ≥9.1** — personalize without smoke/h1 regression.  
- Self-score inflation ≤ 0.2; refuse Overview **9.4** gift for localStorage cosmetics.  
- **No** multi-module bake-off victory claim.

---

## Verdict: **PASS**

Wave 7 closes the dual-path honesty dings that R12 named: sticky Resolve is matrix-bound, `requiredPermissions` are live against mock principal grants, and problem/change drawers finally consume workflow admin definitions the same way work-item does. Overview personalization is real operator chrome (widget toggles, greeting, queue chips, compact hero) without breaking the e2e h1 contract. Critical five held. Multi-module 8h still loses to enterprise desks — margin narrower on process honesty, not flipped.

### Why PASS (checklist)

| Requirement | Result | Evidence |
|-------------|:------:|----------|
| S28 sticky Resolve matrix | **CLOSED** | `findResolveTransition` + hide when no RESOLVED edge; disable + tooltip for fields/perms |
| S27 requiredPermissions | **CLOSED (mock scope)** | `missingRequiredPermissions` + `permissions` into runtime; Assign/Escalate stubs; `subscribeRbac` |
| Problem/Change workflow bind | **REAL** | `getProblemRuntimeTransitions` / `getChangeRuntimeTransitions` + drawer bars + policy gates |
| Overview personalize | **Yes (9.2)** | widgets LS, greetingNamed, queue chips, compact hero; h1 preserved |
| Critical five held | **Yes** | WID **9.1**, Overview **9.2**, Queues/Shell/Catalog hold |
| Self-score honesty | **Harsh cuts** | Overview no 9.4; Problems/Changes process gift capped; S29 still open |
| Multi-module 8h honesty | **Enterprise still wins** | Mock RBAC principal, session workflow, no live engines |

### Why this is not a rubber stamp

1. **S27 closed only for mock principal.** `getUserPermissions(currentUser.id)` + role grants gate transitions. Assign on RBAC admin still does not rewrite OIDC/`AuthContext` — **S29 remains**. If principalPermissions path null, gate no-ops (intentional short-circuit).
2. **S28 closed for sticky Resolve UI path.** Macros / other god-mode shortcuts not fully audited. Resolve modal still collects notes; matrix edge must exist first.
3. **Problem/Change bind real, craft hold.** Drawers get workflow chip + action bar + required-field/policy disable. Not process-engine productization (no graph, mock store, CAB policy still client-side).
4. **Overview personalize is localStorage cosmetics + filter UX.** Not multi-user dashboard product, no server prefs, no drag layout. Score lift modest (**9.1 → 9.2**).
5. **SLA still unbound (S21 remainder).** Change/problem bind closed; SLA targets still chrome-only.
6. **No bake-off flip.** Enterprise keeps durable engines, IAM, multi-user prefs, path routes.

**What FAIL would look like:** Sticky Resolve still `disabled={resolved}` only; permissions still dead data; problem/change still decorative status buttons; Overview h1 gone / smoke fail; critical regression; multi-module victory claimed.

---

## Wave 7 verification — live code

### S27 / S28 Resolve matrix + permissions — **CLOSED** (WorkItemDetail **9.1**)

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Sticky Resolve only if RESOLVED edge | **PASS** | `findResolveTransition(wfRuntime)`; hide when undefined |
| Disable + tooltip missing fields | **PASS** | `transitionDisabledReason` / required fields (notes exception for modal) |
| Disable + tooltip missing perms | **PASS** | `missingPermissions` on runtime transition |
| `requiredPermissions` gate enablement | **PASS** | `missingRequiredPermissions` in `getWorkItemRuntimeTransitions` |
| Principal from mock RBAC | **PASS honest mock** | `getUserPermissions(currentUser.id)`; default `u-anna` / SERVICE_DESK_AGENT |
| `admin.full` short-circuit | **PASS** | in `missingRequiredPermissions` |
| Assign / Escalate action perms | **PASS stubs** | `WORK_ITEM_ACTION_PERMISSIONS` + disable |
| Live re-read after RBAC assign | **PASS session** | `subscribeRbac` / `rbacTick` |
| AuthContext / OIDC enforcement | **FAIL residual S29** | session catalog only |
| Live backend policy engine | **FAIL** | mock only |

**Files:** `lib/workflowRuntime.ts`, `WorkItemDetailPage.tsx`, `mock/rbac.ts`, i18n `workItem.workflowMissingPermissions`.

**WorkItemDetail R12 9.0 → R13 9.1 (+0.1)** — dual-path ding closed. Refuse 9.2: still mock principal, SLA unbound, macros unproven.

---

### Problem / Change workflow bind — **REAL** (Problems **9.2**, Changes **9.2**)

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Active `problem` def → drawer transitions | **PASS** | `getProblemRuntimeTransitions` |
| Active `change` def → drawer transitions | **PASS** | `getChangeRuntimeTransitions` |
| UI ↔ workflow state maps | **PASS** | problem work-item-like; change draft/cab/schedule map + API enum aliases |
| Required fields + fieldOverrides | **PASS craft** | root_cause / plan drafts count |
| Change policy gates (CAB/plan/backout) | **PASS honesty** | `changeRuntimePolicyBlock` |
| Hard-coded fallback when inactive | **PASS** | `HARD_CODED_PROBLEM_TRANSITIONS` / `HARD_CODED_CHANGE_TRANSITIONS` |
| State chip + source meta | **PASS craft** | `module-workflow__*` |
| Subscribe admin version toggle | **PASS** | `subscribeWorkflowDefinitions` |
| Live backend workflow API | **FAIL** | mock session |
| Graph / definition CRUD | **FAIL** | unchanged |

**S21:** work-item + problem + change transitions bound; **SLA still open**.

**Problems/Changes R12 9.1 → R13 9.2 (+0.1)** — process honesty lift, not CAB/root-cause product gift. Refuse 9.3+.

**Files:** `workflowRuntime.ts`, `ProblemsPage.tsx`, `ChangesPage.tsx`, CSS `.module-workflow*`, i18n `problems.*` / `changes.*`.

**Workflow admin surface R12 7.8 → R13 8.0 (+0.2)** — activation now moves three object keys. Still mock, no graph/CRUD. Refuse 8.2+.

---

### Overview personalization — **9.2** (was 9.1)

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Widget toggles localStorage | **PASS** | `vox-overview-widgets` |
| Customize popover | **PASS craft** | metrics / queue / flow / copilot |
| Greeting + OIDC name | **PASS** | `greeting` / `greetingNamed` |
| h1 always present (e2e) | **PASS gate** | smoke contract |
| Queue quick filters + counts | **PASS** | my / unassigned / breached |
| Compact hero density | **PASS** | `headline--compact`, icon-only customize |
| Server-side user prefs | **FAIL residual** | localStorage only |
| Drag layout / multi-dashboard | **FAIL** | out of scope |

**R12 9.1 → R13 9.2 (+0.1).** Personalize is real operator ownership without product bloat. Self 9.3+ refused.

**Files:** `OverviewPage.tsx`, overview CSS, i18n `overview.*`.

---

## Score table R12 → R13

| Surface | R10 | R11 | R12 | **R13** | Δ R12→R13 | Gate role | Notes |
|---------|----:|----:|----:|--------:|----------:|-----------|-------|
| **Overview** | 9.1 | 9.1 | 9.1 | **9.2** | **+0.1** | Critical | Widgets / greeting / chips |
| **MyWork** | 8.7 | 8.7 | 8.7 | **8.7** | 0 | — | Held |
| **Queues** | 9.1 | 9.1 | 9.1 | **9.1** | 0 | Critical | Held |
| **Catalog** | 9.0 | 9.0 | 9.0 | **9.0** | 0 | Critical | Held |
| **Knowledge** | 9.0 | 9.0 | 9.0 | **9.0** | 0 | Secondary AAA | Held |
| **CMDB** | 9.0 | 9.0 | 9.0 | **9.0** | 0 | Secondary AAA | Held |
| **Assets** | 9.0 | 9.0 | 9.0 | **9.0** | 0 | Secondary AAA | Held |
| **Problems** | 9.1 | 9.1 | 9.1 | **9.2** | **+0.1** | Secondary AAA | Workflow bind |
| **Changes** | 9.1 | 9.1 | 9.1 | **9.2** | **+0.1** | Secondary AAA | Workflow bind + policy |
| **Settings** | 8.0 | 8.0 | 8.0 | **8.0** | 0 | Admin | Held |
| **WorkItemDetail** | 9.0 | 9.0 | 9.0 | **9.1** | **+0.1** | Critical | S27/S28 closed |
| **Shell** | 9.1 | 9.1 | 9.1 | **9.1** | 0 | Critical | Held; S11 open |
| **Reports** | 8.5 | 8.5 | 8.5 | **8.5** | 0 | Ops | Held |
| **Admin Metadata** | 7.5 | 7.5 | 7.5 | **7.5** | 0 | Admin | Held |
| **Admin Automation** | 7.3 | 7.3 | 7.3 | **7.3** | 0 | Admin | Held |
| **Admin Workflow** | — | 7.5 | 7.8 | **8.0** | **+0.2** | Admin | 3 object keys runtime |
| **Admin SLA** | — | 7.4 | 7.4 | **7.4** | 0 | Admin | Still unbound |
| **Admin Audit** | — | 7.2 | 7.2 | **7.2** | 0 | Admin | Held |
| **Admin RBAC** | — | — | 7.4 | **7.5** | **+0.1** | Admin | Now affects WID gates (S29 partial depth) |
| **Global Search** | — | 7.8 | 8.1 | **8.1** | 0 | Discoverability | Held |

### Dimensional

| Dimension | Wave claim | **R13 critic** | Inflated? |
|-----------|------------|---------------:|:---------:|
| S27 requiredPermissions | closed | **CLOSED (mock)** | Honest if S29 stated |
| S28 sticky Resolve | closed | **CLOSED** | — |
| Problem/Change bind | real | **Real** | Refuse process 9.3+ |
| Overview personalize | polish | **+0.1 craft** | Refuse 9.3+ for LS toggles |

### Aggregates

| Aggregate | R12 | **R13** | Δ |
|-----------|----:|--------:|--:|
| Average (scored surfaces) | ~8.73 | **~8.78** | bind + personalize |
| Average (critical five) | ~9.06 | **~9.10** | WID + Overview |
| Average (secondary five†) | ~9.04 | **~9.08** | Problems/Changes |
| Secondary preferred (≥9 count) | 5/5 | **5/5** | Held |
| Wave7 focus avg (WID+P+C+Overview+WF) | — | **~9.14 / WF 8.0** | |

†Assets, Problems, Changes, CMDB, Knowledge

### Critical five hold

| Surface | R13 | ≥9? |
|---------|----:|:---:|
| Overview | 9.2 | Yes |
| Queues | 9.1 | Yes |
| WorkItemDetail | 9.1 | Yes |
| Shell | 9.1 | Yes |
| Catalog | 9.0 | Yes |

### Wave 7 gate surfaces

| Surface / claim | R13 | Gate | Result |
|-----------------|----:|------|:------:|
| S28 sticky Resolve matrix | CLOSED | Must close | **PASS** |
| S27 requiredPermissions | CLOSED (mock) | Must gate | **PASS** |
| Problem/Change bind | real | Must be real | **PASS** |
| Overview personalize | 9.2 | Hold ≥9.1 | **PASS** |
| Critical five | all ≥9 | Hold | **PASS** |

---

## Self-score honesty (wave docs vs critic)

| Claim | Wave self | **R13 critic** | Inflated? |
|-------|----------:|---------------:|:---------:|
| S27/S28 closed | closed | **CLOSED (mock principal)** | OK if S29 residual explicit |
| Problem/Change bind | real | **Real** | OK; no 9.3 process gift |
| Overview personalize | polish | **9.2 (+0.1)** | Refuse ≥9.3 |
| Workflow admin lift | implicit | **7.8 → 8.0** | Refuse ≥8.2 |
| RBAC “now real gates” | — | **7.4 → 7.5** | Partial only; S29 open |

Inflation discipline: no surface gifted above evidence. Overview +0.1 only.

---

## Blind A/B — unlabeled operator UX

### (a) L1 triage — 2-hour shift

**Task:** Queue → detail with honest Resolve, permission-disabled edges, Overview personal queue chips, problem/change drawer transitions if escalated.

| Dimension | Winner | Why |
|-----------|--------|-----|
| Queue scan + bulk | **Vox** | Unchanged |
| Workbench transitions | **Vox** | Dual-path Resolve fixed; perms tip real for demo principal |
| Overview start-of-shift | **Vox slight** | My/breached chips + hide noise widgets |
| Catalog / notif | **Unchanged** | S11 open |
| **2h L1 desk pick** | **Vox** | Honesty + personalize help L1; no hurt |

### (b) Multi-module — 8-hour full desk

| Dimension | Winner | Why |
|-----------|--------|-----|
| Premium polish | **Vox** | Chrome |
| Process workflow (WI/P/C) | **Tie / Enterprise slight** | Three object keys bound; enterprise durable engines + SLA still win long day |
| RBAC / IAM | **Enterprise** | Mock principal gates ≠ realm sync |
| Search / KB / CMDB / CAB / SLA / Audit | **Enterprise (same or slight)** | Residuals hold |
| **8h multi-module desk pick** | **Enterprise desks** | Margin **narrower than R12** (S27/S28 + P/C bind); **not flipped** |

### Which looks better unlabeled

| Scenario | Blind winner | Confidence |
|----------|--------------|------------|
| **(a) L1 triage 2h** | **Vox** | High |
| **(b) Multi-module 8h** | **Enterprise** | High — margin reduced again |
| **Process config → runtime prove** | **Tie / Vox demo** | Medium-high — three keys bind |
| **Polish screenshot A/B** | **Vox** | High |
| **IAM / durable workflow 45m** | **Enterprise** | High |

**Why enterprise still wins 8h:** Server engines, durable IAM, SLA calendar runtime, path routes, multi-user prefs, notification centers, discovery CMDB. Wave7 closed **god-mode Resolve**, **dead permission fields**, and **P/C workflow posters**. Did not invent suite durability.

---

## Per-surface harsh notes (focus)

### WorkItemDetail — **9.1** (was 9.0)

**Credit:** Sticky Resolve matrix-true; permission tooltips; Assign/Escalate stubs; RBAC subscribe.  
**Ding:** S29 (assign elsewhere ≠ auth rewrite); SLA chrome unbound; macros not re-audited; mock principal only.

### Problems / Changes — **9.2** each (was 9.1)

**Credit:** Runtime bars, chips, required fields, change policy block, admin toggle live.  
**Ding:** Mock store; no graph; CAB still not quorum product; root-cause still store-validated not engine.

### Overview — **9.2** (was 9.1)

**Credit:** Customize, chips with counts, greetingNamed, compact.  
**Ding:** localStorage not server prefs; no layout DnD; copilot still demo.

### Admin Workflow — **8.0** (was 7.8)

**Credit:** Three object keys affect product.  
**Ding:** Mock, EN seeds, no CRUD/graph, SLA still museum.

### Admin RBAC — **7.5** (was 7.4)

**Credit:** Role assign now changes WID enablement via principal grants.  
**Ding:** Still no AuthContext; EN descriptions; no multi-role/live API. S29 open.

---

## Critical / craft defect register

### C1–C8 class

**None new. None reopened.**

### Residual register

| ID | R12 | R13 |
|----|-----|-----|
| **S3 / S3b / S4 / S5 / S6 / S12 / S13 / S20** | CLOSED | **CLOSED** |
| **S7** Deep-link path routes | Partial | **Partial** |
| **S8** Asset free-text assignee | Open | **Open** |
| **S9** CAB quorum / full calendar | Partial | **Partial** |
| **S10** KB queue / use-in-ticket | Partial | **Partial** |
| **S11** Notification center depth | Open | **Open** |
| **S14–S16** Reports / bulk honesty | Open P2 | **Open P2** |
| **S17–S18** Settings prefs / appearance | Open P2 | **Open P2** |
| **S19** Catalog drawer success race | Open P3 | **Open P3** |
| **S21** Workflow/SLA unbound | Partial (WI only) | **Partial** — WI+P+C bound; **SLA open** |
| **S22** SLA enabled badge non-interactive | Open P3 | **Open P3** |
| **S23** Live bulk fake success count | Open P1 | **Open P1** |
| **S24** Notifications silent mock fallback | Open P2 | **Open P2** |
| **S25** Knowledge CMS split brain live | Open P1 | **Open P1** |
| **S26** Audit filter keys from mock seed | Open P3 | **Open P3** |
| **S27** requiredPermissions ignored | Open P2 | **CLOSED** |
| **S28** Sticky Resolve bypasses matrix | Open P2 | **CLOSED** |
| **S29** RBAC assign vs Auth/gates | Open P2 | **Partial** — gates mock principal; Auth still decoupled |
| **S30** RBAC descriptions EN-hardcoded | Open P3 | **Open P3** |
| **S31** (new) Overview prefs localStorage only (no server/user sync) | — | **Open P3** |

---

## Remaining backlog (post-PASS → multi-module *tie* closer)

### P1 — bake-off margin

1. **S23 / S25 / S11+S24** — bulk honesty, KB durability, notif center.  
2. **S10 remainder:** use-in-ticket; KE→KB.  
3. **S7 remainder (optional):** path routes if shareability matters.

### P2 — bind truth / admin depth

4. **S21 remainder:** bind SLA targets to work-item SLA chrome (or permanent “preview only”).  
5. **S29:** wire RBAC session role into Auth gates harder, or product chrome “directory demo only.”  
6. Workflow graph (read-only SVG); SLA enable + holidays + clock preview.  
7. Search: corpus facets, match highlight, J/K, recents.  
8. Audit export / date range / live action keys.  
9. S8 assignee picker; S9 CAB quorum; S14–S16; S18.

### P3

10. S22 / S26 / S30 content i18n; **S31** server prefs optional.  
11. Visual regression CI per `quality-gates.md`.

---

## Build / e2e

| Check | Result |
|-------|:------:|
| `npm run build` (tsc + vite) | **PASS** |
| `npm run test:e2e` | *(run in wave7 gate)* |

---

## Critic sign-off

**R13 PASS.** Wave 7 elevates honesty, not screenshot scores. S27/S28 closed. Problem/Change bind real. Overview +0.1 personalize. Critical five held. Enterprise still wins 8h multi-module. Next wave: P1 residuals (S23/S25/S11) or S21 SLA bind — pick for margin, not vanity.
