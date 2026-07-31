# Vox ITSM — Visual / Operator UX Critic Round 12 (Wave 6: Workflow runtime bind · RBAC admin · Search deep-links)

**Date:** 2026-07-31  
**Scope:** Live code under `G:\ITSM\frontend\src` after R11 PASS + Wave 6 (`docs/ux/wave6-workflow-bind.md`, `docs/ux/wave6-rbac-admin.md`, `docs/ux/wave6-search-deeplink.md`).  
**Inputs:** `docs/ux/visual-critic-round11.md`, wave6 docs, live pages/components/API/mock/lib/CSS/i18n.

**Focus this wave:**  
Workflow admin **bound** to WorkItemDetail transitions · Admin RBAC (roles + users assign) · Search / related **entity deep-links** (S7/S20)

**Regression scan:** Overview · Queues · WorkItemDetail · Shell · Catalog · Knowledge · Problems · Changes · Assets · CMDB · Reports · Settings · Metadata · Automation · Workflow · SLA · Audit · Search

**Viewports mentally reviewed:** 1440 · 1024 · 768 · 320

**Elevation bar (this round — non-negotiable):**  
- **Workflow binding real** — active `work-item` definition drives next-state actions (not inspector cosplay).  
- **RBAC admin ≥ 7.0 craft** — Metadata/Automation class, not a dump table.  
- **Search deep links closed** — Open lands on entity (query+drawer or work-item path), not module root.  
- **Critical five held ≥ 9.0.**  
- Self-score inflation ≤ 0.2 vs critic; refuse RBAC **7.7** gift if assign is session theatre.  
- **No** multi-module bake-off victory claim.

---

## Verdict: **PASS**

Wave 6 closes the three R11 residuals that mattered for honesty, not screenshot inflation. **Workflow binding is real:** `getWorkItemRuntimeTransitions` + WorkItemDetail action bar consume the active mock definition (required fields, unsupported TRIAGED targets, live subscribe on admin version toggle) with a hard-coded fallback when inactive. **RBAC admin clears ≥7.0** as a Roles/Users master–detail with backend-aligned seed keys — mock-only, assign does not touch Auth/OIDC. **Search deep-links are closed** for S20: Open/title link → entity grain via `resolveRelatedHref` / `searchHitPath` + module `?id=` / `?article=` / `?ci=` honor. Critical five held. Multi-module 8h still loses to enterprise desks.

### Why PASS (checklist)

| Requirement | Result | Evidence |
|-------------|:------:|----------|
| Workflow binding real | **Yes** | `lib/workflowRuntime.ts` + WorkItemDetail bar; subscribe to `setWorkflowActiveVersion`; v1 edges vs v2 TRIAGED disabled |
| RBAC ≥ 7.0 | **Yes (7.4)** | `RbacPage` tabs, perm chips, dense permission table, session role assign, nav/crumb/palette/i18n; self **7.7** refused |
| Search deep links closed | **Yes (S20 CLOSED)** | Entity paths; Problems/Changes/Assets/Knowledge/CMDB honor query params |
| Critical five held | **Yes** | Spot-check: Queues / Overview / WID / Shell / Catalog ≥9 hold |
| Self-score honesty | **Harsh cuts** | RBAC **7.7 → 7.4**; Search deep-link lift real but facets/kbd still tax; WF admin gift capped |
| Multi-module 8h honesty | **Enterprise still wins** | Mock engines, session RBAC, query+drawer not path routes, perms not enforced at runtime |

### Why this is not a rubber stamp

