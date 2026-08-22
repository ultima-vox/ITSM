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
   - on tab **visible** / window **focus**, refresh if &lt; 120s remaining.
6. **Silent restore after reload** — tokens are memory-only, so a reload starts anonymous.
   Once per browser tab, and only for a browser that has already held a session, the SPA
   replays the authorize request with `prompt=none`:
   - Keycloak's SSO cookie answers with a code → the session is restored without a form;
   - no SSO session → Keycloak returns `login_required` and the SPA stays anonymous
     without an error banner;
   - the shell renders a loading state while this resolves, so pages do not fire
     unauthenticated requests in the meantime;
   - explicit sign-out suppresses the next silent attempt and clears the marker, so a
     first-time visitor is never bounced through the identity provider;
   - a callback whose `state` does not match the pending request is treated as stale: it is
     ignored without consuming the authorization still in flight and without an error
     screen, because a silent attempt and an interactive login can overlap.
7. **401 interceptor** — `apiRequest` / `apiFetch` (attachments multipart):
   - on HTTP **401**, call registered `setAuthRefreshHandler` (wired by `AuthProvider`);
   - **single-flight**: concurrent 401s share one refresh promise;
   - on success, **one retry** with new Bearer from the in-memory session;
   - on failure, original 401 surfaces; session cleared by refresh failure path;
   - skipped when `VITE_USE_MOCK=true`, or `skipAuthRefresh: true`.
8. **Logout** — clears session + token; RP-initiated logout at Keycloak end-session when possible.

## Realm requirement: the `basic` client scope

Keycloak only emits the `sub` claim in an access token when the client carries the built-in
`basic` client scope. Without it every request authenticates as a subject-less principal and
deny-by-default RBAC rejects it — the UI looks logged in but every call returns 401/403.

`infra/keycloak/itsm-realm.json` therefore declares the `basic` scope (with `oidc-sub-mapper`)
and lists it in `defaultClientScopes` for `itsm-spa` and `itsm-backend`. CI asserts this, the
Compose smoke asserts the minted token carries `sub`, and the backend rejects a token without a
subject instead of treating it as anonymous. Keep the fixed user ids in the realm import: they
are the RBAC `principal_role.subject_id` values seeded by migration V14.

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

## Backend audience

Resource-server JWT validation requires claim `aud` to contain `itsm-backend` (override with
`OIDC_AUDIENCE`). Missing or wrong audience is rejected. Local realm clients `itsm-spa` and
`itsm-backend` already map that audience onto access tokens, so anna/admin password-grant smoke
keeps working.

```yaml
spring.security.oauth2.resourceserver.jwt.audiences: ${OIDC_AUDIENCE:itsm-backend}
```

## Group-role mapping

Table `group_role_mapping` maps IdP group names (and matching Keycloak realm roles) to
`role.role_key` / `principal_role` assignments. Seeded mappings:

| IdP group | ITSM role |
| --- | --- |
| `ITSM-Users` | `REQUESTER` |
| `ITSM-ServiceDesk` | `SERVICE_DESK_AGENT` |
| `ITSM-ServiceDesk-Managers` | `SERVICE_DESK_MANAGER` |
| `ITSM-Change-Managers` | `CHANGE_MANAGER` |
| `ITSM-CAB` | `CAB_MEMBER` |
| `ITSM-Admins` | `ADMIN` |

On each authenticated request the backend upserts `identity_account` from JWT `iss` + `sub` and
grants mapped roles only. Unknown groups, including a bare `ADMIN` claim, do not grant `ADMIN`.
Disable an account with `identity_account.enabled = false`; the next request is rejected.
Demo users `anna` / `admin` still rely on V14 `principal_role` seeds plus the realm import — do
not remove those password-grant users from `itsm-realm.json`.

Production Keycloak imports [`itsm-realm-prod.json`](../../infra/keycloak/itsm-realm-prod.json):
no demo users, no password grant, no git-tracked `itsm-backend` secret. Directory join is
Keycloak User Federation over **LDAPS** — see [ad-ldap.md](./ad-ldap.md). Do not treat the
demo realm as directory-backed.

## Break-glass (Keycloak bootstrap admin)

The Keycloak **master** admin created by `KC_BOOTSTRAP_ADMIN_USERNAME` /
`KC_BOOTSTRAP_ADMIN_PASSWORD` is a break-glass IdP operator, not an ITSM application user.
It is not in the realm JSON and must never be `admin` / `admin` in production.

| Control | Requirement |
| --- | --- |
| Secret | Set `KC_ADMIN_PASSWORD` in the environment or secret store. `docker-compose.prod.yml` has **no** default and refuses to start if the variable is unset or empty. |
| Username | Prefer a unique `KC_ADMIN_USER`. Defaulting the local compose stack to `admin` is demo-only. |
| TOTP | In the **master** realm, enroll `CONFIGURE_TOTP` on the bootstrap user before exposing the admin console. |
| Audit | Prod realm has `eventsEnabled` + `adminEventsEnabled`. Ship Keycloak logs to the same audit sink as ITSM. |
| After first start | Bootstrap credentials apply only when no master admin exists. Rotate in the admin console; changing env on a running volume does nothing. |
| ITSM access | Break-glass for the **application** is an AD user in `ITSM-Admins` (see `group_role_mapping`), not this Keycloak master account. |

Never bake `admin`/`admin` or `itsm-backend-secret` into prod images, compose defaults, or
realm JSON. Retrieve the generated `itsm-backend` client secret from the admin console only
if a machine client needs it; the resource server validates JWTs and does not use that secret.

## Related

- [ad-ldap.md](./ad-ldap.md) — LDAPS user federation, group mapping, ADMIN OTP flow  
- [local-dev.md](./local-dev.md) — compose, backend profiles, frontend modes  
- [infra/keycloak/README.md](../../infra/keycloak/README.md) — realm, clients, users  
- [authorization.md](../security/authorization.md) — RBAC / JWT roles  

## Organization claim

Access tokens must include trusted string claim `organization_id`. Backend uses it for tenant
data isolation; local realm maps user attribute with same name through default `organization`
client scope. Provision every production user/service account with an organization attribute.
Never accept organization scope from request headers or body fields.
