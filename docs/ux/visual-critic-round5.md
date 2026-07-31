# Vox ITSM — Visual / Operator UX Critic Round 5 (Secondary Module Elevation Gate)

**Date:** 2026-07-31  
**Scope:** Live code under `G:\ITSM\frontend\src` after:
- `docs/ux/cmdb-kb-aaa-pass.md`
- `docs/ux/secondary-modules-aaa-pass.md`
- Prior AAA depth / R4 gate (`docs/ux/visual-critic-round4.md`, `docs/ux/aaa-depth-pass.md`)

**Surfaces inspected (live code):**  
CMDB · Knowledge · Assets · Problems · Changes · Settings · Admin Metadata  
Plus regression scan: Overview · Queues · Shell (sidebar / crumbs / badge)

**Viewports mentally reviewed:** 1440 · 1024 · 768 · 320  

**Secondary AAA bar (this round):**  
Assets, Problems, Changes, CMDB, Knowledge each **≥ 8.5** to claim secondary AAA.  
**≥ 9 preferred** (true parity with critical-path Queues craft).  
Zero new C1–C8-class critical a11y/usability defects.  
Critical five (Overview / Queues / WorkItemDetail / Shell / Catalog) must not regress below 9.0.

---

## Verdict: **FAIL**

Round 5 is a **real, large elevation** of the secondary suite — not paint, not drawer-only theatre. Session-mutable stores, validated workflow transitions, create modals, tabbed drawers, interactive CMDB graph, and Knowledge tabs/topics/reader depth all landed in code and are re-verified.

**It is still FAIL for suite-wide secondary AAA.**

### Why FAIL (harsh, non-negotiable)

1. **CMDB critic score is 8.4** — under the **≥ 8.5** secondary AAA floor. Interactive map is real; vanity stats + single seed impact + new-CI-off-map keep it off the gate.
2. **Preferred ≥ 9 is missed on every secondary module.** Best secondary is Problems **8.6**. None reach Queues-class craft (9.1). Claiming secondary AAA at 9.0–9.2 self-scores is **inflated by ~0.4–0.7**.
3. **Suite-wide craft hole:** Related tabs across Assets / Problems / Changes render **raw technical IDs** (`ci-portal`, `wi-1842`) as labels — not human CI/work-item names. That is list-shell hangover in the “Related” product surface. Enterprise desks do not ship related records as opaque keys.
4. **Secondary tables are OperatorGrid cosplay, not OperatorGrid.** Sort + j/k exist; missing bulk, missing explicit grid ARIA roles, missing `aria-sort`, no mobile meta-line AT parity, no multi-select lifecycle actions. Density toggles do not make them Queues-grade.

**What PASS would have required (minimum):**
- All five secondaries ≥ 8.5 with CMDB impact selection-aware (or honesty: “illustration impact for PG upgrade only” without vanity 97.8%)  
- Related rows resolve to names, not IDs  
- At least one secondary surface hits **≥ 9.0** (true preferred bar) **or** average secondary ≥ 8.8 with zero raw-ID related tabs  

None of those clear.

---

## Self-score inflation callout

| Surface | R4 critic | Self after pass | **R5 critic** | Inflated? | Notes |
|---------|----------:|----------------:|--------------:|:---------:|-------|
| **Assets** | 7.6 | **9.0** | **8.5** | **Yes (−0.5)** | Real lifecycle + create; not OperatorGrid AAA |
| **Problems** | 7.6 | **9.2** | **8.6** | **Yes (−0.6)** | RCA / known-error gates are honest; still drawer product |
| **Changes** | 7.6 | **9.2** | **8.6** | **Yes (−0.6)** | CAB is a status button + plan gates, not CAB product |
| **CMDB** | 6.3 | **9.0** | **8.4** | **Yes (−0.6)** | Graph real; vanity stats + seed impact block ≥8.5 honesty |
| **Knowledge** | 7.3 | **9.1** | **8.5** | **Yes (−0.6)** | Tabs/topics/reader real; feedback/contribute still toast theatre |
| **Secondary avg** | ~7.3 | **~9.1** | **~8.5** | **~0.6 overclaim** | Directionally huge gain; top-of-band fiction |

