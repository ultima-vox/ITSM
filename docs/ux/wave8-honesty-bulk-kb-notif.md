# Wave 8 — Live honesty: bulk · KB CMS · notification center

**Date:** 2026-07-31  
**Scope:** S23 bulk fake success, S25 KB CMS split brain, S11/S24 notification center + fail surface  
**Branch:** `feat/naumen-depth-wave8`

---

## Goal

Close operator-trust residuals where live mode **lied** (fake bulk counts, ghost KB edits, silent mock notifications) and ship a real **notification center** route.

| Residual | Fix |
|----------|-----|
| **S23** | Live bulk never returns `ids.length` without server work; assign refuses; problem/change status uses per-item transitions; assets refuse |
| **S25** | KB create/update/publish refuse in live; UI banner + hide contribute/edit/publish |
| **S24** | `fetchNotifications` rethrows on live failure — no silent mock seed |
| **S11** | `/notifications` center page; bell “view all” routes there; menu shows live error |

---

## Behaviour

### Bulk (S23)

| Module | Live assign | Live status |
|--------|-------------|-------------|
| Problems | `refuseLiveFeature` | `transitionProblemStatus` per id → honest success count |
| Changes | `refuseLiveFeature` | `transitionChangeStatus` per id → honest success count |
| Assets | `refuseLiveFeature` | `refuseLiveFeature` (no write API) |
| Work items | unchanged (already calls assign/priority APIs) | — |

UI: try/catch → `module.errors.bulkLiveUnsupported` / `bulkFailed` / `bulkNoneSucceeded`.

### Knowledge CMS (S25)

| Mode | List | Contribute / edit / publish |
|------|------|-----------------------------|
| Mock | store | store (session) |
| Live | `GET /knowledge/articles` | refuse + honesty banner; buttons hidden |

### Notifications (S11 / S24)

| Surface | Behaviour |
|---------|-----------|
| Menu | Live error → alert + link to center; no mock seed |
| Center `/notifications` | Filters all/unread, mark read, mark all, retry on fail |
| API | Live failure propagates |

---

## Files

| Path | Change |
|------|--------|
| `api/client.ts` | `refuseLiveFeature` / `isLiveFeatureUnsupported` |
| `api/problems.ts` `changes.ts` `cmdb.ts` | bulk honesty |
| `api/knowledge.ts` | CMS live refuse |
| `api/notifications.ts` | no silent fallback |
| `pages/*/Assets|Problems|Changes` | bulk toast errors |
| `pages/Knowledge/KnowledgePage.tsx` | live banner + CMS gates |
| `pages/Notifications/NotificationsPage.tsx` | new center |
| `components/layout/NotificationMenu.tsx` | error + route |
| `app/router.tsx` `AppShell.tsx` | route + crumb |
| i18n en/ru/de + CSS | keys + center styles |

---

## Build / e2e

| Check | Result |
|-------|:------:|
| `npm run build` | *(gate)* |
| `npm run test:e2e` | *(gate)* |