1. **RBAC self 7.7 is inflation → critic 7.4.** Roles catalog + user assign Select + toast are real admin chrome. Assign **never** reaches `AuthContext` / OIDC / API gates — pure session table rewrite. Permission **descriptions are English-hardcoded** in seed; role **descriptions** likewise. Permissions matrix is **read-only** (no grant edit). `setUserStatus` exists in mock and is **UI-invisible**. No multi-role principal, no live CRUD, no scope/field grants. That is Metadata-class **catalog**, not access-control product.
2. **Workflow binding is real and incomplete.** Work-item only. Change/problem definitions still **decorative**. `requiredPermissions` are **carried and ignored** — enablement uses fields + UI mapping only. Sticky header **Resolve** still bypasses the workflow bar (`disabled={resolved}` only) so illegal status jumps remain one click away. Dual path = binding half-honest.
3. **S20 closed; S7 reduced, not annihilated.** Query+drawer deep-links are the supported surface (wave doc admits no `/problems/:id` path routes). Live API hits without store/prefix still need type fallback — implemented. Facet counts remain post-filter toys. No match highlight, J/K, recents.
4. **S21 only partial.** Work-item transitions bound; SLA targets still unbound; change/problem unbound.
5. **WorkItemDetail holds 9.0** — binding is depth polish + residual dual-path ding, not a 9.1 gift.
6. **Search 7.8 → 8.1** for entity landings; refuse “search product 8.5+.”
7. **Workflow admin 7.5 → 7.8** because activation **changes the product** (the R11 kill shot). Still mock engine, no graph, EN seed names, no create/edit definition.
8. **Open residuals from R11 hold** except S20 closed and S21 partial: S7 reduced, S8–S19, S22–S26 as prior.

**What FAIL would look like:** Workflow still unbound (toggle no runtime effect); RBAC dump &lt;7.0 or missing; Search Open still module roots; critical regression; self 7.7/8.x accepted without cuts; bake-off flip claimed.

---

## Wave 6 verification — live code

### Workflow runtime bind — **REAL** (WorkItemDetail hold **9.0**; Workflow admin **7.8**)

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Active `work-item` definition sources transitions | **PASS** | `getActiveWorkflowDefinition('work-item')` → `getWorkItemRuntimeTransitions` |
| UI ↔ workflow state map (PENDING ↔ waiting) | **PASS** | `uiStatusToWorkflowState` / `workflowStateToUiStatus` |
| Outgoing edges from current state | **PASS** | filter `tr.from === currentState` |
| Required fields disable + tooltip | **PASS craft** | `missingRequiredFields`; resolve notes exception for modal |
| Unsupported targets (TRIAGED) disabled | **PASS honesty** | `unsupportedTarget` + tooltip |
| Fallback matrix when inactive | **PASS** | `HARD_CODED_WORK_ITEM_TRANSITIONS` |
| Admin toggle live-updates detail | **PASS** | `subscribeWorkflowDefinitions` → `workflowTick` |
| State chip (label + raw key + title) | **PASS craft** | `chip--workflow` |
| i18n transition / field labels | **PASS chrome** | `workItem.transition.*`, `workItem.fields.*` |
| Change / problem bound to their defs | **FAIL residual** | out of scope; still decorative |
| `requiredPermissions` enforced | **FAIL residual** | copied onto runtime object; never gate `enabled` |
| Sticky Resolve / macros respect matrix | **FAIL residual** | sticky Resolve bypasses workflow bar |
| Live backend workflow API | **FAIL** | mock session only |

**Binding real: YES.** Gate met. Not engine productization.

**Files:** `lib/workflowRuntime.ts`, `mock/workflow.ts` (`getActiveWorkflowDefinition`), `WorkItemDetailPage.tsx`, CSS `.chip--workflow` / `.work-item-workflow*`, i18n.

**Workflow admin surface R11 7.5 → R12 7.8 (+0.3)** — same inspector craft; residual “zero coupling” closed for work-item. Self gift above 8.0 refused (still mock, no graph, no CRUD, change/problem unbound).

---

### Admin RBAC (`/admin/rbac`) — **7.4** (self 7.7 refused)

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Route + lazy + Management nav + crumb + palette | **PASS** | router, Sidebar, AppShell, CommandPalette |
| Roles tab master–detail | **PASS craft** | `rbac-admin-layout` list + detail |
| Seed role keys match backend intent | **PASS** | ADMIN, SERVICE_DESK_*, REQUESTER, CHANGE_MANAGER, CAB_MEMBER |
| Permission chips + overflow | **PASS craft** | `ROLE_CHIP_LIMIT` + more chip |
| Read-only permission table (key + desc) | **PASS** | dense `data-table` |
| Users table name / role / locale / status | **PASS** | avatar + email + badges |
| Session role assign + toast | **PASS honest session** | `assignUserRole` + `rbacAdmin.roleAssignedToast` |
| Empty / error states | **PASS** | EmptyState + ErrorState + mockHint |
| i18n chrome en/ru/de | **PASS chrome / ding content** | UI strings; **perm + role descriptions EN seed** |
| Role labels localized | **PASS** | `role.labels.{en,ru,de}` |
| Assign affects runtime auth / gates | **FAIL residual** | zero `AuthContext` / permission checks |
| Edit permission matrix / create role | **FAIL** | read-only catalog |
| User status control in UI | **FAIL residual** | `setUserStatus` mock-only, no control |
| Multi-role / scopes / live API | **FAIL** | out of scope |

