# Wave 10 — Use-in-ticket + notification prefs wire

**Date:** 2026-07-31  
**Branch:** `feat/naumen-depth-wave10`

## Scope

| Residual | Fix |
|----------|-----|
| **S36 / S10 remainder** | KB reader **Use in ticket** → creates incident + toast deep-link |
| **S11 / S17** | Settings notification prefs filter bell menu + center (live subscribe) |
| Related polish | WorkItemDetail related KB links honor `?article=` |

## Files

- `lib/notificationPrefs.ts` — shared load/save/subscribe/filter
- Settings / NotificationMenu / NotificationsPage
- KnowledgePage use-in-ticket
- WorkItemDetail related KB hrefs
- i18n en/ru/de

## Build / e2e

PASS locally (tsc + vite + 6 smoke).
