# Continuous quality gate

GitHub Actions validates every pull request and protected branch push:

1. Node 22 installs the lockfile exactly and produces a TypeScript/Vite production build.
2. Temurin Java 25 runs Maven verification, including unit tests for locale fallback, business-calendar SLA handling and transactional Service Desk mutation semantics.

The next CI increments should add Testcontainers/PostgreSQL migration verification, Keycloak JWT authorization integration tests, API contract tests, Playwright accessibility/visual regression suites, dependency scanning and container-image scanning. These are explicit quality gates, not manual-release aspirations.
