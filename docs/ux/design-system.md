# Design system

Tokens live in `frontend/src/design-system/tokens.css` (CSS custom properties) with a JS mirror in `frontend/src/design-system/tokens.ts`. Chrome and layout consume `--vox-*` (and semantic aliases such as `--vox-accent`). Shared UI chrome is `frontend/src/styles/global.css`.

## Experiences

- Operator — `/` — `AppShell`. Command palette and create stay here.
- Admin — `/admin` — `AdminShell`. Platform config (metadata, workflow, SLA, RBAC, …).
- Portal — `/portal` — `PortalShell`. Requester home, catalog, knowledge, my requests. No operator chrome.

Experience is derived from the URL in `frontend/src/app/experiences.ts` (`data-experience` on `html`).

## Brand aliases

`--brand-*` currently aliases `--vox-*` (bg, surface, text, accent, focus, danger, warning, success, info, sidebar). Stage 8 branding retargets `--brand-*` without rewriting `--vox-*` internals.
