# Production deployment

## Images

Build immutable application images from repository root:

```bash
docker build --pull -t registry.example/vox-itsm-backend:${VERSION} backend
docker build --pull -t registry.example/vox-itsm-frontend:${VERSION} frontend
```

Backend image runs as unprivileged user `itsm`; frontend runs as `nginx`. Both listen
on port `8080` and provide container health checks. Frontend proxies `/api/` to DNS
name `backend:8080` and serves SPA fallback routes.

## Required backend configuration

Inject values through deployment secrets/configuration; never bake them into images:

```text
SPRING_PROFILES_ACTIVE=prod,compose
DATABASE_URL=jdbc:postgresql://postgres:5432/itsm
DATABASE_USER=<secret reference>
DATABASE_PASSWORD=<secret reference>
OIDC_ISSUER_URI=https://id.example/realms/itsm
ITSM_CORS_ORIGINS=https://itsm.example
REDIS_HOST=redis
REDIS_PASSWORD=<secret reference>
RABBITMQ_HOST=rabbitmq
RABBITMQ_USER=<secret reference>
RABBITMQ_PASSWORD=<secret reference>
OPENSEARCH_URL=https://opensearch:9200
S3_ENDPOINT=https://s3.example
S3_BUCKET=itsm-attachments
S3_ACCESS_KEY=<secret reference>
S3_SECRET_KEY=<secret reference>
OTEL_EXPORTER_OTLP_ENABLED=true
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://otel-collector.observability.svc.cluster.local:4318/v1/traces
OTEL_TRACES_SAMPLER_ARG=0.1
```

Use TLS for every network hop. Provide CA certificates through platform trust stores.
Do not use `application-dev.yml`, default demo credentials, public MinIO buckets, or
OpenSearch with its security plugin disabled.

## Production-shaped Compose stack

`docker-compose.prod.yml` is the rehearsal stack. It differs from `docker-compose.yml`:

- every long-running service declares `restart: unless-stopped`;
- all images are pinned by digest;
- Keycloak runs `start` (not `start-dev`) against its own `keycloak-db` PostgreSQL service with a
  persistent volume, so realms, users, and sessions survive a restart;
- RabbitMQ has a data volume and explicit credentials (`RABBITMQ_USER` / `RABBITMQ_PASSWORD`)
  instead of the `guest` account.

Required overrides before it is a real deployment — `SPRING_PROFILES_ACTIVE=prod` refuses to start
until they are set (`ProductionSafetyGuard`):

```text
SPRING_PROFILES_ACTIVE=prod,compose
DB_PASSWORD, KC_DB_PASSWORD, KC_ADMIN_PASSWORD, RABBITMQ_PASSWORD, S3_ACCESS_KEY, S3_SECRET_KEY
OIDC_ISSUER_URI=https://…            # https is mandatory
ITSM_CORS_ORIGINS=https://itsm.example   # explicit https origins only, no wildcards/localhost
```

Still not production-grade in this file: OpenSearch runs with its security plugin disabled and
TLS terminates outside the stack. Use `deploy/kubernetes` for an actual production rollout.

Because Keycloak now stores state in `keycloak-db`, `--import-realm` only seeds a realm that does
not exist yet. Editing `infra/keycloak/itsm-realm.json` and redeploying changes nothing on an
existing installation: apply realm changes through the admin API/console, or recreate the
`keycloak_db_data` volume in a throwaway environment. The SPA origin must appear in the client's
Web Origins — Keycloak cannot derive it from a redirect URI that uses a wildcard port, and a
missing origin fails the token exchange with a CORS error after an otherwise successful login.

## Rollout order

1. Verify PostgreSQL backup and restore point.
2. Start one backend instance; Flyway applies forward-only migrations under deployment lock.
3. Confirm `/actuator/health/readiness` reports `UP`.
4. Roll remaining backend instances with readiness/liveness probes.
5. Roll frontend instances and verify `/healthz` plus security headers.
6. Run API, authentication, integration, and critical UI smoke suites.
7. Record image digests, migration version, test evidence, and rollback decision.

Never roll database schema backward. Roll application back only when prior version is
compatible with current schema; otherwise deploy a forward corrective migration.

## Runtime controls

- Run containers with read-only root filesystems where platform permits; mount only required temp paths.
- Drop Linux capabilities and enforce `no-new-privileges`.
- Use network policies: frontend→backend; backend→PostgreSQL/Redis/RabbitMQ/OpenSearch/S3/Keycloak only.
- Keep Swagger disabled or access-restricted at ingress in production.
- Export logs, metrics, and traces before accepting traffic.
- Schedule PostgreSQL backups and periodic restore exercises; retain object-storage versions separately.
