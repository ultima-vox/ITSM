# Vox ITSM — Visual / Operator UX Critic Round 7 (Naumen parity wave: CAB · KB votes · durable store)

**Date:** 2026-07-31  
**Scope:** Live code under `G:\ITSM\frontend\src` after R6 PASS + Changes/KB parity pass (`docs/ux/changes-kb-parity.md`).  
**Inputs:** `docs/ux/visual-critic-round6.md`, `docs/ux/changes-kb-parity.md`, live pages/store/components/i18n/CSS.

**Focus surfaces this wave:**  
Changes CAB · Knowledge votes · durable `localStorage` mock store · SLA tick · copilot · DynamicForm · notifications  

**Regression scan:** Overview · Queues · WorkItemDetail · Shell · Catalog · Problems · CMDB · Assets  

**Viewports mentally reviewed:** 1440 · 1024 · 768 · 320

**Elevation bar (this round):**  
- **Changes ≥ 9.0** (close R6 residual **S4** — preferred process path, not silent `cabApproved`).  
- Knowledge must leave toast-theatre floor (**S5**).  
- Critical five must stay **≥ 9.0**.  
- **No** multi-module bake-off victory claim unless product depth actually beats enterprise desks.  
- Self-score inflation ≤ 0.2 vs critic.

---

## Verdict: **PASS**

Round 7 closes the **Changes preferred bar** with a real CAB product surface in live code, and closes Knowledge feedback theatre with store-mutating votes. Multi-module 8h still loses honestly to enterprise desks — narrower margin, not a flip.

### Why PASS (non-negotiable checklist)

| Requirement | Result | Evidence |
|-------------|:------:|----------|
| Changes ≥ 9.0 (preferred) | **Yes (9.0)** | Explicit chair approve/reject; member votes; normal schedule blocked without `cabApproved`; emergency banner + activity; no silent flip on schedule for normal |
| Knowledge above toast theatre | **Yes (8.8)** | `voteKnowledgeArticle` mutates `helpfulYes`/`helpfulNo`/`helpfulScore`; list + reader re-bind; contribute → pending article in store |
| Durable demo state | **Yes** | `vox-itsm-store-v1` hydrate/persist (debounce 350ms); Settings reset reseed |
| Critical five ≥ 9.0 | **Held (+shell/overview micro)** | Queues still best; shell notifications event-driven; Overview copilot store-backed |
| New C1–C8 critical defects | **None** | — |
| Multi-module 8h honesty | **Enterprise still wins** | Durable mock ≠ enterprise CAB calendar / CMS / discovery / assignment model |

### Why this is not a rubber stamp

1. **Changes 9.0 is process-path preferred, not CAB suite.** Two hard-coded seats (Maria / Dmitry), no quorum rule (chair can approve with zero votes), no calendar, no freeze-window conflict vs CMDB, no assignment groups, still drawer-only table (no OperatorGrid bulk). Same honesty frame as Problems R6: **9.0 process, not 9.2 product suite.**
2. **Knowledge 8.8 not 8.9+.** Votes are real; contribute is still pending-chip mock (no review queue, no KE→KB publish, seed bodies often i18n keys). Self-doc **8.9** is 0.1 optimistic.
3. **Durability is demo-grade localStorage**, not multi-user authority or server of record. SLA tick is a 30s client countdown — excellent for L1 theatre-of-urgency, not a true SLA engine (business calendars, pause conditions, per-priority targets).
4. **Copilot is scripted briefing from live queue stats**, not generative operator agent. Credit for non-lorem content; do not market as AI workstation.
5. **DynamicForm is a light metadata renderer** (CEL visibility = literal `"false"` only). Real for create + detail field chrome; not a full form-engine product.
6. **S3 residual still open** — secondary modules still plain `<table>`, not OperatorGrid. Preferred scores on Problems/Changes are **not** list-craft scores.

**What FAIL would still look like:** silent `cabApproved` on schedule for normal; helpful vote toast-only with frozen %; Changes stuck at 8.8; critical-path regression; claiming multi-module bake-off win.

---

## P0 / residual verification (R6 → R7) — live code