**R11 n/a → R12 7.4.** Gate **≥7.0 PASS**. Self **7.7** refused: assign theatre, EN content, no auth coupling.

**Files:** `pages/Admin/RbacPage.tsx`, `mock/rbac.ts`, CSS `.rbac-admin-*`, types, i18n `rbacAdmin.*` / `nav.rbac`.

---

### Search entity deep-links — **S20 CLOSED**; Search **8.1** (was 7.8)

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Work item → `/work-items/:id` | **PASS** | `resolveRelatedHref` / type fallback |
| CI → `/cmdb?ci=` honored | **PASS** | CmdbPage `ciFromQuery` |
| Asset → `/assets?id=` drawer | **PASS** | AssetsPage honor + clear |
| Problem → `/problems?id=` drawer | **PASS** | ProblemsPage honor + clear |
| Change → `/changes?id=` drawer | **PASS** | ChangesPage honor + clear |
| Knowledge → `/knowledge?article=` reader | **PASS** | KnowledgePage honor + clear |
| Search Open + title Link | **PASS** | `resolveRelatedHref ?? searchHitPath` |
| Palette path deep-link aware | **PASS** | `searchHitPath` prefers related |
| Related drawer hrefs | **PASS** | same resolver |
| Full path routes `/problems/:id` | **FAIL residual (S7 reduced)** | query+drawer is product surface |
| Corpus facets / highlight / J/K | **FAIL residual** | unchanged from R11 |
| Live id fidelity | **PASS plumbing / ding product** | type fallback when store miss |

**Deep links closed: YES (S20).** Usable search elevated. Not enterprise search suite.

**Files:** `lib/resolveRelated.ts`, `api/search.ts`, `SearchPage.tsx`, Knowledge/Assets/Problems/Changes/CMDB pages.

---

## Score table R11 → R12

| Surface | R9 | R10 | R11 | **R12** | Δ R11→R12 | Gate role | Notes |
|---------|---:|----:|----:|--------:|----------:|-----------|-------|
| **Overview** | 9.1 | 9.1 | 9.1 | **9.1** | 0 | Critical | Held |
| **MyWork** | 8.7 | 8.7 | 8.7 | **8.7** | 0 | — | Held |
| **Queues** | 9.1 | 9.1 | 9.1 | **9.1** | 0 | Critical | Held |
| **Catalog** | 9.0 | 9.0 | 9.0 | **9.0** | 0 | Critical | Held |
| **Knowledge** | 8.8 | 8.8 | 9.0 | **9.0** | 0 | Secondary AAA | Held; article deep-link additive |
| **CMDB** | 9.0 | 9.0 | 9.0 | **9.0** | 0 | Secondary AAA | Held |
| **Assets** | 8.9 | 9.0 | 9.0 | **9.0** | 0 | Secondary AAA | `?id=` honor |
| **Problems** | 9.1 | 9.1 | 9.1 | **9.1** | 0 | Secondary AAA | `?id=` honor; no process gift |
| **Changes** | 9.1 | 9.1 | 9.1 | **9.1** | 0 | Secondary AAA | Same |
| **Settings** | 7.0 | 8.0 | 8.0 | **8.0** | 0 | Admin | Held |
| **WorkItemDetail** | 9.0 | 9.0 | 9.0 | **9.0** | 0 | Critical | Binding depth; dual-path ding; no 9.1 |
| **Shell** | 9.1 | 9.1 | 9.1 | **9.1** | 0 | Critical | RBAC nav + search paths; S11 open |
| **Reports** | 8.5 | 8.5 | 8.5 | **8.5** | 0 | Ops | Held |
| **Admin Metadata** | 6.7 | 7.5 | 7.5 | **7.5** | 0 | Admin | Held |
| **Admin Automation** | — | 7.3 | 7.3 | **7.3** | 0 | Admin | Held |
| **Admin Workflow** | — | — | 7.5 | **7.8** | **+0.3** | Admin | Runtime bind for work-item |
| **Admin SLA** | — | — | 7.4 | **7.4** | 0 | Admin | Still unbound (S21 residual) |
| **Admin Audit** | — | — | 7.2 | **7.2** | 0 | Admin | Held |
| **Admin RBAC** | — | — | — | **7.4** | new | Admin | ≥7.0; self 7.7 refused |
| **Global Search** | — | — | 7.8 | **8.1** | **+0.3** | Discoverability | Entity deep-links closed |

