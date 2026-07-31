# Wave 9 — SLA runtime bind + KE→KB

**Date:** 2026-07-31  
**Scope:** S21 remainder (SLA), S22 enable toggle, S10 KE→KB  
**Branch:** `feat/naumen-depth-wave9`

---

## Goal

| Residual | Fix |
|----------|-----|
| **S21 SLA** | WorkItemDetail SLA tab consumes mock SLA admin policies (response/resolution by priority) |
| **S22** | Enable badge becomes toggle → runtime falls back when disabled |
| **S10** | Known-error problem → pending KB article from RCA |

---

## SLA runtime (`lib/slaRuntime.ts`)

- Resolves enabled `work-item.response` / `work-item.resolution` policies
- Matches `priority=CRITICAL|HIGH|MEDIUM|LOW` conditions
- Fallback to prior hard-coded mins/hours when disabled/missing
- Pause chip when UI status maps into policy `pauseStates` (e.g. waiting → PENDING)
- Calendar zone/window from policy calendar key
- Live subscribe: admin save/toggle refreshes detail

## KE→KB

- Problem drawer: when `knownError`, primary **Publish to knowledge**
- Requires root cause; body = intro + root cause + workaround
- Mock CMS `createKnowledgeArticle` pending draft; toast deep-link `/knowledge?article=`
- Live mode: refuse with CMS message

## Files

- `lib/slaRuntime.ts` (new)
- `mock/sla.ts` — `setSlaPolicyEnabled`
- `WorkItemDetailPage.tsx` — SLA bind UI
- `SlaPage.tsx` — enable toggle
- `ProblemsPage.tsx` — KE→KB
- i18n en/ru/de

---

## Build / e2e

| Check | Result |
|-------|:------:|
| build | *(gate)* |
| e2e | *(gate)* |