Self-docs (`secondary-modules-aaa-pass.md`, `cmdb-kb-aaa-pass.md`) are **not fraudulent** about features shipped. They are **marketing-hot on the score scale**. R4 trimmed critical self-scores by ~0.1. R5 must trim secondary self-scores by **~0.5–0.6**. That is the difference between “we built the module” and “agent would pick this for an 8-hour multi-module shift.”

---

## Blind A/B — multi-module 8-hour shift

**Unlabeled Vox vs Naumen ITSM / ServiceNow Agent Workspace / Jira Service Management** for a full-desk agent (queues + assets + problems + changes + CMDB + knowledge) across an 8-hour shift.

| Dimension | Winner | Why |
|-----------|--------|-----|
| Premium polish (chrome, brand, density aesthetic) | **Vox wins** | Navy/violet system, drawers, catalog/KB hero craft still ahead of enterprise skins |
| L1 queue triage (1–2h) | **Vox ties / slight win** | Unchanged from R4: predicates, saved views, OperatorGrid, workbench honesty |
| Secondary module process depth | **Naumen / SN / JSM** | Vox now has lifecycle/RCA/CAB gates — **demo-credible**. Enterprise still has assignment groups, multi-queue ownership, CAB calendars, discovery, authoring CMS |
| Cross-module graph (change → CI → problem → KB) | **Naumen / SN / JSM** | Vox related tabs are seeded ID links; CMDB impact is one scenario; no publish KE → KB |
| Hierarchy & IA consistency | **Naumen / SN / JSM** | Critical path feels AAA; secondary still one tier of craft below Queues |
| Consistency across modules | **Narrower loss** | Same drawer chrome + StatusChip language helps; table craft split vs OperatorGrid hurts |
| Russian typography at operator sizes | **Vox competitive / slight win** | Floor tokens held; Knowledge 7px score chrome **closed** |
| Demo vs product (operator path) | **Tie** | R4 still holds for L1 |
| Demo vs product (**whole suite / multi-module**) | **Naumen / SN / JSM** | Margin narrower than R4, **not closed** |
| **8h multi-module desk pick** | **Enterprise desks** | Vox is no longer embarrassing on secondaries; still not the shift home for full ITSM |

### Honest summary

- **L1 triage hour:** Vox can still win or tie (R4 stands).  
- **Multi-module 8h desk:** **Vox cannot win.** Can **partially tie polish and isolated module demos** (show RCA gate, CAB block, CI graph highlight). Full-day problem manager / change manager / asset admin still picks Naumen/SN/JSM.  
- R1 lost in five minutes. R4 survived L1. **R5 survives a guided secondary walkthrough.** R5 does **not** win the unlabeled multi-module bake-off.

---

## Score table R4 → R5

| Surface | R1 | R2 | R3 | R4 | **R5** | Δ R4→R5 | Gate role | Notes |
|---------|---:|---:|---:|---:|-------:|--------:|-----------|-------|
| **Overview** | 6.5 | 7.5 | 8.4 | 9.0 | **9.0** | 0 | Critical | No regression; live metrics + OperatorGrid |
| **MyWork** | 5.5 | 7.5 | 8.3 | 8.7 | **8.7** | 0 | — | Inherits store / grid |
| **Queues** | 5.0 | 7.8 | 8.6 | 9.1 | **9.1** | 0 | Critical | Still best surface |
| **Catalog** | 6.5 | 7.3 | 8.1 | 9.0 | **9.0** | 0 | Critical | Untouched path OK |
| **Knowledge** | 5.5 | 6.8 | 7.2 | 7.3 | **8.5** | **+1.2** | Secondary AAA | Tabs/topics/reader/print; not CMS |
| **CMDB** | 5.0 | 6.0 | 6.2 | 6.3 | **8.4** | **+2.1** | Secondary AAA | **Fails ≥8.5 floor** |
| **Assets** | 4.0 | 6.0 | 6.3 | 7.6 | **8.5** | **+0.9** | Secondary AAA | Floor only |
| **Problems** | 4.0 | 6.0 | 6.3 | 7.6 | **8.6** | **+1.0** | Secondary AAA | Best secondary |
| **Changes** | 4.0 | 6.0 | 6.3 | 7.6 | **8.6** | **+1.0** | Secondary AAA | CAB policy real-ish |
| **Settings** | 5.5 | 6.2 | 6.5 | 6.6 | **6.8** | +0.2 | — | Integrations cards; Save still toast |
| **WorkItemDetail** | 5.5 | 7.8 | 8.5 | 9.0 | **9.0** | 0 | Critical | No regression scan flags |
| **Shell** | 6.5 | 7.8 | 8.6 | 9.0 | **9.0** | 0 | Critical | Live badge OK; metadata crumb gap |
| **Reports** | — | — | 7.0 | 7.2 | **7.3** | +0.1 | — | Live aggregates; still snapshot |
| **Admin Metadata** | — | — | — | — | **6.7** | — | — | Read-only object browser; fine for demo |