### S4 CAB thin — **CLOSED** (earns Changes 9.0)

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Explicit approve / reject (not silent on schedule) | **PASS** | `setChangeCabDecision` sets `cabApproved` / `cabRejected`; activity `cab_approved` / `cab_rejected` |
| Normal schedule requires explicit approve | **PASS** | `transitionChange` → `changes.validation.cabApprovalRequired` when `type === 'normal' && !cabApproved` |
| Normal draft → scheduled blocked | **PASS** | Store + UI filter hide/block; `cabRequired` |
| Rejected CAB blocks schedule | **PASS** | `cabRejected` → `changes.validation.cabRejected` |
| No silent flip for normal on schedule | **PASS** | Only **standard** auto-`cabApproved` on schedule (policy pre-approve — acceptable) |
| Emergency skip with warning | **PASS** | Banner `module-cab-banner` + activity `scheduled_emergency` when emergency schedules without approve |
| CAB panel UI | **PASS** | Risk select, notes, member list, vote buttons, chair actions, approved/rejected chips |
| Member vote simulation | **PASS** | `castCabMemberVote` mutates `cabVotes` + activity |
| Plan / backout still gated | **HOLD** | Unchanged schedule validators |
| `aria-sort` on Changes headers | **PASS** | Number / Type / Status / Risk / Window |
| CAB calendar / bulk CAB board / CMDB conflict | **FAIL (residual)** | Not in scope; caps score at 9.0 not 9.3+ |

**Files:** `mock/store.ts` (`setChangeCabDecision`, `castCabMemberVote`, `transitionChange`), `pages/Changes/ChangesPage.tsx` (CAB panel + gates), `styles/global.css` (`.module-cab*`), i18n `changes.cab.*`, `api/changes.ts`, `types` CabVote.

**Inflation guard:** R6 dinged Changes for *silent* approve. Explicit chair action + schedule gate is exactly the 9.0 claim. Do not rewrite as “enterprise CAB.”

### S5 Knowledge feedback toast — **CLOSED** (score 8.5 → 8.8)

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Yes/No mutates score | **PASS** | `voteKnowledgeArticle` increments yes/no, `recomputeHelpfulScore` |
| Persist in durable store | **PASS** | Knowledge articles + `knowledgeVotes` in `snapshotStore` / hydrate |
| List re-renders new % | **PASS** | `subscribeKnowledge` + card bind `helpfulScore` |
| Reader live % + vote count | **PASS** | `aria-live` score + `knowledge.voteCount`; pulse class `is-updated` |
| Toast secondary only | **PASS** | Toast confirmation after mutation |
| Contribute writes article | **PASS** | `addKnowledgeArticle` pending at top of list |
| Full CMS / versioning / KE→KB | **FAIL (residual)** | Caps Knowledge well below preferred 9 |

**Self-doc claimed 8.9; critic 8.8.** One-shot vote (cannot change mind), seed body i18n residual, no editorial queue UI beyond pending chip.

### Durable localStorage store — **REAL (demo class)**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Persist WI / CI / assets / problems / changes / knowledge | **PASS** | `PersistedMockStore` v1 full snapshot |
| Hydrate on load | **PASS** | Module load `hydrateFromStorage` else reseed + persist |
| Debounced save | **PASS** | 350ms after `notify` path |
| Operator reset | **PASS** | Settings `resetDemoData` clears key + reseed + notify all listeners |
| Multi-tab / multi-user | **FAIL** | Single-browser demo durability only |

**Credit:** This is the single largest *demo honesty* upgrade since critical-path toast purge. Refresh no longer erases CAB votes, KB %, assignments, comments. Still mock authority — say so.

### SLA tick — **REAL (client urgency)**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| 30s ticker | **PASS** | `startSlaTicker` / `SLA_TICK_MS = 30_000` on load |
| Countdown decrement | **PASS** | `tickSlaClocks` parses `HH:MM` / `-HH:MM`, −1 min per tick |
| State flip on_track → at_risk → breached | **PASS** | ≤60 → at_risk; ≤0 → breached; skips resolved/closed/met |
| Does not spam `updatedAt` | **PASS** | Explicit comment + omit bump |
| Business-hour SLA engine | **FAIL** | Cosmetic clock for operator urgency UX |

**Operator impact:** L1 2h shift feels *alive*; Queues/Overview/notifications can show movement without user action. Credit for craft; do not claim ITIL SLA product.

### Copilot — **IMPROVED (scripted, store-true)**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Briefing from live queue stats | **PASS** | `getQueueCopilotStats` + `buildMockCopilotContent` |
| Overview UI + loading/error | **PASS** | `copilot__response`, aria-live, suggestion chips navigate queues |
| Command palette entry | **PASS** | `/?copilot=1` |
| Generative multi-turn agent | **FAIL** | Mock model `vox-operator-brief-v1`; requiresHumanReview always |

