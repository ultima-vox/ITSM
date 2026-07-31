# Compose integrations: Redis · OpenSearch · MinIO

Default `application.yml` keeps Redis **off** and OpenSearch **URL empty** so unit tests and bare `mvn spring-boot:run` need no infra.

Profile **`compose`** turns the full stack **on** against `docker-compose.yml`.

## What gets enabled

| Integration | Config | Behaviour |
|-------------|--------|-----------|
| **Redis** | `itsm.redis.enabled=true` | `CachePort` → Redis + concurrent-map fallback; locale prefs cached 15m |
| **OpenSearch** | `itsm.opensearch.url=http://localhost:9200` | Search index HTTP; index auto-created on startup; work-item create/update projected |
| **MinIO / S3** | `itsm.storage.type=s3` | Attachments go to bucket `itsm-attachments` |
| **RabbitMQ** | always (default) | Outbox relay (unchanged) |
| **PostgreSQL** | always | System of record |

## Start

```bash
# 1) Infra (Postgres, Redis AOF, OpenSearch, MinIO+bucket, RabbitMQ, Keycloak)
docker compose up -d

# 2) Wait until healthy
docker compose ps
# Optional:
#   ./scripts/smoke-integrations.ps1   # Windows
#   ./scripts/smoke-integrations.sh    # Unix

# 3) Backend: dev (open API) + compose (integrations)
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev,compose"
```

Or load env from `backend/.env.compose` then run with the same profiles.

## Verify

| Check | URL / command |
|-------|----------------|
| Actuator | `GET http://localhost:8080/actuator/health` — look for `redisCache`, `opensearch` components when profile compose |
| Integrations API | `GET /api/v1/platform/integrations` (auth + `metadata.read`) |
| Search | Create a work item → `GET /api/v1/search?q=INC` |
| OpenSearch raw | `curl http://localhost:9200/itsm/_search?q=*` |
| Redis | `redis-cli PING` → `PONG` |
| MinIO console | http://localhost:9001 (`minioadmin` / `minioadmin`) |

## Profile matrix

| Profiles | OIDC | Redis | OpenSearch | Storage |
|----------|------|-------|------------|---------|
| _(default)_ | JWT required | off | JDBC search | local metadata |
| `dev` | synthetic `dev-local` | off | JDBC | local |
| `compose` | JWT (unless +dev) | **on** | **OpenSearch** | **S3/MinIO** |
| `dev,compose` | synthetic | **on** | **OpenSearch** | **S3/MinIO** |

## Degradation

- Redis down → `FallbackCachePort` uses in-process map; requests continue.
- OpenSearch down → index/search log warnings; search returns empty; mutations still commit.
- MinIO down → attachment upload fails with storage error (expected).

## Disable again

Run without `compose` profile, or set:

```bash
ITSM_REDIS_ENABLED=false
OPENSEARCH_URL=
ITSM_STORAGE_TYPE=local
```
