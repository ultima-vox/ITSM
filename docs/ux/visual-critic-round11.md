# Vox ITSM — Visual / Operator UX Critic Round 11 (Wave 5: Workflow/SLA admin · Knowledge CMS · Global search · Audit · Live API depth)

**Date:** 2026-07-31  
**Scope:** Live code under `G:\ITSM\frontend\src` after R10 PASS + Wave 5 (`docs/ux/wave5-workflow-sla-admin.md`, `docs/ux/wave5-knowledge-live.md`, `docs/ux/wave5-search-audit.md`).  
**Inputs:** `docs/ux/visual-critic-round10.md`, wave5 docs, live pages/components/API/mock/CSS/i18n.

**Focus this wave:**  
Admin Workflow definitions · Admin SLA policies + calendar · Knowledge CMS (edit / publish / version note / status filter) · Global `/search` · Admin Audit trail · Work-item activity diffs · Live API depth (problems/changes/catalog/notifications)

**Regression scan:** Overview · Queues · WorkItemDetail · Shell · Catalog · Knowledge · Problems · Changes CAB · Assets · Reports · CMDB · Settings · Metadata · Automation

**Viewports mentally reviewed:** 1440 · 1024 · 768 · 320

**Elevation bar (this round — non-negotiable):**  
- **No regressions** on critical five (≥ 9.0).  
- **New admin surfaces ≥ 7.0 craft** each (Workflow, SLA, Audit) — Metadata/Automation class, not dump pages.  
- **Knowledge improved** vs R10 **8.8** — real CMS loop, not chip cosplay.  
- **Search usable** — full page + type filter + open path; refuse “enterprise search product” inflation.  
- Self-score inflation ≤ 0.2 vs critic on every claimed surface; refuse Knowledge **9.2** and admin **7.7/7.8** gifts.  
- **No** multi-module bake-off victory claim.

---

## Verdict: **PASS**

Wave 5 ships **real discoverability + admin metadata depth + a genuine Knowledge authoring loop**, not pure score theatre. Workflow and SLA admin clear the **≥7.0 craft** bar with master–detail inspectors matching Metadata/Automation grammar. Knowledge finally graduates from vote-only reader to **edit → version note → pending filter → one-click publish** (session mock CMS). Global search is a **usable** full-page surface wired to palette/URL. Audit is a **light** mock console that meets 7.0 barely. Live API fixes (transition path/body, catalog UUID request, notifications GET) are honest plumbing — **not** UX score gifts. Critical five held. Multi-module 8h still loses to enterprise desks.

### Why PASS (checklist)

| Requirement | Result | Evidence |
|-------------|:------:|----------|
| New admin surfaces ≥ 7.0 craft | **Yes** | Workflow **7.5**, SLA **7.4**, Audit **7.2** — all ≥ 7.0 |
| Knowledge improved | **Yes (8.8 → 9.0)** | Reader edit form, publish pending, version note/chip, status filter + counts, i18n en/ru/de |
| No critical five regressions | **Yes** | Queues OperatorGrid, Overview, WorkItemDetail, Shell, Catalog browse intact in spot-check; activity diffs are additive |
| Search usable | **Yes (7.8)** | `/search?q=&types=`, abortable `searchAll`, type chips, skeletons/empty/error, palette “Search all”, Open → path |
| Self-score honesty | **Harsh cuts** | Knowledge self **9.2 → 9.0**; Workflow **7.8 → 7.5**; SLA **7.7 → 7.4**; Search **8.0 → 7.8**; Audit **7.5 → 7.2** |
| Multi-module 8h honesty | **Enterprise still wins** | Mock engines, session CMS, shallow deep-links, no graph / holiday editor / audit export / server KB write |

### Why this is not a rubber stamp