### Aggregates

| Aggregate | R4 | **R5** | Δ |
|-----------|---:|-------:|--:|
| Average (all scored) | ~8.1 | **~8.4** | **+0.3** |
| Average (critical five) | ~9.0 | **~9.0** | 0 |
| Average (secondary five*) | ~7.3 | **~8.5** | **+1.2** |
| Secondary five min | 6.3 | **8.4** | +2.1 |
| Secondary five preferred (≥9 count) | 0/5 | **0/5** | — |

\*Assets, Problems, Changes, CMDB, Knowledge

### Secondary AAA checklist

| Module | R5 | ≥8.5? | ≥9 preferred? |
|--------|---:|:-----:|:-------------:|
| Assets | **8.5** | Yes | **No** |
| Problems | **8.6** | Yes | **No** |
| Changes | **8.6** | Yes | **No** |
| CMDB | **8.4** | **No** | **No** |
| Knowledge | **8.5** | Yes | **No** |
| **Suite secondary AAA** | — | **FAIL** | **FAIL** |

### Critical five hold (regression)

| Surface | R5 | ≥9? |
|---------|---:|:---:|
| Overview | 9.0 | Yes |
| Queues | 9.1 | Yes |
| WorkItemDetail | 9.0 | Yes |
| Shell | 9.0 | Yes |
| Catalog | 9.0 | Yes |

Critical AAA from R4 **held**. Secondary elevation is the only gate in play — and it fails.

---

## Live verification (what actually shipped)

### Assets / Problems / Changes — **substantive product depth**

| Claim | Result | Evidence |
|-------|:------:|----------|
| Session-mutable secondary store | **PASS** | `mock/store.ts`: `addAsset` / `transitionAsset`, `addProblem` / `transitionProblem` / `updateProblemFields`, `addChange` / `transitionChange` / `updateChangeFields`, `subscribeSecondaryModules` |
| Validated transitions | **PASS** | Asset in_use needs assignee; problem resolve needs RCA; known error needs RCA; change schedule needs plans; normal/emergency draft→scheduled blocked without CAB |
| Create modals → store | **PASS** | Page modals call API → store; list reloads via subscribe |
| 4-tab drawer | **PASS** | `ModuleDetailDrawer`: Overview · Activity · Related · History; focus trap; Escape; validation `role="alert"` |
| Sort / filter / density / kbd | **PARTIAL** | Column sort + j/k/Enter + density; **no bulk**, **no aria-sort**, **not shared OperatorGrid** |
| Status not color-only | **PASS** | `StatusChip` + labels |
| i18n ru/en/de module strings | **PASS** | `module.*`, `assets.*`, `problems.*`, `changes.*` present |
| Related records human-readable | **FAIL** | Labels are raw ids (`relatedCiIds` / `relatedWorkItemIds` mapped to `label: id`) |

### CMDB

| Claim | Result | Evidence |
|-------|:------:|----------|
| Interactive dependency map | **PASS** | SVG `DependencyGraph`: clickable nodes, neighbor edge highlight, dim non-neighbors, keyboard on nodes |
| Select CI → detail + relations | **PASS** | Detail dl + relationship list navigates selection |
| Filters + search + listbox kbd | **PASS** | Filter counts live; ArrowUp/Down/Home/End |
| Impact panel | **PARTIAL** | Drawer with hops/users — **single seeded PG scenario**, not per-selection BFS |
| Add CI → store | **PASS** | `createConfigurationItem` + subscribe; appears in list |
| New CI on map | **FAIL** | Fixed `GRAPH_LAYOUT` — created CIs never get coordinates |
| Honest metrics | **FAIL** | Hardcoded `97.8%` freshness and `+2.4%` growth — demo theatre |
| Type floor | **PASS** | No 7px chrome on CMDB |

### Knowledge

