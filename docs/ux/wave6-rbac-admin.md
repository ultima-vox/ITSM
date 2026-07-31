# Wave 6 — RBAC / users admin

**Date:** 2026-07-31  
**Scope:** `frontend/src/pages/Admin/RbacPage`, `frontend/src/mock/rbac.ts`, nav, i18n, CSS  
**Build:** `npm run build` **PASS**

---

## Goal

Add a Naumen-class **RBAC admin** surface for platform roles and directory assignments, matching backend Flyway seeds (`role` / `permission` / `role_permission`) and Keycloak realm role keys.

| Surface | Route | Focus |
|---------|-------|--------|
| **RBAC** | `/admin/rbac` | Roles list + permission chips; role detail (read-only permissions); users table with session role assign |

---

## 1. Roles tab

**Files:** `pages/Admin/RbacPage.tsx`, `mock/rbac.ts`

1. **List** roles seeded like backend:
   - `ADMIN`, `SERVICE_DESK_AGENT`, `SERVICE_DESK_MANAGER`, `REQUESTER`, `CHANGE_MANAGER`, `CAB_MEMBER`
2. **List chips:** first N permission keys + overflow count
3. **Detail:** labels (en/ru/de), description, full **read-only** permission table (key + description)
4. Permission catalog from V10 + V12 + V13 + V15 grants

---

## 2. Users tab

1. Mock directory table: **name**, **role**, **locale**, **status**
2. Includes Keycloak demo principals (anna / admin / requester fixed `sub`s) + operator people
3. **Session store:** `assignUserRole(userId, roleKey)` with success toast
4. Empty / error states

---

## 3. Design & shared

| Piece | Change |
|-------|--------|
| Layout | Tabs (Roles / Users); roles master–detail grid |
| CSS | `rbac-admin-*` in `styles/global.css` |
| Types | `RbacRole`, `RbacPermission`, `RbacUser`, `RbacRoleKey` in `types/index.ts` |
| i18n | `nav.rbac` + `rbacAdmin.*` in **en / ru / de** |
| Shell | `AppShell` crumb map; `Sidebar` secondaryNav; `CommandPalette` nav item |
| Route | Lazy `/admin/rbac` |

---

## Self-scores (honest)

| Surface | Target | After (self) | Notes |
|---------|-------:|-------------:|-------|
| **RBAC admin** | 7.5+ | **7.7** | Full role matrix + assign; mock-only, no live API / multi-role principal |

**Criteria met**

- [x] `/admin/rbac` roles list with permission chips  
- [x] Role detail — read-only permissions list  
- [x] Seed matching backend RBAC role keys  
- [x] Users table: name, role, locale, status  
- [x] Session store role assignment  
- [x] Sidebar Management + command palette  
- [x] i18n ru/en/de  
- [x] Empty/error states, lazy route  
- [x] `npm run build` passes  
- [x] This doc  

---

## Out of scope (next)

- Live API for role / principal CRUD  
- Multi-role principals / scopes  
- Field-level and object-scoped grants UI  
- Sync with Keycloak realm roles  
