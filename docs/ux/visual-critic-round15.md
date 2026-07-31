# Vox ITSM — Visual / Operator UX Critic Round 15 (Wave 9: SLA runtime · KE→KB)

**Date:** 2026-07-31  
**Scope:** After R14 PASS + Wave 9 (`docs/ux/wave9-sla-ke-kb.md`).

**Focus:** **S21 SLA bind** · **S22 enable toggle** · **S10 KE→KB**

---

## Verdict: **PASS**

Wave 9 closes the last major **admin→runtime** gap for SLA and the KE→KB process hop. WorkItemDetail SLA tab reads live mock policies (targets, pause, calendar, source honesty). Known-error problems mint pending KB drafts with toast deep-link. Critical five held. Enterprise still wins 8h multi-module.

### Gates

| Gate | Result | Evidence |
|------|:------:|----------|
| S21 SLA bind | **CLOSED (mock)** | `getWorkItemSlaRuntime` + WID SLA tab + subscribe |
| S22 enable interactive | **CLOSED** | `setSlaPolicyEnabled` toggle on SlaPage |
| S10 KE→KB | **CLOSED (mock)** | Problems KE button → createKnowledgeArticle pending |
| Critical five | **held** | WID +0.1 honesty; others hold |
| Multi-module flip | **No** | Mock clocks, mock CMS, no live SLA engine |

### Harsh cuts

1. Countdown string on work item still seed `slaTarget` — policy sets **targets**, does not rewrite remaining clock (honest partial bind).  
2. KE→KB is mock CMS only; live refuses (S33).  
3. SLA admin still no holiday editor / graph / multi-calendar.  
4. Admin SLA **7.4 → 7.7** — enable + runtime proof; refuse 8.0.

---

## Scores R14 → R15

| Surface | R14 | **R15** | Δ |
|---------|----:|--------:|--:|
| WorkItemDetail | 9.1 | **9.2** | +0.1 SLA honesty |
| Problems | 9.2 | **9.3** | +0.1 KE→KB |
| Knowledge | 9.0 | **9.0** | held (consumer of KE drafts) |
| Admin SLA | 7.4 | **7.7** | +0.3 enable + bind |
| Critical five | hold | **hold** | Overview 9.2 Queues 9.1 Shell 9.1 Catalog 9.0 WID 9.2 |

### Residuals

| ID | R15 |
|----|-----|
| S10 / S21 / S22 | **CLOSED** (mock scope) |
| S11 partial | center exists (R14) |
| S23–S25 / S27–S28 | CLOSED prior |
| S29–S34 | open prior / wave8 |
| **S35** countdown not re-derived from policy hours | Open P2 |
| **S36** use-in-ticket from KB → work item | Open P2 (S10 remainder) |

---

## Blind A/B

| Scenario | Winner |
|----------|--------|
| L1 2h | **Vox** |
| Multi-module 8h | **Enterprise** (margin narrower: full object workflow + SLA bind + KE→KB demo path) |

---

## Sign-off

**R15 PASS.** Process honesty stack (workflow + SLA + KE→KB) is demo-complete for mock. Next P1: S11 depth / live write APIs / countdown re-derive — not vanity chrome.
