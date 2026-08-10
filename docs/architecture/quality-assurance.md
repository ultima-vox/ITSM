# Continuous quality gate

The repository has an executable quality-gate baseline for pull requests:

1. Node 22 installs the lockfile exactly and produces a TypeScript/Vite production build.
2. Temurin Java 25 runs Gradle verification, including unit tests for locale fallback, business-calendar SLA handling and transactional Service Desk mutation semantics.

This execution environment currently lacks permission to add GitHub Actions workflow files, so the automation definition must be added once the GitHub connection has `workflows` permission. Until then these commands are the required local/CI checks:

```bash
npm ci --prefix frontend
npm run build --prefix frontend
backend/gradlew test --no-daemon
```

The next CI increments should add Testcontainers/PostgreSQL migration verification, Keycloak JWT authorization integration tests, API contract tests, Playwright accessibility/visual regression suites, dependency scanning and container-image scanning. These are explicit quality gates, not manual-release aspirations.