1. **Knowledge self 9.2 is inflation.** Critic **9.0**. CMS is **session mock store** even when `!useMock` — backend still read/vote only. No multi-locale revision, no assignee review queue, no KE→KB, no use-in-ticket, no permission matrix, no version history list (only a number + free-text note). Seed bodies still materialize from i18n until first edit. That is a **real loop**, not a product CMS suite.
2. **Workflow self 7.8 refused → 7.5.** Transition matrix + multi-version active toggle are real craft. Still **mock-only**, no visual state graph, names/descriptions hard-coded English in seed (i18n keys cover chrome only), **zero coupling** to WorkItemDetail / Changes / Problems transition UIs. Toggling v2 active does not change runtime transitions anywhere. Inspector ≠ engine.
3. **SLA self 7.7 refused → 7.4.** Editable hour targets + dirty footer + toast + calendar panel clear 7.0. **Enabled badge is display-only** (no enable toggle). Holidays array empty and never surfaced. No clock-preview (“what is due if opened Friday 17:50 Moscow?”). No multi-calendar CRUD. Condition strings are opaque `priority=CRITICAL` codes, not operator language.
4. **Search self 8.0 refused → 7.8.** Usable yes. **Deep-links are weak:** knowledge/CI/asset/problem/change resolve to **module list roots** (`/knowledge`, `/problems`, …) — not the hit entity (S7 still open). Type chip **counts are post-filter** of the current result page — after you filter to “problem”, other type counts vanish; they are not corpus facets. No result keyboard nav (J/K), no recent queries, no saved searches. Live quality is whatever OpenSearch returns.
5. **Audit self 7.5 refused → 7.2.** Pretty dense table + action chips + actor avatars + work-item links. **Mock seed cosplay.** Live `GET /audit` path exists but filter chip keys always come from **mock seed** (`listAuditActionKeys`). No date range, paging, export, object-type filter, or detail drawer. Non–work-item objects are dead text (S7). Meets gate floor; do not call it compliance audit product.
6. **Live API depth is engineering, not craft elevation.** Problems/changes transition **path** fixed (`/transitions` + `{ target }`); catalog UUID → `POST …/requests`; notifications GET with **silent mock fallback** on error (can hide live failure as “seed noise”). Live **bulk still returns `ids.length` no-op**; live **patch** returns notFound. Do **not** gift Problems/Changes/Shell surface scores for wiring alone.
7. **Activity diffs are residual polish on WorkItemDetail, not a new surface score.** Clear before→after when maps exist; field labels via i18n with raw-key fallback. Not a full field catalog, not immutable audit product. **WorkItemDetail holds 9.0.**
8. **S10 only partial.** Status filter + publish closes “no editorial path” enough to lift Knowledge to preferred **9.0 process-CMS path**. Dedicated review queue, change-vote, use-in-ticket remain open.
9. **Open residuals from R10 hold:** S7 deep routes, S8 free-text asset assignee, S9 CAB quorum, S11 notif center, S14/S15 Reports honesty, S16 bulk skip reasons, S17/S18 Settings, S19 Catalog drawer success race.

**What FAIL would look like:** Workflow/SLA/Audit as unstyled dumps <7.0; Knowledge still vote-only at 8.8; search broken empty or palette-only; critical regression on Queues/Catalog; self-scores accepted at 9.2 / admin 8.x without cuts; bake-off flip claimed.

---

## Wave 5 verification — live code

### Admin Workflow (`/admin/workflow`) — **7.5** (self 7.8 refused)

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Master–detail list + detail | **PASS** | `WorkflowPage.tsx` + `workflow-admin-layout` |
| Multi-object seeds (work-item ×2, change, problem) | **PASS** | `mock/workflow.ts` SEED |
| State track + initial mark | **PASS craft** | `workflow-admin-states__track` / `is-initial` |
| Transition table (key, from→to, fields, perms) | **PASS** | dense `data-table` |
| Active version toggle (one active per objectKey) | **PASS honest session** | `setWorkflowActiveVersion` sibling deactivation + fallback |
| Empty / error + mockHint | **PASS** | EmptyState, ErrorState, `workflowAdmin.mockHint` |
| Nav + crumb + palette + lazy | **PASS** | Sidebar, `AppShell` crumbMap, CommandPalette, router |
| i18n chrome en/ru/de | **PASS chrome / ding content** | UI strings translated; **seed name/description English-hardcoded** |
| Graph editor / live API / runtime coupling | **FAIL residual** | mock inspector only |
| Create / edit definition | **FAIL** | not present |

**R10 n/a → R11 7.5.** Gate **≥7.0 PASS**. Self **7.8** refused: no graph, no API, no runtime effect, EN seed content.

**Files:** `pages/Admin/WorkflowPage.tsx`, `mock/workflow.ts`, `styles/global.css` `.workflow-admin-*`, i18n `workflowAdmin.*`.

---

### Admin SLA (`/admin/sla`) — **7.4** (self 7.7 refused)

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Policy list + detail | **PASS** | `SlaPage.tsx` master–detail |
| Editable target / warning hours | **PASS** | draft targets + number inputs |
| Dirty footer + Save toast | **PASS craft** | `sla-admin-footer` + `useToast` |
| Working calendar panel | **PASS display** | Mon–Fri 09:00–18:00 `Europe/Moscow` |
| Pause states chips | **PASS** | `pauseStates` pills |
| Empty / error + mockHint | **PASS** | present |
| Policy enable/disable control | **FAIL residual** | Badge only — **no Toggle** |
| Holiday editor / multi-calendar | **FAIL** | `holidays: []` never UI |
| Live clock preview | **FAIL** | not present |
| Live API CRUD | **FAIL** | mock session store only |

**R10 n/a → R11 7.4.** Gate **≥7.0 PASS**. Self **7.7** refused: display-only enabled, no holidays, no preview, mock-only.