**Unlabeled:** Better than static marketing copy. Still not ServiceNow Virtual Agent / Now Assist depth. L1 briefing assist: **Vox competitive**.

### DynamicForm — **HOLD / craft credit (not new AAA)**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Metadata sections → fields | **PASS** | Create modal + WorkItemDetail details tab |
| Enum / rich text / required | **PASS** | FORM_FIELD_META + Select/Textarea/Input |
| Save-on-blur commit | **PASS** | `onCommit` detail path |
| CEL visibility engine | **STUB** | Only hides when `visibleWhen.source === 'false'` |
| Full form designer UX | **FAIL** | Admin metadata still thin read-only |

No surface score inflation solely for DynamicForm existence — it was already structural craft. Documented so parity reviewers do not invent it as net-new AAA.

### Notifications — **REAL event coupling**

| Claim | Verdict | Live evidence |
|-------|:-------:|---------------|
| Header menu + unread dot | **PASS** | `NotificationMenu` |
| Seed + mark read durable | **PASS** | `vox-notif-read` localStorage |
| React to assign / SLA transitions | **PASS** | `subscribeWorkItems` diff → push breach/at_risk/assign |
| Full notification center page | **FAIL** | “View all” → `/my-work` (not a real center) |
| CAB / KB events in bell | **FAIL** | Work-item store only |

**Shell craft:** Bell is no longer pure seed wallpaper. SLA tick → state flip → unread breach is a real L1 loop. Still shallow vs enterprise notification preferences / channels.

---

## Score table R6 → R7

| Surface | R5 | R6 | **R7** | Δ R6→R7 | Gate role | Notes |
|---------|---:|---:|-------:|--------:|-----------|-------|
| **Overview** | 9.0 | 9.0 | **9.1** | **+0.1** | Critical | Copilot briefing bound to live stats + actionable suggestions |
| **MyWork** | 8.7 | 8.7 | **8.7** | 0 | — | Held |
| **Queues** | 9.1 | 9.1 | **9.1** | 0 | Critical | Still best surface; SLA tick feeds data, grid craft unchanged |
| **Catalog** | 9.0 | 9.0 | **9.0** | 0 | Critical | Held |
| **Knowledge** | 8.5 | 8.5 | **8.8** | **+0.3** | Secondary AAA | Votes + contribute mutate durable store |
| **CMDB** | 8.4 | 8.8 | **8.8** | 0 | Secondary AAA | No CMDB work this wave |
| **Assets** | 8.5 | 8.7 | **8.7** | 0 | Secondary AAA | Held |
| **Problems** | 8.6 | 9.0 | **9.0** | 0 | Secondary AAA | Preferred held |
| **Changes** | 8.6 | 8.8 | **9.0** | **+0.2** | Secondary AAA | **Preferred bar closed (CAB)** |
| **Settings** | 6.8 | 6.8 | **7.0** | **+0.2** | — | Demo reset + durable store operator control |
| **WorkItemDetail** | 9.0 | 9.0 | **9.0** | 0 | Critical | DynamicForm hold (not net-new) |
| **Shell** | 9.0 | 9.0 | **9.1** | **+0.1** | Critical | Event-driven notification center |
| **Reports** | 7.3 | 7.3 | **7.3** | 0 | — | Snapshot |
| **Admin Metadata** | 6.7 | 6.7 | **6.7** | 0 | — | Read-only; crumb gap still open |

### Aggregates

| Aggregate | R6 | **R7** | Δ |
|-----------|---:|-------:|--:|
| Average (all scored) | ~8.5 | **~8.6** | **+0.1** |
| Average (critical five) | ~9.0 | **~9.06** | **+0.06** |
| Average (secondary five*) | ~8.8 | **~8.86** | **+0.06** |
| Secondary five min | 8.5 | **8.7** | +0.2 |
| Secondary preferred (≥9 count) | 1/5 | **2/5** | Problems + **Changes** |

\*Assets, Problems, Changes, CMDB, Knowledge

### Secondary AAA checklist

| Module | R7 | ≥8.5? | ≥9 preferred? |
|--------|---:|:-----:|:-------------:|
| Assets | **8.7** | Yes | No |
| Problems | **9.0** | Yes | **Yes** |
| Changes | **9.0** | Yes | **Yes (new)** |
| CMDB | **8.8** | Yes | No |
| Knowledge | **8.8** | Yes | No |
| **Suite secondary AAA** | — | **PASS** | **PASS (2 preferred)** |

