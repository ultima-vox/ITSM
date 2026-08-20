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

`docker-compose.prod.yml` is the rehearsal stack. An nginx `edge` service is the only ingress:
it terminates TLS for the SPA and for Keycloak and forwards `X-Forwarded-*`, so no application
port is published. Nothing else changes for the containers behind it.

```bash
cp .env.prod.example .env          # then replace every secret
./scripts/gen-tls-cert.sh          # or point ITSM_TLS_DIR at your own certificates
./scripts/gen-opensearch-certs.sh  # transport certificates for the search cluster
OPENSEARCH_ADMIN_PASSWORD=… OPENSEARCH_PASSWORD=… ./scripts/gen-opensearch-users.sh
docker compose -f docker-compose.prod.yml up -d --build
```

With `SPRING_PROFILES_ACTIVE=prod` the backend refuses to start unless the issuer is https,
the CORS origins are explicit non-local https URLs, and no secret is left at a demo value —
the defaults in `.env.prod.example` satisfy the shape, not the secrecy.

It differs from `docker-compose.yml`:

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

OpenSearch runs with its security plugin enabled: anonymous requests are rejected and the
backend authenticates as a user restricted to the `itsm*` indices. Its HTTP layer stays
plaintext inside the private container network — set `plugins.security.ssl.http.enabled` and the
http pem paths in `deploy/opensearch/opensearch.yml` to terminate TLS there as well, and supply
the CA to the backend JVM truststore.

Still not production-grade in this file: `scripts/gen-tls-cert.sh` issues a self-signed
certificate that no client trusts by default, and there is no high availability. Use
`deploy/kubernetes` with certificates from your own CA for an actual production rollout.

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