**Files:** `pages/Admin/SlaPage.tsx`, `mock/sla.ts`, CSS `.sla-admin-*`, i18n `slaAdmin.*`.

---

### Admin Audit (`/admin/audit`) — **7.2** (self 7.5 refused)

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Dense event table | **PASS** | time / actor / action / object / detail |
| Action filter chips | **PASS craft / ding truth** | chips from **seed** action keys always |
| Work-item deep link | **PASS partial** | `objectHref` only work-item |
| Loading / empty / error | **PASS** | Skeleton + EmptyState + ErrorState |
| Mock hint honesty | **PASS** | `audit.mockHint` |
| Live GET path | **PASS plumbing / ding product** | `fetchAuditEvents` → `/audit` when `!useMock` |
| Export / paging / date range / object filter | **FAIL** | not present |
| Problem/change/CI object links | **FAIL residual (S7)** | label text only |

**R10 n/a → R11 7.2.** Gate **≥7.0 PASS** (floor). Self **7.5** refused.

**Files:** `pages/Admin/AuditPage.tsx`, `api/audit.ts`, mock `auditEvents`, CSS `.audit-*`.

---

### Global Search (`/search`) — **7.8 usable** (self 8.0 refused)

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Full page + URL `?q=` / `?types=` | **PASS** | `SearchPage` + `useSearchParams` |
| `searchAll` mock multi-index | **PASS** | work-item, KB, CI, asset, problem, change |
| Live GET `/search` | **PASS plumbing** | `api/search.ts` |
| Type chips + Open action | **PASS** | `SEARCH_OBJECT_TYPES` + `searchHitPath` |
| Empty query / no results / loading / error | **PASS** | EmptyState + skeletons + ErrorState |
| Palette “Search all” + Enter → `/search?q=` | **PASS** | `CommandPalette` |
| Sidebar primary + crumb | **PASS** | Search in primary nav |
| Hit → entity deep route | **FAIL residual (S7)** | non–work-item → module root only |
| Facet counts = corpus | **FAIL residual** | counts from **current hits after type filter** |
| Result kbd / recents / highlight | **FAIL** | not present |

**Usable: YES.** Gate met. **Not 8.0** product search.

**Files:** `pages/Search/SearchPage.tsx`, `api/search.ts`, CommandPalette, Sidebar, CSS `.search-page*` / `.search-hit*`.

---

### Knowledge CMS — **9.0** (was 8.8; self 9.2 refused)

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Edit existing (title/tag/body/version note) | **PASS** | `ArticleReader` pencil → form → `updateKnowledgeArticle` |
| Publish pending | **PASS** | Publish primary when `status === 'pending'` |
| Version bump + note + updatedAt | **PASS** | store `version++`, `versionNote`, chips |
| Status filter All / Published / Pending + counts | **PASS craft** | `knowledge-status-filter` |
| Contribute still pending path | **PASS** | `addKnowledgeArticle` status pending |
| i18n en/ru/de (no locale ternaries) | **PASS** | edit/publish/filter/version keys |
| Session mock even when live | **PASS honest / ding durability** | `api/knowledge.ts` write → store always |
| List/topics live GET | **PASS** | `fetchKnowledgeArticles` / topics derive |
| Review queue / assignees / approvals | **FAIL residual (S10 partial)** | filter ≠ queue product |
| Use-in-ticket / KE→KB | **FAIL residual (S10)** | not present |
| Server-backed CMS | **FAIL** | no write API |
| Version history timeline | **FAIL** | number + note only |

**R10 8.8 → R11 9.0 (+0.2).** Preferred **process-CMS path** now yes. Self **9.2** refused hard.

**S10:** Partial close — editorial loop exists; dedicated queue + use-in-ticket remain open.

**Files:** `KnowledgePage.tsx`, `mock/store.ts` update/publish, `api/knowledge.ts`, CSS chips/filter, i18n.

---

### Work-item activity diffs — **dimensional (WorkItemDetail hold 9.0)**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| `before` / `after` on activity type | **PASS** | types + seed + `pushActivity` diffs |
| ActivityDiff UI (struck → after) | **PASS craft** | `ActivityDiff` in tab + stream |
| Field labels i18n | **PASS partial** | `workItem.fields.*` with raw fallback |
| Full field catalog / every action | **FAIL residual** | only when maps present |
| Self 7.8 activity score | **Dimensional OK** | do not invent surface total |

**WorkItemDetail R10 9.0 → R11 9.0** (depth polish, no gift).

---

### Live API depth — **plumbing held / residual honesty**

