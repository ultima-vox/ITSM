# Vox ITSM — Visual / Operator UX Critic Round 14 (Wave 8: Live honesty · bulk · KB · notif center)

**Date:** 2026-07-31  
**Scope:** Live code after R13 PASS + Wave 8 (`docs/ux/wave8-honesty-bulk-kb-notif.md`).  
**Inputs:** R13 residual register, wave8 doc, live API/pages.

**Focus this wave:**  
**S23** live bulk honesty · **S25** KB CMS split brain · **S11/S24** notification center + fail surface

**Elevation bar:**  
- S23 CLOSED — no fake `ids.length` success in live.  
- S25 CLOSED — no ghost CMS writes in live; banner honest.  
- S24 CLOSED — no silent mock fallback on live notif fail.  
- S11 Partial→stronger — `/notifications` center exists (≥7.0 craft).  
- Critical five held ≥9.0.  
- No multi-module bake-off flip.

---

## Verdict: **PASS**

Wave 8 is **trust repair**, not chrome inflation. Live bulk no longer lies; Knowledge CMS no longer invents local ghosts against a read-only API; notifications fail loudly and open a real center page. Critical five held. Enterprise still wins 8h multi-module.

### Why PASS

| Requirement | Result | Evidence |
|-------------|:------:|----------|
| S23 bulk honesty | **CLOSED** | `refuseLiveFeature` assign/asset; P/C status via real transitions |
| S25 KB CMS split brain | **CLOSED** | refuse writes + banner + UI hide authoring in live |
| S24 silent mock fallback | **CLOSED** | `fetchNotifications` rethrows; menu/page error surface |
| S11 notif center | **Partial→7.2** | `/notifications` filters, mark read, retry; not full product |
| Critical five | **held** | no regression path on L1 surfaces |
| Multi-module honesty | **Enterprise wins** | still mock engines, no live bulk assign APIs |

### Why not rubber stamp

1. **Bulk status for P/C is fan-out, not bulk endpoint** — honest counts, not enterprise bulk API.  
2. **Asset bulk still refuse-only** — correct honesty, weak live ops.  
3. **KB CMS still mock-only for writes** — closed split brain by **disabling**, not by shipping write API.  
4. **Notif center is list + filters** — no prefs wire from Settings (S17), no mark-read live API, no websocket.  
5. **S11 not fully closed** as enterprise notification product — craft **7.2**, residual depth open.

**FAIL would be:** still returning `ids.length`; CMS still writing mock on live list; menu still showing mock seed after live 500; no center route.

---

## Verification

### S23 — **CLOSED**

| Claim | Verdict |
|-------|:-------:|
| Never fake success count | **PASS** |
| Live problem/change status → server transitions | **PASS** |
| Live assign refuse + toast | **PASS** |
| Live asset bulk refuse | **PASS** |

### S25 — **CLOSED**

| Claim | Verdict |
|-------|:-------:|
| create/update/publish refuse live | **PASS** |
| Honesty banner when `!useMock()` | **PASS** |
| Contribute/edit/publish hidden live | **PASS** |

**Knowledge R13 9.0 → R14 9.0** — honesty depth, no process gift (still no KE→KB).

### S24 / S11 — **S24 CLOSED · S11 7.2**

| Claim | Verdict |
|-------|:-------:|
| Live fetch error not silent mock | **PASS** |
| Route `/notifications` + crumb | **PASS** |
| Menu view-all → center | **PASS** |
| All/unread filter + mark all | **PASS craft** |
| Settings prefs wire (S17) | **FAIL residual** |
| Live mark-read API | **FAIL residual** |
| Real-time push | **FAIL** |

**Shell holds 9.1** — center is additive, not shell regression.

---

## Score table R13 → R14

| Surface | R13 | **R14** | Δ | Notes |
|---------|----:|--------:|--:|-------|
| Overview | 9.2 | **9.2** | 0 | Held |
| Queues | 9.1 | **9.1** | 0 | Held |
| WorkItemDetail | 9.1 | **9.1** | 0 | Held |
| Shell | 9.1 | **9.1** | 0 | Notif route additive |
| Catalog | 9.0 | **9.0** | 0 | Held |
| Knowledge | 9.0 | **9.0** | 0 | Honesty; no score gift |
| Assets | 9.0 | **9.0** | 0 | Bulk toast honesty |
| Problems | 9.2 | **9.2** | 0 | Live bulk status path |
| Changes | 9.2 | **9.2** | 0 | Same |
| **Notifications center** | — | **7.2** | new | Clears floor; refuse 7.5+ |
| Global Search | 8.1 | **8.1** | 0 | Held |
| Admin * | prior | prior | 0 | Held |

### Critical five

All ≥9.0 **HOLD**.

### Wave 8 gates

| Gate | Result |
|------|:------:|
| S23 closed | **PASS** |
| S25 closed | **PASS** |
| S24 closed | **PASS** |
| S11 center ≥7.0 | **PASS (7.2)** |
| Critical five | **PASS** |

---

## Residual register

| ID | R13 | R14 |
|----|-----|-----|
| **S23** | Open P1 | **CLOSED** |
| **S24** | Open P2 | **CLOSED** |
| **S25** | Open P1 | **CLOSED** |
| **S11** | Open | **Partial** — center exists; depth/prefs/push open |
| **S7–S10, S14–S22, S26, S29–S31** | prior | **unchanged** |
| **S32** (new) Live bulk assign / asset status need server endpoints | — | **Open P2** |
| **S33** (new) KB write API (not just refuse) | — | **Open P2** |
| **S34** (new) Live notification mark-read / stream | — | **Open P2** |

---

## Blind A/B

| Scenario | Winner |
|----------|--------|
| L1 2h triage | **Vox** (honesty helps trust; chrome held) |
| Multi-module 8h | **Enterprise** (margin slightly narrower on trust; engines still win) |

---

## Backlog post-PASS

### P1
1. S10 use-in-ticket / KE→KB  
2. S11 remainder + S17 prefs wire  
3. S21 SLA runtime bind  

### P2
4. S32/S33 live bulk + KB write APIs  
5. S34 mark-read live + optional poll  
6. Search facets / S8 assignee / S9 CAB  

---

## Critic sign-off

**R14 PASS.** Trust residuals closed without score theatre. Notification center clears admin-class floor. Next: process depth (SLA bind / KE→KB) or live write APIs — not more localStorage cosmetics.
