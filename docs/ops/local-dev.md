# Local development

## Infrastructure

```bash
docker compose up -d
```

Starts PostgreSQL (`5432`), Redis (AOF), RabbitMQ, OpenSearch, MinIO (+ `itsm-attachments` bucket via `minio-init`), and Keycloak (`8081`). Defaults match `application.yml` (`itsm` / `itsm` / `itsm` for Postgres).

**Full integrations (Redis cache + OpenSearch + MinIO)** — use profile `compose` (see [compose-integrations.md](./compose-integrations.md)):

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev,compose'
.\scripts\smoke-integrations.ps1
```

Stop: `docker compose down`. Wipe volumes: `docker compose down -v`.

## Backend

Requirements: Java 25 and Postgres from compose. Gradle wrapper is included.

```bash
cd backend

# JWT required — Keycloak must be up (issuer http://localhost:8081/realms/itsm)
./gradlew bootRun

# Local demo without OIDC — NEVER use in production
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Useful URLs:

| Path | Notes |
| --- | --- |
| `http://localhost:8080/actuator/health` | Public health |
| `http://localhost:8080/swagger-ui.html` | OpenAPI UI |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON |

With profile `dev`, JWT resource-server auto-config is disabled and a synthetic principal `dev-local` is injected. All `/api/**` routes still go through application security; only OIDC validation is relaxed.

## Frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

| Mode | How |
| --- | --- |
| **Mock (default)** | `VITE_USE_MOCK` unset or not `false` — SPA uses in-browser mock data |
| **Live API** | `VITE_USE_MOCK=false` — Vite proxies `/api` → `http://localhost:8080` |

Live with **backend `dev`** (no JWT): leave `VITE_API_TOKEN` empty.

Live with **Keycloak JWT** (preferred: SPA OIDC login):

```bash
# .env.local
VITE_USE_MOCK=false
VITE_API_BASE=/api/v1
VITE_OIDC_ENABLED=true
VITE_OIDC_ISSUER=http://localhost:8081/realms/itsm
VITE_OIDC_CLIENT_ID=itsm-spa
VITE_OIDC_REDIRECT_URI=http://localhost:5173/auth/callback
```

Then use **Sign in** in the profile menu or Settings (demo user `anna` / `anna`). Full notes: [auth.md](./auth.md).

Development-only alternative — provide a token to Vite dev server:

```bash
VITE_API_TOKEN=<access_token>
```

Production builds ignore `VITE_API_TOKEN`. OAuth tokens remain in memory and are
cleared on reload; sign in again after a reload.

Settings page shows Mock/Live pill and OIDC status when enabled.

Optional: `VITE_API_BASE` overrides API base (default `/api/v1`).

Quality scripts:

```bash
npm run typecheck      # tsc --noEmit
npm run build          # typecheck + vite build
npm run build:check    # same as build (CI-friendly alias)
npm run test:e2e       # Playwright smoke (mock; uses 127.0.0.1:5173)
```

CI (GitHub Actions on `main` / PR): frontend typecheck + build + Playwright, backend `./gradlew test`, shell syntax on smoke scripts.

### Playwright smoke E2E

Mock mode is default (`VITE_USE_MOCK` unset / not `false`), so no backend is required.

```bash
cd frontend
npx playwright install chromium   # once per machine
npm run test:e2e                  # headless Chromium smoke
npm run test:e2e:ui               # Playwright UI mode
```

Config: `frontend/playwright.config.ts` (`baseURL` `http://127.0.0.1:5173` / port 5173, starts `npm run dev` via `webServer`, reuses an already-running Vite when not in CI). Specs live in `frontend/e2e/`. Using `127.0.0.1` avoids Windows resolving `localhost` to a different IPv6 process on the same port.

## Keycloak token flow

Issuer: `http://localhost:8081/realms/itsm` (`OIDC_ISSUER_URI`). Realm import: `infra/keycloak/itsm-realm.json`.

Demo users: `anna`/`anna` (agent), `admin`/`admin`, `requester`/`requester`.

```bash
curl -s -X POST "http://localhost:8081/realms/itsm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=itsm-spa" \
  -d "username=anna" \
  -d "password=anna"
```

Use `access_token` as `Authorization: Bearer …` for CLI calls. `VITE_API_TOKEN`
is restricted to the Vite development server.

Non-dev profile: all `/api/**` need valid OIDC JWT. Public: health + swagger only.

See: [infra/keycloak/README.md](../../infra/keycloak/README.md), [authorization](../security/authorization.md).

## API smoke

With the backend running:

```powershell
# Windows
.\scripts\smoke-api.ps1
.\scripts\smoke-integrations.ps1   # Redis / OpenSearch / MinIO
.\scripts\smoke-api.ps1 -BaseUrl http://localhost:8080 -Token $env:ITSM_TOKEN
```

```bash
# CI / Unix
chmod +x scripts/smoke-api.sh
./scripts/smoke-api.sh
TOKEN="$ITSM_TOKEN" ./scripts/smoke-api.sh http://localhost:8080
```

Checks: `/actuator/health`, Swagger UI, `/v3/api-docs`, and optionally `GET /api/v1/work-items` when a token is provided. Exits non-zero on failure.