| Path | Verdict | Notes |
|------|:-------:|-------|
| Problems list/create/transition live | **PASS fix** | `/problems/{id}/transitions` + `target` (+ RCA fields) |
| Changes list/create/transition live | **PASS fix** | body maps `rollbackPlan`, `businessJustification`, enums |
| Changes/Problems live patch | **FAIL residual** | `{ ok: false, notFound }` |
| Bulk assign/status live | **FAIL residual (lie risk)** | returns `ids.length` without server work |
| Catalog UUID → request endpoint | **PASS** | `submitCatalogRequest` when UUID id |
| Notifications GET + map | **PASS craft / ding silent fallback** | error → mock seed without user signal |
| Knowledge write live | **Honest mock** | store path always |

**No critical surface score gifts** for API fixes alone. Catalog holds **9.0**. Shell holds **9.1** (S11 still open; live fetch ≠ notification center).

---

## Score table R10 → R11

| Surface | R8 | R9 | R10 | **R11** | Δ R10→R11 | Gate role | Notes |
|---------|---:|---:|----:|--------:|----------:|-----------|-------|
| **Overview** | 9.1 | 9.1 | 9.1 | **9.1** | 0 | Critical | Held |
| **MyWork** | 8.7 | 8.7 | 8.7 | **8.7** | 0 | — | Held |
| **Queues** | 9.1 | 9.1 | 9.1 | **9.1** | 0 | Critical | Still best surface |
| **Catalog** | 9.0 | 9.0 | 9.0 | **9.0** | 0 | Critical | Live request path; no 9.1 gift |
| **Knowledge** | 8.8 | 8.8 | 8.8 | **9.0** | **+0.2** | Secondary AAA | CMS loop; self 9.2 refused |
| **CMDB** | 8.9 | 9.0 | 9.0 | **9.0** | 0 | Secondary AAA | Held |
| **Assets** | 8.9 | 8.9 | 9.0 | **9.0** | 0 | Secondary AAA | Held list-ops preferred |
| **Problems** | 9.1 | 9.1 | 9.1 | **9.1** | 0 | Secondary AAA | Live transition fix ≠ process gift |
| **Changes** | 9.1 | 9.1 | 9.1 | **9.1** | 0 | Secondary AAA | Same |
| **Settings** | 7.0 | 7.0 | 8.0 | **8.0** | 0 | Admin | Held; S17/S18 open |
| **WorkItemDetail** | 9.0 | 9.0 | 9.0 | **9.0** | 0 | Critical | Activity diffs polish only |
| **Shell** | 9.1 | 9.1 | 9.1 | **9.1** | 0 | Critical | Search nav + notif live fetch; S11 open |
| **Reports** | 8.1 | 8.5 | 8.5 | **8.5** | 0 | Ops | Held |
| **Admin Metadata** | 6.7 | 6.7 | 7.5 | **7.5** | 0 | Admin | Held |
| **Admin Automation** | — | — | 7.3 | **7.3** | 0 | Admin | Held |
| **Admin Workflow** | — | — | — | **7.5** | new | Admin | ≥7.0; self 7.8 refused |
| **Admin SLA** | — | — | — | **7.4** | new | Admin | ≥7.0; self 7.7 refused |
| **Admin Audit** | — | — | — | **7.2** | new | Admin | ≥7.0 floor; self 7.5 refused |
| **Global Search** | — | — | — | **7.8** | new | Discoverability | Usable; self 8.0 refused |

### Dimensional (not surface totals)

| Dimension | Wave self | **R11 critic** | Inflated? |
|-----------|----------:|---------------:|:---------:|
| Knowledge CMS loop | **9.2** surface | **9.0** surface | **Yes (−0.2)** |
| Activity diffs | **7.8** | **~7.5 craft / WID 9.0 hold** | Mild path overclaim OK dimensional |
| Live API “wired” | claimed | **plumbing only** | Do not launder into module scores |

### Aggregates

| Aggregate | R10 | **R11** | Δ |
|-----------|----:|--------:|--:|
| Average (prior 15 surfaces*) | ~8.85 | **~8.87** | **+0.02** (Knowledge +0.2 only) |
| Average (critical five) | ~9.06 | **~9.06** | 0 |
| Average (secondary five†) | ~9.0 | **~9.04** | Knowledge +0.2 |
| Secondary five min | 8.8 | **9.0** | Knowledge floor lifts |
| Secondary preferred (≥9 count) | 4/5 | **5/5** | Knowledge joins preferred on **process-CMS path** |
| New admin avg (WF+SLA+Audit) | — | **~7.37** | all ≥7.0 |
| Search usable | — | **Yes (7.8)** | |

\*R10 set excluding new R11-only surfaces for like-for-like.  
†Assets, Problems, Changes, CMDB, Knowledge

### Secondary AAA checklist

