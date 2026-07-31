# Changes CAB + Knowledge Feedback Parity Pass

**Date:** 2026-07-31  
**Scope:** R6 residuals **S4** (CAB thin) and **S5** (Knowledge feedback toast) under `G:\ITSM\frontend`  
**Baseline (R6 critic):** Changes **8.8**, Knowledge **8.5**  
**Build:** `npm run build` **PASS** (`tsc --noEmit && vite build`)

---

## Goal

Close product-depth residuals that blocked Changes preferred (≥9) and kept Knowledge at floor-only:

| Residual | R6 status | This pass |
|----------|-----------|-----------|
| **S4** CAB is silent `cabApproved` on schedule | Open | **Closed** — explicit approve/reject + votes |
| **S5** Helpful Yes/No is toast theatre | Open | **Closed** — mutates store score + list % |

---

## Changes — what shipped

| Requirement | Implementation |
|-------------|----------------|
| CAB panel on change drawer | Risk select, CAB notes textarea, Approve / Reject chair actions |
| Approve/reject sets flags + activity | `setChangeCabDecision` → `cabApproved` / `cabRejected` + activity log |
| Schedule requires `cabApproved` for **NORMAL** | Store gate `changes.validation.cabApprovalRequired`; no silent flip on schedule |
| Emergency can skip with warning banner | Draft/cab_review may schedule without approve; banner + `scheduled_emergency` activity |
| Implementation + backout before schedule | Held (existing gates) |
| CAB members mock + vote simulation | 2-seat list (Maria / Dmitry); per-member approve/reject |
| i18n ru / en / de | Full `changes.cab.*` + validation + activity keys |
| List `aria-sort` on headers | Sortable columns announce ascending / descending / none |

### Schedule policy (honest)

| Type | draft → scheduled | cab_review → scheduled |
|------|-------------------|------------------------|
| **standard** | Allowed (policy pre-approve) | Allowed |
| **normal** | **Blocked** (must CAB) | Requires **explicit** `cabApproved` |
| **emergency** | Allowed with **warning banner** | Allowed without approve (logged) |

Rejected CAB blocks schedule until returned to draft path.

**Files:** `types/index.ts`, `mock/data.ts`, `mock/store.ts`, `api/changes.ts`, `pages/Changes/ChangesPage.tsx`, `styles/global.css`, `i18n/locales/{ru,en,de}.json`

---

## Knowledge — what shipped

| Requirement | Implementation |
|-------------|----------------|
| Helpful Yes/No mutates score | `voteKnowledgeArticle` increments `helpfulYes` / `helpfulNo`, recomputes `%` |
| Persist in session store | Knowledge list in mock store (+ durable snapshot); `subscribeKnowledge` reloads list |
| List re-render shows new % | Cards bind to store articles; reader score `aria-live` updates inline |
| Contribute flow | Modal title/body → `addKnowledgeArticle` as **pending** draft in store |
| Feedback not toast-only | Reader shows live `%` + vote count; toast is secondary confirmation |

**Files:** `types/index.ts`, `mock/data.ts` (seed vote totals), `mock/store.ts`, `api/knowledge.ts`, `pages/Knowledge/KnowledgePage.tsx`, `styles/global.css`, i18n

---

## Self-scores (honest)

| Surface | R6 critic | Self after pass | Δ | Why |
|---------|----------:|----------------:|--:|-----|
| **Changes** | 8.8 | **9.0** | +0.2 | CAB is a real product surface (risk, notes, votes, chair decision, schedule gates). Hits preferred process bar like Problems; still not CAB calendar / assignment groups. |
| **Knowledge** | 8.5 | **8.9** | +0.4 | Votes mutate durable mock score; contribute writes pending article; reader shows count update. Not full CMS / versioning / KE→KB publish. |

### Why not higher

- **Changes 9.0 not 9.2+:** No bulk grid, no conflict check vs CMDB window, no meeting calendar — process path preferred, not enterprise CAB suite.
- **Knowledge 8.9 not 9.1+:** Contribute is pending mock (not editorial workflow); body for seed articles remains i18n keys; no “use in ticket” deep-link.

Inflation guard: R6 dinged Changes for *silent* approve. This pass makes approve a deliberate action — that is the 9.0 claim, not marketing.

---

## Manual smoke

1. **Changes / CHG-422 (normal, cab_review)**  
   - Open drawer → CAB panel → cast 2 member votes → set risk/notes → **Approve CAB** → chip “CAB approved” → **Schedule** succeeds.  
   - Reject path: Reject → Schedule blocked with `cabRejected` message.
2. **Changes / emergency**  
   - Create emergency with plans → Schedule from draft without CAB → warning banner + activity “scheduled emergency”.
3. **Changes list**  
   - Sort Number/Type/Status/Risk/Window → headers expose `aria-sort`.
4. **Knowledge**  
   - Open VPN article → Yes → `%` and vote count update in reader; close → list card shows new %.  
   - Contribute → title/body → pending article at top of list; open reader shows body.
5. **Locale** ru / en / de — no raw keys on CAB panel or contribute modal.

```bash
cd frontend && npm run build   # PASS
```

---

## Residual (non-blocking)

- Secondary tables still lack OperatorGrid bulk (R6 S3).  
- No dedicated `/changes/:id` route.  
- Knowledge contribute has no editor review queue UI beyond pending chip.  
- Multi-module 8h bake-off still enterprise-favored on durable CAB calendars / CMS.
