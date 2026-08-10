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
```

Use TLS for every network hop. Provide CA certificates through platform trust stores.
Do not use `application-dev.yml`, default demo credentials, public MinIO buckets, or
OpenSearch with its security plugin disabled.

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