| Module | R11 | ≥8.5? | ≥9 preferred? |
|--------|----:|:-----:|:-------------:|
| Assets | **9.0** | Yes | **Yes (list-ops)** |
| Problems | **9.1** | Yes | **Yes (process)** |
| Changes | **9.1** | Yes | **Yes (process + light ops)** |
| CMDB | **9.0** | Yes | **Yes (relation-ops)** |
| Knowledge | **9.0** | Yes | **Yes (process-CMS path)** |
| **Suite secondary AAA** | — | **PASS** | **PASS (5/5 preferred)** |

### Critical five hold

| Surface | R11 | ≥9? |
|---------|----:|:---:|
| Overview | 9.1 | Yes |
| Queues | 9.1 | Yes |
| WorkItemDetail | 9.0 | Yes |
| Shell | 9.1 | Yes |
| Catalog | 9.0 | Yes |

### New admin / discoverability gate

| Surface | R11 | ≥7.0? | Usable? |
|---------|----:|:-----:|:-------:|
| Workflow | **7.5** | **Yes** | Demo inspector yes |
| SLA | **7.4** | **Yes** | Hour edit yes |
| Audit | **7.2** | **Yes** | Read list yes |
| Global Search | **7.8** | n/a (≥ usable) | **Yes** |

---

## Self-score honesty (wave docs vs critic)

| Claim | Wave self | **R11 critic** | Inflated? |
|-------|----------:|---------------:|:---------:|
| Workflow admin | **7.8** | **7.5** | **Yes (−0.3)** — matrix real; engine cosplay |
| SLA admin | **7.7** | **7.4** | **Yes (−0.3)** — hours real; enable/holiday theatre gaps |
| Knowledge | **9.2** | **9.0** | **Yes (−0.2)** — CMS loop real; durability/suite not |
| Global search | **8.0** | **7.8** | **Yes (−0.2)** — usable; deep-link + facet tax |
| Admin audit | **7.5** | **7.2** | **Yes (−0.3)** — seed table, not compliance |
| Activity diffs | **7.8** | dimensional ~**7.5** | Mild |
| Live problems/changes/catalog/notif | “wired” | **plumbing accepted** | Honest as engineering claim |

Inflation discipline: **several surfaces >0.2 over critic — all cut.** Knowledge hits the 0.2 line; admin claims hit 0.3 and are refused harder.

---

## Blind A/B — unlabeled operator UX

Compare **Vox** vs latest **Naumen ITSM** / **ServiceNow Agent Workspace** class desks without brand labels.

### (a) L1 triage — 2-hour shift

**Task:** Claim queue, sort by SLA urgency, work breached → at-risk → unassigned, brief assist, open detail (now with activity diffs), update fields, optional global search mid-shift, catalog request, notifications.

| Dimension | Winner | Why |
|-----------|--------|-----|
| Queue scan density + bulk | **Vox** | OperatorGrid still cleaner |
| Live SLA urgency | **Vox slight** | Unchanged runtime |
| Workbench + activity truth | **Tie / Vox polish** | Diffs help; enterprise field history deeper |
| Mid-shift find-anything | **Tie / Vox improved** | Full `/search` usable; enterprise facets/recents still deeper |
| Catalog request | **Tie / Vox** | Form + toast path held |
| Notification interrupt | **Enterprise slight** | Live GET exists; still no center (S11); silent fallback |
| **2h L1 desk pick** | **Vox** | Wave5 does not hurt L1; search + diffs slight assist |

**Honest summary (2h):** Unlabeled L1 still **picks Vox**.

### (b) Multi-module — 8-hour full desk

**Task:** Queues + incidents + problems (RCA/KE) + normal change through CAB + ModuleGrid bulk + **Knowledge CMS** (edit/publish) + CMDB + assets + Reports + Settings + Automation inspect + **Workflow/SLA admin** + **Audit** + **Search** + Catalog + refresh mid-shift.

| Dimension | Winner | Why |
|-----------|--------|-----|
| Premium polish | **Vox** | System chrome still ahead |
| L1 queue path | **Vox** | Same as (a) |
| Knowledge authoring | **Tie / Enterprise slight** | Vox finally has session CMS; enterprise multi-user review + server durability + use-in-ticket still win a full shift |
| Workflow / SLA admin product | **Enterprise** | Vox mock inspectors vs real definition engines + calendars + runtime binding |
| Audit / compliance trail | **Enterprise** | Vox seed table vs immutable enterprise audit with export |
| Global search product | **Enterprise slight** | Vox usable page; enterprise entity deep-link + facets |
| Change / CAB process | **Tie / Enterprise slight** | S9 quorum still open |
| CMDB / discovery | **Enterprise (narrower)** | Unchanged |
| Automation rules | **Enterprise** | Mock WHEN/IF/THEN held |
| Settings / notif routing | **Enterprise** | S11/S17 |
| Reports / export | **Enterprise (narrower)** | Vox 8.5 snapshot |
| Hierarchy / deep links | **Enterprise** | S7 still open — search makes this **more** painful (open → wrong grain) |
| **8h multi-module desk pick** | **Enterprise desks** | Margin **slightly narrower than R10** (KB CMS + search + admin WF/SLA); **not flipped** |

