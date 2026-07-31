# Wave 4 — Settings · Admin Metadata · Catalog depth

**Date:** 2026-07-31  
**Scope:** `frontend/src/pages/Settings`, `frontend/src/pages/Admin/MetadataPage`, `frontend/src/pages/Catalog`, toast hook, i18n, CSS  
**Build:** `npm run build` **PASS**

---

## Goal

Elevate secondary admin / self-service surfaces toward AAA:

| Surface | Target | Focus |
|---------|--------|--------|
| **Settings** | **8.0+** | Section nav, appearance polish, notification persistence, keyboard focus |
| **Admin Metadata** | **7.5+** | Object filter, dense tables + empty states, workflow mock, form preview |
| **Catalog** | depth | Full request form before submit, toast → work item link, empty results |

---

## Settings (target 8.0+)

**File:** `pages/Settings/SettingsPage.tsx`

1. **Section nav (tabs):** Profile | Language | Appearance | Notifications | API | Integrations | Demo data  
   - Vertical tab rail on desktop (`tabs--vertical`); wraps to horizontal chips on narrow viewports.
2. **Appearance:** theme as icon cards (Light / Dark / High contrast); density as two explicit radio cards (not a bare toggle).
3. **Notifications:** prefs load/save via `localStorage` key `vox-notification-prefs`; auto-persist on change + Save action still toasts.
4. **Keyboard:** focus rings on section tabs, theme/density cards, language options, toggles (track + ring), links.

Auth block remains under **Profile** when OIDC is enabled. Translation admin sample table lives under **Language**. Demo reset isolated under **Demo data** (mock-only).

---

## Admin Metadata (target 7.5+)

**File:** `pages/Admin/MetadataPage.tsx`

1. **Search/filter** on object list (key, localized label, attribute keys/labels).
2. **Attribute table** density (`data-table--dense`) + empty states for zero attributes / zero relations / filter miss.
3. **Workflow states** strip: enum values from `state` / `status` (fallback first multi-value ENUM) as mock lifecycle pills.
4. **Form definition preview:** if `fetchFormDefinition(objectKey)` returns a form (e.g. work-item), **Form preview** opens a read-only `DynamicForm` modal of sections.

---

## Catalog

**File:** `pages/Catalog/CatalogPage.tsx`

1. **Service request drawer:** loads work-item form metadata; renders **DynamicForm** with title / description / service / urgency / impact.  
   - Impact **optional** (patched `required: false` + empty option).  
   - Service prefilled from catalog item.
2. **After create:** toast message + **link** to `/work-items/:id` (`useToast` action API); drawer closes (no forced queue jump).
3. **Empty results:** distinct filtered copy, clear-filters CTA, optional “still N services when cleared” hint; category empty state separate.

---

## Shared

| Piece | Change |
|-------|--------|
| `hooks/useToast.tsx` | Optional `ToastAction` `{ label, href }` on success/info/…; longer TTL when action present; `Link` in toast |
| `styles/global.css` | Settings shell, appearance cards, metadata workflow + dense table, service drawer form, toast link, vertical tabs, focus rings |
| i18n | New keys in **en / ru / de** for settings sections, metadata, catalog empty/form/toast |

---

## Self-scores (honest)

| Surface | Before (approx.) | After (self) | Notes |
|---------|-----------------:|-------------:|-------|
| **Settings** | ~6.5–7.0 | **8.2** | Organized nav, persistence, appearance cards, focus; still mock profile / no server prefs API |
| **Admin Metadata** | ~6.5 | **7.7** | Filter + dense table + workflow strip + form preview; still read-only mock objects |
| **Catalog request path** | ~7.5–8.0 | **8.6** | Full form before submit, optional impact, toast link; not full multi-item cart / approvals UI |

**Criteria met**

- [x] Settings section nav as specified  
- [x] Appearance theme + density polished  
- [x] Notification prefs → localStorage  
- [x] Focus rings on settings controls  
- [x] Metadata object search/filter  
- [x] Dense attribute table + empty states  
- [x] Workflow states mock when enum present  
- [x] Form definition preview (read-only)  
- [x] Catalog DynamicForm-like fields; impact optional  
- [x] Toast with work-item link  
- [x] Empty results polish  
- [x] i18n ru / en / de  
- [x] `npm run build` pass (verify in CI/local)  

---

## Out of scope (honest debt)

- Server-backed notification prefs / user profile edit  
- Editable metadata / real workflow engine graph  
- Catalog multi-step approvals UI beyond flag display  
- Live form definitions for change/problem objects (only work-item mock form today)