### Dimensional (not surface totals)

| Dimension | Wave claim | **R12 critic** | Inflated? |
|-----------|------------|---------------:|:---------:|
| Workflow binding real | real | **Yes — work-item only** | Honest if scoped |
| RBAC admin | **7.7** | **7.4** | **Yes (−0.3)** |
| Search deep-link close | S20 closed | **S20 CLOSED; S7 reduced** | Path-route overclaim would be inflation |
| Critical five | held | **held** | — |

### Aggregates

| Aggregate | R11 | **R12** | Δ |
|-----------|----:|--------:|--:|
| Average (prior surfaces + Search) | ~8.7 | **~8.73** | Workflow + Search lifts |
| Average (critical five) | ~9.06 | **~9.06** | 0 |
| Average (secondary five†) | ~9.04 | **~9.04** | 0 |
| Secondary preferred (≥9 count) | 5/5 | **5/5** | Held |
| New/admin focus avg (WF+RBAC+Search) | — | **~7.77** | WF 7.8, RBAC 7.4, Search 8.1 |
| RBAC ≥7.0 | — | **Yes (7.4)** | |
| Search deep links | open S20 | **CLOSED** | |

†Assets, Problems, Changes, CMDB, Knowledge

### Critical five hold

| Surface | R12 | ≥9? |
|---------|----:|:---:|
| Overview | 9.1 | Yes |
| Queues | 9.1 | Yes |
| WorkItemDetail | 9.0 | Yes |
| Shell | 9.1 | Yes |
| Catalog | 9.0 | Yes |

### Wave 6 gate surfaces

| Surface / claim | R12 | Gate | Result |
|-----------------|----:|------|:------:|
| Workflow binding real | real (WI) | Must be real | **PASS** |
| Admin RBAC | **7.4** | ≥7.0 | **PASS** |
| Search deep links | S20 closed / **8.1** | Closed | **PASS** |
| Critical five | all ≥9 | Hold | **PASS** |

---

## Self-score honesty (wave docs vs critic)

| Claim | Wave self | **R12 critic** | Inflated? |
|-------|----------:|---------------:|:---------:|
| RBAC admin | **7.7** | **7.4** | **Yes (−0.3)** — catalog + assign UI; zero auth effect |
| Workflow bind | “real” (checklist) | **Real (scoped)** | Honest when work-item-only + dual-path residual stated |
| Search deep-link | acceptance closed | **S20 CLOSED** | OK; do not claim S7 full path-route close |
| Workflow admin surface lift | implicit | **7.5 → 7.8** | Critic-owned; refuse ≥8.0 |
| Search surface | implicit usable+ | **7.8 → 8.1** | Refuse ≥8.4 without facets/kbd/highlight |

Inflation discipline: **RBAC cut 0.3.** No surface gifted above craft evidence.

---

## Blind A/B — unlabeled operator UX

Compare **Vox** vs latest **Naumen ITSM** / **ServiceNow Agent Workspace** class desks without brand labels.

### (a) L1 triage — 2-hour shift

**Task:** Claim queue, SLA urgency, open detail (workflow bar + state chip), transition with field gates, optional global search → **land on entity**, catalog, notifications.

| Dimension | Winner | Why |
|-----------|--------|-----|
| Queue scan density + bulk | **Vox** | OperatorGrid still cleaner |
| Workbench transitions | **Tie / Vox slight** | Bound next-states + missing-field tips; sticky Resolve dual-path dirty |
| Mid-shift find-anything | **Tie / Vox improved** | Deep-link Open finally lands on entity grain |
| Catalog / notif | **Unchanged** | Catalog held; S11 open |
| **2h L1 desk pick** | **Vox** | Wave6 helps L1 (search land + WI transitions); does not hurt |

**Honest summary (2h):** Unlabeled L1 still **picks Vox**.

### (b) Multi-module — 8-hour full desk

**Task:** Full prior suite + **RBAC assign demo** + **workflow admin → detail prove** + **search entity open** + Knowledge CMS + CAB + CMDB + Reports + Settings.