| Claim | Result | Evidence |
|-------|:------:|----------|
| 7px score chrome killed | **PASS** | `.article-score small { font-size: var(--text-meta) }` |
| Tabs change ordering/filter | **PASS** | recommended / popular / updated real sorts |
| Topic sidebar filters + counts | **PASS** | Live counts from articles |
| Reader: related / feedback / print | **PARTIAL** | Related + print real; feedback is **toast only** (score does not persist) |
| Contribute | **MOCK** | Toast theatre (honest copy) |
| Search | **PASS** | Title/summary/tag |

### Settings / Admin Metadata

| Surface | Assessment |
|---------|------------|
| **Settings** | Profile, locale, theme, density, notification toggles, API mode, integration health cards. Save = toast. Not admin product. **6.8** |
| **Metadata** | Read-only object/attribute/relation browser with locale labels. Useful demo configurability surface. No edit. **6.7**. Shell `crumbMap` **omits** `/admin/metadata` (crumb falls back awkwardly). |

### Regression scan — Overview / Queues / Shell

| Check | Result |
|-------|:------:|
| Queues predicates + saved views + OperatorGrid | **HOLD** |
| Overview OperatorGrid + live metrics + sync | **HOLD** |
| Shell live My Work badge (`countMyOpenAssigned` + subscribe) | **HOLD** |
| Critical path store mutability | **HOLD** |
| C1 type floor tokens (11/13) | **HOLD** |
| Knowledge residual 7px | **CLOSED** |

No critical-path regression found from secondary work.

---

## Critical defects

### C1–C8 class (a11y / foundational usability)

**None new. None reopened.**

| ID | Status | Blocking secondary AAA? |
|----|--------|:-----------------------:|
| C1 Typography floor | **CLOSED** (KB 7px fixed) | No |
| C2 Accessible operator tables | **CLOSED** on Queues path; secondary tables weaker but not C2-reopen level | No |
| C3–C8 | **CLOSED** (R3/R4) | No |

### Gate-blocking craft defects (this round)

These are **not** classic C1–C8, but they **block an honest secondary AAA claim**:

| ID | Severity | Surface | Defect |
|----|----------|---------|--------|
| **S1** | **P0 (gate)** | CMDB | Vanity metrics (`97.8%`, `+2.4%`) + single non-selection impact scenario + Add-CI invisible on graph → CMDB **8.4** under floor |
| **S2** | **P0 (craft)** | Assets / Problems / Changes | Related tab labels = raw IDs; hrefs dump to `/cmdb` without selecting the CI; uses plain `<a href>` not router Link |
| **S3** | **P1** | Secondary lists | No bulk actions, no `aria-sort`, not OperatorGrid — craft ceiling **~8.6** not **9.x** |
| **S4** | **P1** | Changes | CAB is one button flip to `cab_review` / auto `cabApproved` on schedule — process demo, not CAB product |
| **S5** | **P1** | Knowledge | Helpful Yes/No does not mutate score; contribute remains mock |
| **S6** | **P2** | Shell | `/admin/metadata` missing from `crumbMap` |
| **S7** | **P2** | Secondary | No deep-link detail routes (drawer-only); History tab = activity filter, not audit product |
| **S8** | **P2** | Assets | Assignee is free-text input, not directory person; location not editable post-create |

No **critical** a11y blocker for the R4 critical five. Secondary AAA fails on **product craft honesty**, not illegibility.

---

## Per-surface harsh notes

### Assets — **8.5** (floor only)

**Credit:** Create with validation → stock; lifecycle transitions with assign-required for in_use; serial/model/vendor/cost center in drawer; density; sort; j/k; activity log on transitions.

**Ding:** Related CI shows `ci-portal` not “Northstar Portal”. No bulk retire/assign. No OperatorGrid multi-select. Free-text assignee. No URL you can paste to a colleague. Self **9.0** pretends this is Queues parity — it is not.

### Problems — **8.6** (best secondary)

**Credit:** RCA required to resolve; known-error gate; save RCA; workflow buttons; priority/status filters; related WI path at least points at `/work-items/{id}` when seeded; auto-assign on start work.

**Ding:** Related still raw IDs for CI; priority not editable in drawer after create; no reassignment control; incident count often a number without linked tickets; history thin. Self **9.2** is fantasy — this is a strong **8.6** problem board, not SN Problem Management.