### Critical five hold

| Surface | R7 | ≥9? |
|---------|---:|:---:|
| Overview | 9.1 | Yes |
| Queues | 9.1 | Yes |
| WorkItemDetail | 9.0 | Yes |
| Shell | 9.1 | Yes |
| Catalog | 9.0 | Yes |

---

## Self-score honesty (parity doc vs critic)

| Surface | R6 critic | Parity self | **R7 critic** | Inflated? |
|---------|----------:|------------:|--------------:|:---------:|
| Changes | 8.8 | **9.0** | **9.0** | No — matches; barely preferred |
| Knowledge | 8.5 | **8.9** | **8.8** | **−0.1** self overclaim |

Parity doc inflation guard on Changes is **accepted**. Knowledge self **8.9** trimmed to **8.8**.

---

## Blind A/B — unlabeled operator UX

Compare **Vox** vs latest **Naumen ITSM** / **ServiceNow Agent Workspace** (and peer enterprise desks) without brand labels. Two scenarios.

### (a) L1 triage — 2-hour shift

**Task:** Claim queue, sort by SLA urgency, work breached → at-risk → unassigned, use brief assist, open detail, update fields, watch notifications.

| Dimension | Winner | Why |
|-----------|--------|-----|
| Queue scan density + bulk | **Vox** | OperatorGrid + predicates + saved views still cleaner than typical enterprise list chrome |
| Live SLA urgency | **Vox slight** | 30s tick + breach notifications create movement enterprise mocks often freeze in demos |
| Workbench field edit | **Tie / Vox polish** | DynamicForm + detail craft; enterprise wins depth of form rules |
| Shift briefing assist | **Tie** | Vox copilot is honest live-stats brief; enterprise AI is deeper but heavier |
| Notification interrupt | **Enterprise slight** | Vox bell works for assign/SLA; enterprise has preference models, multi-channel, fuller center |
| Premium chrome | **Vox** | Unchanged |
| **2h L1 desk pick** | **Vox** | Wins unlabeled L1 on scan speed + living SLA pressure + polish |

**Honest summary (2h):** An L1 who only lives in queue + detail + overview still **picks Vox** (or ties high). CAB/KB depth does not matter here; durability + SLA tick + notif coupling **help** Vox’s L1 story.

### (b) Multi-module — 8-hour full desk

**Task:** Queues + incidents + problems (RCA/KE) + normal change through CAB + schedule + knowledge vote/contribute + CMDB impact + assets related graph + refresh mid-shift.

| Dimension | Winner | Why |
|-----------|--------|-----|
| Premium polish | **Vox** | System chrome still ahead of most enterprise skins |
| L1 queue path | **Vox** | Same as (a) |
| Change / CAB process | **Tie / Enterprise slight** | Vox now has **real** approve/reject + votes + schedule gates — credible normal-change path. Enterprise still has CAB boards, calendars, risk calculators, conflict windows |
| Knowledge authoring | **Enterprise** | Vox votes + pending contribute ≠ CMS, versioning, approvals, portal publish |
| CMDB / discovery | **Enterprise** | Vox selection BFS + orphans honest; no discovery/edit relations |
| Cross-module graph | **Enterprise (narrower)** | Vox human related + durable mid-shift state; still seeded shallow graph |
| Persistence across refresh | **Tie / Vox demo win** | localStorage durability beats “session demo amnesia”; loses to real multi-user servers |
| Secondary list bulk ops | **Enterprise** | S3 still open — no ModuleGrid bulk |
| Hierarchy / IA | **Enterprise slight** | Critical path AAA; secondary still thinner than Queues |
| **8h multi-module desk pick** | **Enterprise desks** | Margin **narrower than R6**; still not Vox’s unlabeled full-shift home |

### Which looks better unlabeled — operator UX call

| Scenario | Blind winner | Confidence |
|----------|--------------|------------|
| **(a) L1 triage 2h** | **Vox** | High |
| **(b) Multi-module 8h** | **Enterprise (Naumen / ServiceNow Workspace class)** | High — margin reduced by CAB + durable store + KB votes |
| **Guided secondary walkthrough (PRB + CHG CAB + CMDB impact)** | **Vox competitive / demo-tie** | Medium-high |
| **Polish-only screenshot A/B** | **Vox** | High |