### Which looks better unlabeled

| Scenario | Blind winner | Confidence |
|----------|--------------|------------|
| **(a) L1 triage 2h** | **Vox** | High |
| **(b) Multi-module 8h** | **Enterprise (Naumen / ServiceNow Workspace class)** | High — margin reduced by KB CMS + search + WF/SLA admin craft |
| **Guided secondary + admin walkthrough** | **Vox competitive / demo-tie** | Medium-high |
| **Polish-only screenshot A/B** | **Vox** | High |
| **Admin workflow/SLA 45m real config** | **Enterprise** | High — Vox is mock-honest |
| **KB editorial shift 2h multi-author** | **Enterprise** | High — Vox session CMS |
| **Find entity from search hit** | **Enterprise slight** | High — S7 deep-link tax |

**Why enterprise still wins 8h:** Server-backed KB workflow, runtime-bound workflow/SLA engines, immutable audit export, discovery-fed CMDB, multi-week change calendars, real automation execution, entity-deep search, multi-user notification centers. Wave5 closed **discoverability emptiness**, **admin engine surface vacuum**, and **KB no-edit insult**. It did not invent suite durability or engine binding.

---

## Per-surface harsh notes (focus + hold)

### Knowledge — **9.0** (was 8.8) ★ largest secondary lift

**Credit:**  
- Real authoring loop: pencil edit, validation, version note, save toast, pending publish.  
- Status filter with live counts is the minimum editorial chrome that was missing for years of critic rounds.  
- Pending/published/version chips + reader meta are coherent, not orphaned badges.  
- Store mutations are non-trivial (summary derive, readMinutes, version++).  

**Ding (why not 9.1 / 9.2):**  
- Self **9.2** refused.  
- CMS is **session mock** forever on write — live mode is a split brain (list may be API; edits are local ghosts).  
- Publish is one-click **no review notes, no reviewer, no ACL**.  
- No version timeline, no diff of article revisions, no rollback.  
- S10 use-in-ticket / KE→KB still open — Problems still cannot mint KB.  
- Contribute + edit still plain textarea — not structured KB sections / attachments.

**9.0 means:** preferred **process-CMS path** for a single-operator demo desk. **Not** “enterprise knowledge management.”

### Workflow admin — **7.5** / SLA admin — **7.4**

**Credit:** Same master–detail grammar as Metadata/Automation; real transition matrix; meaningful multi-version toggle logic; SLA dirty/save is more “admin” than Automation’s enable-only flip; calendar panel is readable; focus rings and dense tables meet craft bar.

**Ding:**  
- Mock only; mockHint is honesty, not a free pass to 7.7+.  
- Workflow activation **does not change the product**.  
- SLA enabled is a **lie badge** (cannot disable).  
- Hard-coded English seed labels in a tri-locale product.  
- No graph, no holidays, no sim clock, no import/export.

### Global Search — **7.8 usable**

**Credit:** First-class route, URL state, abort, multi-type mock index, palette bridge, empty states. This ends “command palette is the only search.”

**Ding:**  
- Opening a problem hit lands on **Problems list**, not the problem — operator tax that enterprise desks solved a decade ago.  
- Facet counts are a **UI toy** after client type filter.  
- No keyboard result list, no highlighting of match terms, no “did you mean.”

### Admin Audit — **7.2**

**Credit:** Dense, scannable, filtered, i18n action labels, relative time with absolute title.

**Ding:** Seed cosplay. Chip keys ignore live. No export. Barely clears 7.0 because craft of the table is competent, not because the product is audit-complete.

### Critical path + held secondaries

| Check | Result |
|-------|:------:|
| Queues OperatorGrid + bulk + predicates | **HOLD** |
| Overview live metrics + copilot | **HOLD** |
| WorkItemDetail workbench + DynamicForm + activity diffs | **HOLD (+ polish)** |
| Shell notifications UI depth (S11) | **HOLD residual** (live GET ≠ center) |
| Catalog browse polish | **HOLD 9.0** |
| Reports 8.5 honesty | **HOLD** |
| CMDB 9.0 relation-ops | **HOLD** |
| Knowledge preferred CMS path | **NEW preferred (9.0)** |
| Changes CAB / bulk gates (S13) | **HOLD** |
| S3b ModuleGrid | **HOLD CLOSED** |
| Settings 8.0 | **HOLD** |
| C1 type floor | **HOLD** |

---

## Critical / craft defect register

### C1–C8 class

**None new. None reopened.**

### Residual register

