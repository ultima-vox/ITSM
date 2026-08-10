# OpenAPI client contract

Backend publishes OpenAPI 3.1 at `/v3/api-docs`. Canonical snapshot lives at
`frontend/openapi/itsm.json`; `openapi-typescript` generates
`frontend/src/api/generated/schema.d.ts`, and `openapi-fetch` supplies typed transport.

Regenerate after intentional API changes:

```bash
curl -fsS http://127.0.0.1:8080/v3/api-docs -o frontend/openapi/itsm.json
cd frontend
npm run api:generate
```

Commit snapshot and generated schema together. CI regenerates TypeScript and compares live
backend OpenAPI against snapshot, preventing silent server/client contract drift.