| Dimension | Winner | Why |
|-----------|--------|-----|
| Premium polish | **Vox** | Chrome still ahead |
| L1 queue path | **Vox** | Unchanged strength |
| Workflow admin → runtime | **Tie / Enterprise slight** | Vox finally proves bind for work-item; enterprise engines + change/problem + permissions still win long config |
| RBAC / access admin | **Enterprise** | Vox mock catalog vs Keycloak/realm sync + effective grants + multi-role |
| Global search → entity | **Tie / Vox improved** | Deep-link closed; enterprise facets/recents/path routes still deeper |
| Knowledge / CMDB / CAB / Automation / Audit / SLA | **Enterprise (narrower or same)** | Residuals hold |
| **8h multi-module desk pick** | **Enterprise desks** | Margin **narrower than R11** (bind + deep-links + RBAC surface); **not flipped** |

### Which looks better unlabeled

| Scenario | Blind winner | Confidence |
|----------|--------------|------------|
| **(a) L1 triage 2h** | **Vox** | High |
| **(b) Multi-module 8h** | **Enterprise (Naumen / ServiceNow Workspace class)** | High — margin reduced again |
| **Guided secondary + admin walkthrough** | **Vox competitive / demo-tie** | Medium-high |
| **Polish-only screenshot A/B** | **Vox** | High |
| **Admin workflow config 45m real** | **Enterprise** | High — still mock + partial bind |
| **RBAC / IAM admin shift** | **Enterprise** | High — session assign cosplay |
| **Find entity from search hit** | **Tie / Vox** | High — S20 closed for mock ids |

**Why enterprise still wins 8h:** Server engines, permission-enforced transitions, multi-object workflow, durable RBAC/IAM sync, full path deep routes, audit export, discovery CMDB, multi-user KB, notification centers. Wave6 closed **work-item engine cosplay**, **search module-root insult**, and **RBAC admin vacuum**. It did not invent suite durability or access enforcement.

---

## Per-surface harsh notes (focus + hold)

### Workflow binding + Workflow admin — **bind REAL · admin 7.8** (was 7.5)

**Credit:**  
- `workflowRuntime` is non-trivial: maps, fallback, missing fields, resolve-notes exception, unsupported targets.  
- Detail UI shows source (active definition name/version vs fallback).  
- Admin activate v2 → TRIAGED edges appear disabled — **operator-visible proof** the inspector is no longer a museum.  
- Required-field tooltips are operator language via i18n field labels.

**Ding (why not 8.0+ / 9.1 WID):**  
- Sticky **Resolve** still god-mode.  
- Permissions on transitions are dead data.  
- Change/problem workflows remain posters.  
- Mock-only; EN seed definition names.  
- No graph, no definition CRUD.

### Admin RBAC — **7.4** (self 7.7 refused)

**Credit:**  
- Same grammar as Workflow/Metadata: tabs, master–detail, dense tables, focus rings, mockHint honesty, counts chips.  
- Permission chips in list rows scale; detail table is scannable.  
- Role keys and matrix aligned with platform seed story (demo credibility).  
- Localized role **labels**; assign Select is real control (session).

**Ding (why not 7.7):**  
- Assign does **nothing** outside the RBAC page.  
- Permission descriptions EN-only.  
- Role descriptions EN-only.  
- Read-only matrix with a “Read-only catalog” badge — honest, but caps craft score.  
- No user create, status toggle UI, filter, multi-role, live API, Keycloak sync.  
- Users tab is a flat table — competent, not Naumen directory product.

**7.4 means:** clears admin craft floor with interaction; not IAM.

### Global Search — **8.1** (was 7.8)

**Credit:**  
- S20 kill: problem/change/asset/KB/CI Open no longer dumps the operator at module root.  
- Resolver shared with related drawers — one truth.  
- Type fallback for live ids without mock store.

**Ding (why not 8.3+):**  
- Facet counts still post-filter.  
- No highlight, J/K, recents, saved searches.  
- Query+drawer is fine for SPA mock; enterprise operators still expect stable path URLs and browser history grain.  
- Filtered list may hide deep-linked row if status/query filters exclude it (honor sets selection; operator may not see row — edge residual).

### Critical path + held secondaries

