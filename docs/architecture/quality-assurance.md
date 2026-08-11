# Continuous quality gate

`.github/workflows/ci.yml` is mandatory on pushes and pull requests to `main`.

Current gates:

1. Node 22 lockfile install, strict TypeScript, ESLint, Vitest, production Vite build,
   generated OpenAPI drift check, and Playwright mock-mode E2E.
2. Temurin Java 25 Gradle tests, including Spring Modulith/ArchUnit boundaries,
   domain rules, authorization, and Testcontainers PostgreSQL Flyway verification.
3. Live PostgreSQL backend plus Playwright API/UI contract E2E and live OpenAPI snapshot diff.
4. Kubernetes/Kustomize schema validation and Prometheus/Grafana asset validation.
5. Trivy filesystem and container vulnerability, secret, and misconfiguration gates for
   HIGH/CRITICAL findings; pull-request dependency review; SPDX SBOM artifact generation.
6. CodeQL `security-extended` SAST for Java and JavaScript/TypeScript.
7. Shell syntax and Keycloak organization-claim validation.

Manual workflow dispatch additionally runs k6 load regression and full integration-compose
smoke. Production release still requires review of scan findings, live accessibility evidence,
backup/restore evidence, and signed image digests; a green CI run alone is not release approval.

Equivalent local baseline:

```bash
npm ci --prefix frontend
npm run typecheck --prefix frontend
npm run lint --prefix frontend
npm test --prefix frontend
npm run build --prefix frontend
backend/gradlew test --no-daemon
```
