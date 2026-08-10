# UX quality gates

Review each key surface at 320px, 768px, 1024px and 1440px. Verify keyboard-only navigation, visible focus, 200% zoom/reflow, contrast (WCAG 2.2 AA minimum), loading/empty/error/degraded states, Russian and English expansion, and no color-only status semantics.

The operator workspace intentionally prioritizes scan speed: fixed hierarchy, compact status chips, clear SLA urgency, consistent 8px rhythm and non-destructive creation actions. “AAA” is not accepted as a subjective claim; accessibility, task success, latency, visual-regression and usability criteria must be measured in CI before release.

## CI command checklist

Run before merge / release. Fail the pipeline on non-zero exit.

### Frontend

```bash
cd frontend
npm ci
npm run typecheck      # tsc --noEmit
npm run build          # tsc --noEmit && vite build
# or: npm run build:check
npm run test:e2e       # Playwright smoke (mock mode)
```

GitHub Actions (`.github/workflows/ci.yml`) runs typecheck + build + e2e + backend tests on PR/`main`.

### Backend API smoke

Backend must be reachable (compose + `./gradlew bootRun`, or deployed target).

```bash
# Unix / CI
./scripts/smoke-api.sh http://localhost:8080
# with JWT for work-items list:
TOKEN="$ITSM_TOKEN" ./scripts/smoke-api.sh http://localhost:8080
```

```powershell
# Windows
.\scripts\smoke-api.ps1 -BaseUrl http://localhost:8080
.\scripts\smoke-api.ps1 -BaseUrl http://localhost:8080 -Token $env:ITSM_TOKEN
```

Smoke covers `/actuator/health`, Swagger UI / OpenAPI docs, and optional `GET /api/v1/work-items` when a Bearer token is supplied. See [local-dev](../ops/local-dev.md).

### Integrations smoke (Redis / OpenSearch / MinIO)

With `docker compose up -d` and backend profiles `dev,compose`:

```powershell
.\scripts\smoke-integrations.ps1
.\scripts\smoke-integrations.ps1 -Token $env:ITSM_TOKEN
```

```bash
./scripts/smoke-integrations.sh
TOKEN="$ITSM_TOKEN" ./scripts/smoke-integrations.sh
```

See [compose-integrations](../ops/compose-integrations.md).