| Check | Result |
|-------|:------:|
| Queues OperatorGrid + bulk | **HOLD** |
| Overview live metrics + copilot | **HOLD** |
| WorkItemDetail workbench + workflow bar | **HOLD 9.0 (+ depth)** |
| Shell (S11) | **HOLD residual** |
| Catalog 9.0 | **HOLD** |
| Secondary five preferred | **HOLD 5/5** |
| Knowledge CMS 9.0 | **HOLD** |
| S3b ModuleGrid | **HOLD CLOSED** |
| C1 type floor | **HOLD** |

---

## Critical / craft defect register

### C1–C8 class

**None new. None reopened.**

### Residual register

| ID | R11 | R12 |
|----|-----|-----|
| **S3 / S3b / S4 / S5 / S6 / S12 / S13** | CLOSED | **CLOSED** |
| **S7** Deep-link module detail routes | Open (worsened via Search) | **Partial** — query+drawer entity landings for search/related; full path routes still absent |
| **S8** Asset free-text assignee | Open | **Open** |
| **S9** CAB quorum / full calendar | Partial | **Partial** |
| **S10** KB queue / use-in-ticket | Partial | **Partial** |
| **S11** Notification center depth | Open | **Open** |
| **S14–S16** Reports / bulk honesty | Open P2 | **Open P2** |
| **S17–S18** Settings prefs / appearance | Open P2 | **Open P2** |
| **S19** Catalog drawer success race | Open P3 | **Open P3** |
| **S20** Search non–WI entity deep-link | Open P1 | **CLOSED** |
| **S21** Workflow/SLA unbound from runtime | Open P2 | **Partial** — work-item transitions bound; SLA + change/problem still open |
| **S22** SLA enabled badge non-interactive | Open P3 | **Open P3** |
| **S23** Live bulk fake success count | Open P1 | **Open P1** |
| **S24** Notifications silent mock fallback | Open P2 | **Open P2** |
| **S25** Knowledge CMS split brain live | Open P1 | **Open P1** |
| **S26** Audit filter keys from mock seed | Open P3 | **Open P3** |
| **S27** (new) Workflow `requiredPermissions` ignored at runtime | — | **Open P2** |
| **S28** (new) WorkItemDetail sticky Resolve bypasses workflow matrix | — | **Open P2** |
| **S29** (new) RBAC assign does not affect Auth / product gates | — | **Open P2** |
| **S30** (new) RBAC permission/role descriptions EN-hardcoded | — | **Open P3** |

---

## Remaining backlog (post-PASS → multi-module *tie* closer)

### P1 — bake-off margin

1. **S23 / S25 / S11+S17+S24** — still open from R11 (bulk honesty, KB durability, notif center).  
2. **S10 remainder:** use-in-ticket; KE→KB.  
3. **S7 remainder (optional):** path routes `/problems/:id` etc. if history/shareability matters more than drawer.

### P2 — bind truth / admin depth

4. **S21 remainder:** bind change/problem drawers to workflow defs; bind SLA targets to work-item SLA chrome (or permanent “preview only”).  
5. **S27 / S28:** enforce `requiredPermissions` against current principal; disable sticky Resolve when matrix forbids resolve.  
6. **S29:** wire RBAC session role into mock auth gates or label assign “directory demo only” harder in product chrome.  
7. Workflow graph (read-only SVG); SLA enable + holidays + clock preview.  
8. Search: corpus facets, match highlight, J/K, recents.  
9. Audit export / date range / live action keys.  
10. S8 assignee picker; S9 CAB quorum; S14–S16; S18.

### P3

11. S22 / S26 / S30 content i18n.  
12. Visual regression CI per `quality-gates.md`.

---

## What improved R11 → R12 (credit — real)

- **Workflow binding real** for work-item — admin activation changes detail transitions (S21 partial).  
- **Workflow admin 7.5 → 7.8** on that honesty lift.  
- **S20 CLOSED** — search/related Open lands on entity grain.  
- **Global Search 7.8 → 8.1.**  
- **Admin RBAC 7.4** new surface ≥7.0 with roles matrix + session assign.  
- Shared `resolveRelatedHref` cleans related drawers + search.  
- Critical five held; secondary preferred 5/5 held.

PASS is “binding real + RBAC ≥7.0 + deep links closed + critical hold + inflation refused.”  
PASS is **not** “Vox wins unlabeled 8h multi-module” and **not** “RBAC 7.7 / Search 8.5 / full S7 path routes / permission-enforced engine.”