**Why enterprise still wins 8h:** An unlabeled change manager who needs meeting-based CAB, freeze calendars, assignment groups, and a knowledge editor who needs workflow CMS will still feel Vox’s CAB as a **strong drawer ritual** and KB as a **reader with votes**, not a full secondary product suite. Durability removes the “demo amnesia” insult; it does not invent multi-tenant authority.

**Why Vox is no longer embarrassing on Changes:** Opening CHG in `cab_review`, casting two member votes, setting risk/notes, **Approve CAB**, seeing the chip, then **Schedule** (or hitting a real validation wall without approve) is **product behavior**. R6’s silent flip is dead. That is why Changes hits **9.0**.

---

## Per-surface harsh notes (focus + hold)

### Changes — **9.0** (preferred path closed)

**Credit:** CAB is a visible product panel (accent-bordered, member rows, chair primary/danger, emergency amber banner). Store policy is honest by type (standard pre-approve, normal hard gate, emergency warn-and-go). Activity stream records votes and decisions. List `aria-sort` is present. Plans/backout still gate schedule. Chips for approved/rejected are scannable.

**Ding (why not 9.1+):**  
- Two fixed mock members; chair need not wait for votes (no quorum).  
- Window not inline-editable as first-class conflict object; no CMDB maintenance-window check.  
- No `/changes/:id` deep link; drawer-only.  
- Table without bulk/multi-select (S3).  
- CAB “meeting” is instantaneous form, not a calendar event.

**9.0 means:** change-manager process path AAA. **Not** “we beat ServiceNow Change Advisory.”

### Knowledge — **8.8**

**Credit:** Helpful Yes/No finally changes the number operators see on the card and in the reader; `aria-live` + pulse; votes durable; contribute inserts pending article with body. Toast demoted to confirmation.

**Ding:** One vote lock forever; no “use in ticket”; no editorial queue beyond chip; seed article bodies still often translation keys / thin; not preferred 9.

### Durable store / SLA / notifications / copilot (cross-cutting)

**Credit:** Refresh mid-demo no longer nukes operator work. SLA clocks move. Bell can light on breach. Copilot sentence content tracks real breached/at-risk/unassigned counts. Settings reset is the escape hatch demos need.

**Ding:** All client-mock. Tick is −1 minute per 30s wall clock (accelerated demo physics). Notifications do not cover CAB/KB. Copilot is one-shot brief, not a conversation.

### Problems — **9.0** hold · CMDB **8.8** hold · Assets **8.7** hold

No regression found in process gates / related honesty / live CMDB stats from this wave.

### Critical path

| Check | Result |
|-------|:------:|
| Queues OperatorGrid + bulk + predicates | **HOLD** |
| Overview live metrics + copilot | **HOLD / micro-up** |
| WorkItemDetail workbench + DynamicForm | **HOLD** |
| Shell live My Work badge + notifications | **HOLD / micro-up** |
| Catalog | **HOLD** |
| C1 type floor | **HOLD** |
| `/admin/metadata` crumbMap | **Still missing (P2)** |

---

## Critical / craft defect register

### C1–C8 class

**None new. None reopened.**

### Residual register

| ID | R6 | R7 |
|----|----|----|
| **S3** Secondary ≠ OperatorGrid bulk | Open | **Open** (caps list craft) |
| **S4** CAB thin | Open | **CLOSED** |
| **S5** Knowledge feedback toast | Open | **CLOSED** |
| **S6** Metadata crumb | Open | **Open** |
| **S7** No deep-link module detail routes | Open | **Open** |
| **S8** Asset free-text assignee | Open | **Open** |
| **S9** (new) CAB no quorum / calendar | — | **Open P2** |
| **S10** (new) KB no editorial queue / use-in-ticket | — | **Open P2** |
| **S11** (new) Notifications not full center; no CAB/KB events | — | **Open P2** |

---

## Remaining backlog (post-PASS → multi-module *tie*)

### P1 — bake-off margin

1. **ModuleGrid** shared with optional bulk (close S3) for Assets / Changes / Problems lists.  
2. CAB quorum rule (require ≥1 or all member votes before chair approve) + optional freeze-window note.  
3. Knowledge: change-vote / use-in-ticket deep-link; minimal review queue for pending.  
4. Problem/Change reassignment control in drawer.

### P2 — polish

5. `crumbMap` for `/admin/metadata`.  
6. Routes `/problems/:id`, `/changes/:id`, `/assets/:id`.  
7. Notification center route; emit CAB/KB events into bell.  
8. Quarantine dead `fetchCiImpact` seed if still unused.  
9. Visual regression CI per `quality-gates.md`.  
10. Optional: decelerate SLA tick or document accelerated demo physics in UI.