### Changes — **8.6**

**Credit:** Type pill + risk chip + CAB chip; plan/backout required to schedule; normal/emergency cannot skip CAB from draft (UI hides schedule; store enforces); standard can schedule; create with plans.

**Ding:** CAB meeting/vote/calendar does not exist — one status transition. Window not editable in drawer. No conflict check against CMDB impact. Related IDs again. Self **9.2** confuses “policy stubs exist” with “change manager shift home.”

### CMDB — **8.4** (**under floor**)

**Credit:** Largest absolute jump of the suite (+2.1). Neighbor-highlighted SVG graph, listbox selection, relationship navigation, impact drawer UX, add CI to session store, empty/error/loading states, i18n.

**Ding (why not 8.5+):**
1. Hardcoded health theatre (`97.8%`, `+2.4%`) after R4 spent rounds killing toast theatre on critical path — **integrity regression on honesty**.
2. Impact is always the PostgreSQL upgrade seed, independent of selected CI.
3. New CIs never appear on the map (fixed layout dict).
4. Class filters are icon heuristics, not a real CI class model.

Self **9.0** would require selection-driven impact **or** ruthless honesty in UI copy plus no vanity %, plus new nodes on graph. Not there.

### Knowledge — **8.5**

**Credit:** Closes R4 P1 items that mattered: score type floor, real tab sorts, topic filter with counts, reader with related + print + feedback chrome.

**Ding:** Feedback does not change `helpfulScore`; contribute toast; no “link article to ticket”; body often falls through to summary + fallback; not authoring CMS. Self **9.1** overrates a polished **reader**, not a knowledge product.

### Settings — **6.8** · Metadata — **6.7** · Reports — **7.3**

Unchanged tier: useful, not AAA-gated, not multi-module shift differentiators.

---

## 8-hour multi-module question (R5)

> Side-by-side blind with Naumen / SN / JSM: which desk does an ITSM agent pick for eight hours covering queues **and** assets / problems / changes / CMDB / knowledge?

**Pick: Naumen / ServiceNow / JSM — still.**

### Why the enterprise desk still wins

1. **Related graph is still toy-grade** — raw IDs, shallow links, no operational join across modules mid-shift.  
2. **CMDB is a beautiful diagram with one scenario** — not discovery, not impact-from-selection, not CI lifecycle for new items on the map.  
3. **CAB / assignment group / multi-queue ownership** remain thinner than process UI agents live in.  
4. **Secondary list craft < Queues craft** — operators feel the quality cliff when leaving Queues.  
5. **Persistence** still in-session mock — acceptable for demo, fatal for “product” claim under reload.

### Why Vox is no longer a five-minute reject on secondaries

1. Workflow validation is real (RCA, assign-for-in-use, CAB/plan gates).  
2. Create sticks for the session across navigation.  
3. CMDB graph interaction is demo-competitive.  
4. Knowledge reader is usable mid-shift for lookup.  
5. Visual system remains more pleasant than most enterprise chrome.

**Bottom line:** R5 elevates secondaries from **~7.3 → ~8.5 average**. That is the **biggest secondary jump of any round**. It is **not** enough to claim secondary AAA suite-wide (CMDB 8.4 + zero modules ≥9 + related-ID craft hole). Multi-module 8h still goes to enterprise desks; Vox **ties polish** and **wins L1 path**, but **does not win or full-tie multi-module desk**.

---

## Remaining backlog to convert FAIL → PASS

### P0 — clear secondary AAA floor (≥8.5 all, preferably kill inflation)

1. **CMDB honesty + impact:**  
   - Remove or compute vanity stats from live data.  
   - Impact from selected CI (even trivial 1-hop from `ciRelations`) **or** label strip as “Example: PG upgrade scenario” permanently.  
   - Place new CIs on graph (auto layout slot or list-only emphasis until layout exists).
2. **Related labels:** Resolve CI/work-item **names** (and numbers) from store; deep-link `/cmdb` with selection or query; use `Link`/`navigate`.
3. **One secondary ≥ 9.0 path:** Promote Problems or Changes with bulk, `aria-sort`, assignment control, and human related graph — prove preferred bar is reachable.

### P1 — preferred ≥9 band / multi-module tie