---

## Viewport notes (R12)

| Viewport | Assessment |
|----------|------------|
| **1440** | RBAC two-pane solid; workflow bar under title clean; search hits with Open OK |
| **1024** | RBAC panes readable; workflow primary/secondary stack OK |
| **768** | RBAC layout stacks (`@media` 1fr); users table horizontal scroll risk; workflow chips wrap |
| **320** | RBAC list-first OK; users Select cramped; search Open still usable; workflow bar multi-wrap |

---

## Final call

| Gate | R11 | **R12** |
|------|-----|---------|
| Critical surfaces ≥9 | **PASS** | **PASS (held)** |
| No C1–C8 critical defects | **PASS** | **PASS** |
| Secondary five each ≥8.5 / preferred ≥9 | **PASS 5/5** | **PASS 5/5 held** |
| Workflow binding real | FAIL residual S21 | **PASS (work-item real; S21 partial)** |
| RBAC ≥7.0 | — | **PASS (7.4; self 7.7 refused)** |
| Search deep links | S20 open | **PASS (S20 CLOSED)** |
| Search usable / elevated | 7.8 | **PASS (8.1)** |
| Workflow admin craft | 7.5 | **PASS (7.8)** |
| Blind A/B L1 2h | Vox | **Vox** |
| Blind A/B multi-module 8h | Enterprise | **Enterprise still wins (narrower)** |
| Self-score honesty | Harsh R11 cuts | **Harsh: RBAC −0.3; no WID 9.1 gift** |
| **Elevation verdict** | R11 PASS | **PASS (Wave 6 real; gates met; bake-off not flipped)** |

---

### PASS/FAIL + scores (executive)

**Verdict: PASS** — Workflow binding **real** (work-item); Admin RBAC **7.4** (≥7.0; self 7.7 refused); Search deep links **closed** (S20; Search **8.1**); Workflow admin **7.8**; critical five held; multi-module 8h still enterprise.

| Surface | R11 | **R12** |
|---------|----:|--------:|
| Overview | 9.1 | **9.1** |
| Queues | 9.1 | **9.1** |
| WorkItemDetail | 9.0 | **9.0** |
| Shell | 9.1 | **9.1** |
| Catalog | 9.0 | **9.0** |
| **Critical average** | ~9.06 | **~9.06** |
| Assets | 9.0 | **9.0** |
| Problems | 9.1 | **9.1** |
| Changes | 9.1 | **9.1** |
| CMDB | 9.0 | **9.0** |
| Knowledge | 9.0 | **9.0** |
| MyWork | 8.7 | **8.7** |
| Reports | 8.5 | **8.5** |
| Settings | 8.0 | **8.0** |
| Admin Metadata | 7.5 | **7.5** |
| Admin Automation | 7.3 | **7.3** |
| Admin Workflow | 7.5 | **7.8** |
| Admin SLA | 7.4 | **7.4** |
| Admin Audit | 7.2 | **7.2** |
| **Admin RBAC** | — | **7.4** |
| Global Search | 7.8 | **8.1** |

### Blind winners (unlabeled)

| Scenario | Winner |
|----------|--------|
| **L1 triage 2h** | **Vox** |
| **Multi-module 8h** | **Enterprise (Naumen / ServiceNow Workspace class)** |
| **Polish screenshot** | **Vox** |
| **Guided bind + search deep-link + RBAC demo** | **Vox competitive / demo-tie** |
| **RBAC/IAM real admin shift** | **Enterprise** |
| **Workflow engine config 45m** | **Enterprise** |

**Can Vox win multi-module desk?**  
**No.** Closer than R11: real work-item workflow bind, entity search landings, RBAC catalog. Enterprise still takes unlabeled 8-hour multi-module on durability, permission-enforced engines, multi-object workflows, IAM sync, path deep routes, audit, discovery, and multi-user editorial.

**Which looks better unlabeled?**  
- **2h L1:** **Vox**.  
- **8h multi-module:** **Enterprise**.  
- **Screenshot polish:** **Vox**.

**Why PASS is honest:** Binding verified in live runtime + detail UI with admin subscribe; RBAC clears **≥7.0** with Metadata-class craft and refused **7.7**; search entity deep-links verified end-to-end (resolver → Open → module honor); critic refused dual-path / permission / assign theatre gifts; critical five unregressed; bake-off flip refused.
