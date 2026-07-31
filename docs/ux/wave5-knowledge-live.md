# Wave 5 — Knowledge CMS + Live API wiring

**Date:** 2026-07-31  
**Scope:** Knowledge authoring CMS (mock session), live API depth for problems/changes/catalog, notifications live fetch  
**Build:** `npm run build` **PASS** (`tsc --noEmit && vite build`)

---

## Goal

| Surface | Target | Focus |
|---------|--------|--------|
| **Knowledge** | CMS path | Edit existing · publish pending · version note · status filter · full i18n |
| **Live API** | Depth when `!useMock` | problems/changes create+list+transition · catalog request · GET notifications |
| **Mock** | No regression | Default `VITE_USE_MOCK` still owns contribute/edit/publish/votes |

---

## Knowledge CMS

**Files:** `types/index.ts`, `mock/store.ts`, `api/knowledge.ts`, `pages/Knowledge/KnowledgePage.tsx`, `styles/global.css`, `i18n/locales/{en,ru,de}.json`

| Requirement | Implementation |
|-------------|----------------|
| Article editor | Reader pencil → form (title / tag / body / version note) → `updateKnowledgeArticle` store mutation |
| Publish pending | Reader **Publish** when `status === 'pending'` → `publishKnowledgeArticle` (`published` + `verified`) |
| Version note / `updatedAt` | Edit bumps `version`, sets `versionNote`, refreshes `updatedAt`; publish also increments version |
| Filter pending/published | Chip group under tabs: All · Published · Pending with live counts |
| Full i18n | New keys in **en / ru / de** (edit, publish, filter, version note); no locale ternaries |

### Store mutations

- `updateKnowledgeArticle(id, { title?, body?, tag?, versionNote?, status? })`
- `publishKnowledgeArticle(id)` → status published, verified true, version++

Authoring remains **session mock** even when `!useMock` (backend has read/vote only, no write CMS). List/topics still hit live GET when live.

---

## Live API depth (`VITE_USE_MOCK=false`)

### Problems / Changes

| Call | Mock | Live |
|------|------|------|
| List | store | `GET /problems`, `GET /changes` + mappers |
| Create | store | `POST /problems`, `POST /changes` with backend field shapes |
| Transition | store | `POST /…/{id}/transitions` with `{ target }` (+ RCA fields for problems) |

**Fixes this wave:** path was `/transition` (wrong); body used `status` instead of backend `target`. Create payloads map UI fields → backend (`rollbackPlan`, `businessJustification`, enum uppercasing).

### Catalog

| Call | Mock | Live |
|------|------|------|
| List / categories | seed | `GET /catalog/items` + derive categories |
| Request | `createWorkItem` | UUID item id → `POST /catalog/items/{id}/requests`; else still `createWorkItem` |

### Notifications

| Mode | Behaviour |
|------|-----------|
| Mock | Existing seed + work-item event center (`useSyncExternalStore`) |
| Live | `GET /api/v1/notifications` → map to menu items; **fallback** to mock seed on error |

Plain `title` / `body` on `AppNotification` for live DTOs; i18n keys still used for mock seeds.

**Files:** `api/problems.ts`, `api/changes.ts`, `api/catalog.ts`, `api/notifications.ts`, `api/index.ts`, `api/mappers/knowledge.ts`, `pages/Catalog/CatalogPage.tsx`, `components/layout/NotificationMenu.tsx`, `mock/notifications.ts`

---

## Design system

- Status chips (pending / published / version) reuse meta size and accent/mint tokens
- Status filter chips match existing pill filter patterns + focus rings
- Reader foot actions: Publish primary + Close secondary; editor form reuses `module-create-form--contribute`

---

## Self-scores (honest)

| Surface | Before (approx.) | After (self) | Notes |
|---------|-----------------:|-------------:|-------|
| **Knowledge** | ~8.9 | **9.2** | Real CMS loop (edit/publish/filter/version); not multi-locale revision publish to server |
| **Problems live path** | partial / wrong path | **wired** | create + list + transition body/path fixed |
| **Changes live path** | partial / wrong path | **wired** | same |
| **Catalog live request** | list only | **wired** | UUID → catalog requests endpoint |
| **Notifications live** | mock only | **wired** | GET + map + mock fallback |

### Why not higher (Knowledge)

- No backend draft/review/publish API; CMS is durable mock store only
- Seed bodies still materialize from i18n until first edit
- No KE→KB from Problems, no article permissions matrix

---

## Manual smoke

1. **Knowledge (mock)**  
   - Contribute → pending chip → filter **Pending** → open → **Publish** → filter **Published**  
   - Open any article → pencil → change title/tag/body + version note → Save → meta shows note + version chip  
2. **Notifications**  
   - Mock: seed list + mark read  
   - Live (`VITE_USE_MOCK=false`): menu loads from API or falls back silently  
3. **Live problems/changes**  
   - Create + transition only against running backend with auth  

```bash
cd frontend && npm run build   # PASS
```

---

## Residual (non-blocking)

- Knowledge write path not server-backed  
- Live bulk assign/status still no-ops for problems/changes  
- Notification read state for live items is in-memory (session), not server ACK  