4. Extract shared **ModuleGrid** from OperatorGrid patterns (bulk optional, aria roles, mobile meta-line).  
5. Editable change window + risk in drawer; CAB approval as explicit action (not silent `cabApproved` on schedule).  
6. Knowledge: persist helpful vote → score; “use in ticket” deep-link.  
7. Shell crumb for `/admin/metadata`.  
8. Optional detail routes ` /problems/:id` etc. for shareable focus.

### P2 — enterprise bake-off margin

9. Durable mock persistence (sessionStorage).  
10. Cross-module: change related CI auto-open impact; problem KE → knowledge draft.  
11. Reports breakdown already partial — extend secondary module counts.  
12. Visual regression CI per `quality-gates.md`.

---

## What improved R4 → R5 (credit, not cancelled by FAIL)

- **Assets / Problems / Changes** left thin-drawer purgatory: real status machines, create forms, activity, validation banners.  
- **CMDB** left CSS illustration era: interactive graph + relations + impact UI.  
- **Knowledge** closed type-floor residual and made tabs/topics data-true.  
- Secondary average **~7.3 → ~8.5** (+1.2) — largest secondary delta of the program.  
- Critical five **held at ~9.0** with no C1/C2 reopen.  
- Suite average **~8.1 → ~8.4**.

FAIL here is not “nothing happened.” FAIL is “self-score claimed AAA; critic measures floor breach + preferred miss + craft holes.”

---

## Viewport notes (R5)

| Viewport | Assessment |
|----------|------------|
| **1440** | Secondary tables + drawers readable; CMDB workspace usable; related raw IDs more obvious |
| **1024** | Module tables horizontal-scroll; drawer full-height OK; CMDB two-column tight |
| **768** | Filters stack; create form row collapses; module tables tax scroll — no card meta-line like OperatorGrid |
| **320** | Drawer usable; multi-column secondary tables painful; CMDB map cramped |

---

## Final call

| Gate | R4 | **R5** |
|------|----|--------|
| Critical surfaces ≥9 | **PASS** | **PASS (held)** |
| No C1–C8 critical defects | **PASS** | **PASS (no regression)** |
| Secondary five each ≥8.5 | N/A (backlog) | **FAIL (CMDB 8.4)** |
| Secondary preferred ≥9 | N/A | **FAIL (0/5)** |
| Blind A/B multi-module 8h | Enterprise wins | **Enterprise still wins (narrower)** |
| Blind A/B L1 path | Vox can tie/win | **Unchanged — Vox can tie/win** |
| Self-score honesty | Mild inflate (~0.1) | **Material inflate (~0.5–0.6 on secondaries)** |
| **Secondary elevation verdict** | — | **FAIL** |

---

### PASS/FAIL + scores (executive)

**Verdict: FAIL** — suite-wide secondary AAA not met.

| Secondary (gate set) | R4 | **R5** | ≥8.5 | ≥9 |
|----------------------|---:|-------:|:----:|:--:|
| Assets | 7.6 | **8.5** | Yes | No |
| Problems | 7.6 | **8.6** | Yes | No |
| Changes | 7.6 | **8.6** | Yes | No |
| CMDB | 6.3 | **8.4** | **No** | No |
| Knowledge | 7.3 | **8.5** | Yes | No |
| **Secondary average** | ~7.3 | **~8.5** | Floor miss via min | Preferred miss |

| Critical five | R5 |
|---------------|---:|
| Overview | **9.0** |
| Queues | **9.1** |
| WorkItemDetail | **9.0** |
| Shell | **9.0** |
| Catalog | **9.0** |
| **Critical average** | **~9.0** |

| Other | R5 |
|-------|---:|
| MyWork | 8.7 |
| Reports | 7.3 |
| Settings | 6.8 |
| Admin Metadata | 6.7 |

**Can Vox win or tie multi-module desk?**  
**No win. No full tie.** Enterprise desks still take the 8-hour multi-module shift. Vox wins polish and can tie L1 triage; secondary modules are now **demo-credible (~8.5)** but not **shift-home AAA (≥9)**.

**Why FAIL is honest:** Features claimed in AAA pass notes largely exist in code. Scores claimed do not. CMDB sits at **8.4**. Zero secondaries clear **9.0**. Related-ID craft and OperatorGrid gap cap the suite. Inflated self-scores of **9.0–9.2** are called out at **~0.5–0.6** overclaim — not rubber-stamped.
