# SPA authentication (Keycloak OIDC)

The Vox ITSM frontend uses **Authorization Code + PKCE** against Keycloak client `itsm-spa` (public). No client secret; pure TypeScript PKCE (Web Crypto), no OIDC library.

## Env (frontend)

Copy `frontend/.env.example` → `frontend/.env.local`:

```bash
VITE_USE_MOCK=false
VITE_API_BASE=/api/v1

VITE_OIDC_ENABLED=true
VITE_OIDC_ISSUER=http://localhost:8081/realms/itsm
VITE_OIDC_CLIENT_ID=itsm-spa
VITE_OIDC_REDIRECT_URI=http://localhost:5173/auth/callback
```

| Variable | Purpose |
| --- | --- |
| `VITE_OIDC_ENABLED` | `true` shows Login / Logout and soft “Sign in” banner (live mode, no token) |
| `VITE_OIDC_ISSUER` | Realm issuer (`…/realms/itsm`) |
| `VITE_OIDC_CLIENT_ID` | Public client id (`itsm-spa`) |
| `VITE_OIDC_REDIRECT_URI` | Must match Keycloak redirect (default: origin + `/auth/callback`) |

When OIDC is disabled, mock mode and backend `dev` profile still work without a token.

## Flow

1. **Login** — SPA generates `code_verifier` / S256 `code_challenge` + `state`, stores them in `sessionStorage`, redirects to Keycloak authorize.
2. **Callback** — route `/auth/callback` exchanges `code` + verifier at the token endpoint.
3. **Tokens** — access, refresh and ID tokens remain in memory only. `apiRequest` receives the access token through the in-memory auth session and sends `Authorization: Bearer …`.
4. **Actor** — JWT `sub` stays in memory with the session for assign-to-me.
5. **Refresh (silent renew)** — if `refresh_token` is present:
   - timer refreshes slightly before `expires_at` and reschedules;
   - on load, near-expiry sessions (&lt; 90s) refresh immediately;
   - on tab **visible** / window **focus**, refresh if &lt; 120s remaining;
   - reload or tab close clears the session; user signs in again.
6. **401 interceptor** — `apiRequest` / `apiFetch` (attachments multipart):
   - on HTTP **401**, call registered `setAuthRefreshHandler` (wired by `AuthProvider`);
   - **single-flight**: concurrent 401s share one refresh promise;
   - on success, **one retry** with new Bearer from the in-memory session;
   - on failure, original 401 surfaces; session cleared by refresh failure path;
   - skipped when `VITE_USE_MOCK=true`, or `skipAuthRefresh: true`.
7. **Logout** — clears session + token; RP-initiated logout at Keycloak end-session when possible.

## Soft banner

With `VITE_USE_MOCK=false`, OIDC enabled, and no Bearer token, the shell shows a **non-blocking** “Sign in” banner. Backend profile `dev` still allows anonymous API as `dev-local`.

## Local test (anna / anna)

Prerequisites: `docker compose up -d --build` (or infra-only + host backend), Keycloak on `8081`, frontend with OIDC env above. Compose frontend uses `http://localhost/auth/callback`.

```bash
cd frontend
# .env.local as above
npm run dev
```

1. Open http://localhost:5173  
2. Profile menu → **Sign in** (or Settings → Authentication → Sign in)  
3. Keycloak login: user **`anna`**, password **`anna`**  
4. Redirect back to `/auth/callback` then home; profile shows **Anna Yakovleva** (or name from token)  
5. Network tab: API calls include `Authorization: Bearer eyJ…`  
6. **Sign out** clears tokens and ends Keycloak session  

Demo users: see [infra/keycloak/README.md](../../infra/keycloak/README.md) (`anna`, `admin`, `requester`).

Manual token (without SPA login) still works:

```bash
# password grant against itsm-spa, then:
# Development only: VITE_API_TOKEN=<access_token> npm run dev
```

## Related

- [local-dev.md](./local-dev.md) — compose, backend profiles, frontend modes  
- [infra/keycloak/README.md](../../infra/keycloak/README.md) — realm, clients, users  
- [authorization.md](../security/authorization.md) — RBAC / JWT roles  
## Organization claim

Access tokens must include trusted string claim `organization_id`. Backend uses it for tenant
data isolation; local realm maps user attribute with same name through default `organization`
client scope. Provision every production user/service account with an organization attribute.
Never accept organization scope from request headers or body fields.