---

## What improved R6 → R7 (credit — real)

- **Changes preferred 9.0** — CAB is deliberate product surface; silent schedule-approve for normal is gone.  
- **Knowledge left floor** — 8.5 → 8.8 with mutating, durable scores.  
- **Demo durability** — localStorage store + Settings reset; secondary + primary state survive refresh.  
- **Living L1 urgency** — SLA tick + notification diff on breach/at_risk/assign.  
- **Copilot honesty** — brief text derived from store queue stats, not wallpaper.  
- **Secondary preferred count 1 → 2** (Problems + Changes).  
- **Critical five held ≥9** with shell/overview micro-ups.

PASS is “Changes preferred closed + Knowledge theatre closed + durability real.”  
PASS is **not** “Vox wins unlabeled 8h multi-module desk.”

---

## Viewport notes (R7)

| Viewport | Assessment |
|----------|------------|
| **1440** | CAB panel readable in drawer; member rows + actions fit; KB feedback score row clear |
| **1024** | CAB actions wrap; tables still horizontal-scroll (S3 pain) |
| **768** | CAB member vote buttons stack; emergency banner full-width OK; secondary tables tax scroll |
| **320** | Drawer CAB usable with wrap; multi-column change table painful; notif panel OK |

---

## Final call

| Gate | R6 | **R7** |
|------|----|--------|
| Critical surfaces ≥9 | **PASS** | **PASS (held / micro-up)** |
| No C1–C8 critical defects | **PASS** | **PASS** |
| Secondary five each ≥8.5 | **PASS** | **PASS** (min 8.7) |
| Secondary preferred ≥9 | **PASS (Problems)** | **PASS (Problems + Changes)** |
| CAB explicit (S4) | Open | **PASS / CLOSED** |
| KB vote mutates (S5) | Open | **PASS / CLOSED** |
| Durable mock store | Wishlist | **PASS (demo class)** |
| Blind A/B L1 2h | Vox can tie/win | **Vox wins** |
| Blind A/B multi-module 8h | Enterprise wins | **Enterprise still wins (narrower)** |
| Self-score honesty | Mostly honest | **Honest (KB −0.1)** |
| **Elevation verdict** | Secondary AAA PASS | **PASS (Changes preferred closed)** |

---

### PASS/FAIL + scores (executive)

**Verdict: PASS** — Changes **9.0** preferred process path verified in live code (explicit CAB); Knowledge **8.8** leaves toast theatre; durable store + SLA tick + event notifications are real demo craft; critical five held; multi-module 8h still enterprise.

| Secondary (gate set) | R6 | **R7** | ≥8.5 | ≥9 |
|----------------------|---:|-------:|:----:|:--:|
| Assets | 8.7 | **8.7** | Yes | No |
| Problems | 9.0 | **9.0** | Yes | **Yes** |
| Changes | 8.8 | **9.0** | Yes | **Yes** |
| CMDB | 8.8 | **8.8** | Yes | No |
| Knowledge | 8.5 | **8.8** | Yes | No |
| **Secondary average** | ~8.8 | **~8.86** | Pass | **2/5 preferred** |

| Critical five | R7 |
|---------------|---:|
| Overview | **9.1** |
| Queues | **9.1** |
| WorkItemDetail | **9.0** |
| Shell | **9.1** |
| Catalog | **9.0** |
| **Critical average** | **~9.06** |

| Other | R7 |
|-------|---:|
| MyWork | 8.7 |
| Reports | 7.3 |
| Settings | 7.0 |
| Admin Metadata | 6.7 |

### Blind winners (unlabeled)

| Scenario | Winner |
|----------|--------|
| **L1 triage 2h** | **Vox** |
| **Multi-module 8h** | **Enterprise (Naumen / ServiceNow Workspace class)** |
| **Polish screenshot** | **Vox** |
| **Guided CHG CAB path** | **Tie / Vox credible** |

**Can Vox win multi-module desk?**  
**No.** Partial demo-tie on guided Problems + Changes CAB + CMDB impact + durable refresh. Enterprise desks still take the unlabeled 8-hour multi-module shift — margin narrower than R6 because CAB is no longer theatre and state survives reload.

**Why PASS is honest:** Changes schedule no longer lies; CAB approve is a deliberate operator action with UI + store + activity; KB % moves and persists; L1 urgency ticks; critic did not hand Vox the 8h bake-off.