| ID | R10 | R11 |
|----|-----|-----|
| **S3** Secondary bulk class | CLOSED | **CLOSED** |
| **S3b** ModuleGrid list craft | CLOSED | **CLOSED** |
| **S4** CAB silent approve | CLOSED | **CLOSED** |
| **S5** Knowledge toast theatre | CLOSED | **CLOSED** |
| **S6** Metadata crumb | CLOSED | **CLOSED** (+ workflow/sla/audit crumbs present) |
| **S7** Deep-link module detail routes | Open | **Open** — **worsened pain via Search** (module-root hits) |
| **S8** Asset free-text assignee | Open | **Open** |
| **S9** CAB quorum / full calendar | Partial | **Partial** |
| **S10** KB editorial queue / use-in-ticket | Open | **Partial** — CMS loop + status filter; queue product + use-in-ticket still open |
| **S11** Notification center depth | Open | **Open** (live GET + silent mock fallback; still no center; prefs unwired) |
| **S12** Conflict raw CI ids | CLOSED | **CLOSED** |
| **S13** Bulk change plan/backout | CLOSED | **CLOSED** |
| **S14** Reports CSAT not filtered-set truth | Open P2 | **Open P2** |
| **S15** Reports SLA history filter-blind | Open P2 | **Open P2** |
| **S16** Bulk skip: no per-row block reasons | Open P2 | **Open P2** |
| **S17** Settings notif prefs no product consumer | Open P2 | **Open P2** |
| **S18** Settings appearance dark/HC tokens | Open P2 | **Open P2** |
| **S19** Catalog drawer success UI unmounted | Open P3 | **Open P3** |
| **S20** (new) Search non–work-item hits lack entity deep-link | — | **Open P1** (subset of S7) |
| **S21** (new) Workflow/SLA admin mock unbound from runtime engines | — | **Open P2** |
| **S22** (new) SLA policy enabled badge non-interactive | — | **Open P3** |
| **S23** (new) Live bulk assign/status returns success count without server work | — | **Open P1 honesty** |
| **S24** (new) Notifications live failure silently falls back to mock seed | — | **Open P2** |
| **S25** (new) Knowledge CMS write path not server-backed (split brain live) | — | **Open P1 durability** |
| **S26** (new) Audit filter keys always derived from mock seed | — | **Open P3** |

---

## Remaining backlog (post-PASS → multi-module *tie* closer)

### P1 — bake-off margin

1. **S7 / S20:** Deep routes `/problems/:id`, `/changes/:id`, `/assets/:id`, `/knowledge?article=`, `/cmdb?ci=` — **search Open must land on entity**.  
2. **S10 remainder:** Use-in-ticket from work item; KE→KB from Problems; optional review assignee.  
3. **S25:** Backend KB write API or hard-disable CMS in live mode with honest banner (no split brain).  
4. **S23:** Live bulk must call server or refuse with toast — never fake `ids.length`.  
5. **S11 + S17 + S24:** Notification center route; wire Settings prefs; surface live fetch failure instead of silent mock.

### P2 — polish / admin truth

6. **S21:** Bind Workflow admin active version to transition menus; bind SLA targets to work-item SLA chrome (or label “preview only” forever).  
7. Workflow visual graph (even read-only SVG).  
8. SLA enable toggle + holiday list + “sample clock” preview.  
9. Search: corpus facets, match highlight, result J/K, recent queries.  
10. Audit: date range, export CSV, live action keys, object-type filter.  
11. S8 asset assignee picker; S9 CAB quorum; S14–S16 Reports/bulk honesty.  
12. S18 Settings dark/HC appearance tokens; S19 Catalog drawer success race.

### P3

13. S22 enable badge or remove tone that implies control.  
14. S26 audit chip source of truth.  
15. Visual regression CI per `quality-gates.md`.

---

## What improved R10 → R11 (credit — real)

- **Knowledge 8.8 → 9.0** — real CMS loop (edit / publish / version note / status filter); secondary preferred now **5/5**.  
- **Admin Workflow 7.5** + **SLA 7.4** — new Metadata-class inspectors with transition matrix and hour edit.  
- **Admin Audit 7.2** — light trail with action filter.  
- **Global Search 7.8 usable** — full page + palette + multi-type mock index.  
- **Activity diffs** on WorkItemDetail — before/after when present.  
- **Live API honesty fixes** — transition path/body, catalog request UUID path, notifications GET.  
- **Nav completeness** — Search primary; Workflow / SLA / Audit under Management with crumbs + palette.  
- **Critical five held ≥9** with zero regression found in spot-check.

PASS is “admin ≥7.0 + Knowledge improved + search usable + no critical regression + inflation refused.”  
PASS is **not** “Vox wins unlabeled 8h multi-module desk” and **not** “Knowledge 9.2 / Search 8.0 / engines productized.”

---

## Viewport notes (R11)

| Viewport | Assessment |
|----------|------------|
| **1440** | Workflow/SLA two-pane solid; search form + hit cards clean; Knowledge status chips + reader OK |
| **1024** | Admin panes still readable; search chips wrap; Knowledge rail tight |
| **768** | Workflow/SLA stack (`@media` single column); search hits full-width; audit table horizontal scroll risk |
| **320** | Admin list-first OK; search usable but dense chips wrap hard; Knowledge reader modal full-bleed; audit painful |

---

## Final call

| Gate | R10 | **R11** |
|------|-----|---------|
| Critical surfaces ≥9 | **PASS** | **PASS (held)** |
| No C1–C8 critical defects | **PASS** | **PASS** |
| Secondary five each ≥8.5 | **PASS** | **PASS** (min **9.0**) |
| Secondary preferred ≥9 | **PASS (4)** | **PASS (5 — +Knowledge process-CMS)** |
| New admin ≥7.0 (WF / SLA / Audit) | — | **PASS (7.5 / 7.4 / 7.2)** |
| Knowledge improved | 8.8 | **PASS (9.0)** |
| Search usable | — | **PASS (7.8)** |
| Catalog critical hold | 9.0 | **PASS (9.0)** |
| Blind A/B L1 2h | **Vox wins** | **Vox wins** |
| Blind A/B multi-module 8h | Enterprise wins | **Enterprise still wins (slightly narrower)** |
| Self-score honesty | Harsh R10 cuts | **Harsh cuts: KB −0.2, WF −0.3, SLA −0.3, Search −0.2, Audit −0.3** |
| **Elevation verdict** | R10 PASS | **PASS (Wave 5 real; inflation refused; bake-off not flipped)** |

---

### PASS/FAIL + scores (executive)

**Verdict: PASS** — Workflow **7.5**, SLA **7.4**, Audit **7.2** (all ≥7.0); Knowledge **9.0** (improved; self 9.2 refused); Search **7.8 usable**; critical five held; multi-module 8h still enterprise.

| Secondary (gate set) | R10 | **R11** | ≥8.5 | ≥9 |
|----------------------|----:|--------:|:----:|:--:|
| Assets | 9.0 | **9.0** | Yes | **Yes (list-ops)** |
| Problems | 9.1 | **9.1** | Yes | **Yes** |
| Changes | 9.1 | **9.1** | Yes | **Yes** |
| CMDB | 9.0 | **9.0** | Yes | **Yes (process)** |
| Knowledge | 8.8 | **9.0** | Yes | **Yes (process-CMS)** |
| **Secondary average** | ~9.0 | **~9.04** | Pass | **5/5 preferred** |

| Critical five | R11 |
|---------------|----:|
| Overview | **9.1** |
| Queues | **9.1** |
| WorkItemDetail | **9.0** |
| Shell | **9.1** |
| Catalog | **9.0** |
| **Critical average** | **~9.06** |

| Other / new | R10 | **R11** |
|-------------|----:|--------:|
| MyWork | 8.7 | **8.7** |
| Reports | 8.5 | **8.5** |
| Settings | 8.0 | **8.0** |
| Admin Metadata | 7.5 | **7.5** |
| Admin Automation | 7.3 | **7.3** |
| Admin Workflow | — | **7.5** |
| Admin SLA | — | **7.4** |
| Admin Audit | — | **7.2** |
| Global Search | — | **7.8** |

### Blind winners (unlabeled)

| Scenario | Winner |
|----------|--------|
| **L1 triage 2h** | **Vox** |
| **Multi-module 8h** | **Enterprise (Naumen / ServiceNow Workspace class)** |
| **Polish screenshot** | **Vox** |
| **Guided Knowledge CMS + Search + WF/SLA admin demo** | **Vox competitive / demo-tie** |
| **Admin engine config 45m real** | **Enterprise** |
| **KB multi-author editorial shift** | **Enterprise** |

**Can Vox win multi-module desk?**  
**No.** Closer than R10: session Knowledge CMS, usable global search, Workflow/SLA admin craft, audit list, activity diffs, live API path fixes. Enterprise still takes the unlabeled 8-hour multi-module shift on durability, engines, deep-links, audit, discovery, and multi-user editorial.

**Which looks better unlabeled?**  
- **2h L1:** **Vox**.  
- **8h multi-module:** **Enterprise**.  
- **Screenshot polish:** **Vox**.

**Why PASS is honest:** All three new admin surfaces clear **≥7.0** with Metadata-class craft verified in live components; Knowledge **improved to 9.0** on a real edit/publish/filter loop (not chip theatre); search is **usable** as a first-class page; critic refused **0.2–0.3** self-inflation across the board; critical five unregressed; bake-off flip refused.
