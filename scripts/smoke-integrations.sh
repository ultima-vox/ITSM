#!/usr/bin/env bash
# Smoke-check Redis, OpenSearch, MinIO and optional backend integration endpoints.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
OPENSEARCH_URL="${OPENSEARCH_URL:-http://localhost:9200}"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
MINIO_URL="${MINIO_URL:-http://localhost:9000/minio/health/live}"
TOKEN="${TOKEN:-}"

failed=0
ok() { echo "  OK  $*"; }
fail() { echo "  FAIL $*"; failed=$((failed + 1)); }

echo "=== Integration smoke ==="

if command -v redis-cli >/dev/null 2>&1; then
  if [[ "$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" PING 2>/dev/null)" == "PONG" ]]; then
    ok "Redis PING $REDIS_HOST:$REDIS_PORT"
  else
    fail "Redis PING failed"
  fi
else
  if (echo >/dev/tcp/"$REDIS_HOST"/"$REDIS_PORT") >/dev/null 2>&1; then
    ok "Redis TCP $REDIS_HOST:$REDIS_PORT open"
  else
    fail "Redis TCP closed"
  fi
fi

if curl -sf "$OPENSEARCH_URL/_cluster/health" >/tmp/os-health.json; then
  status=$(python3 -c "import json;print(json.load(open('/tmp/os-health.json')).get('status',''))" 2>/dev/null || echo unknown)
  if [[ "$status" == "green" || "$status" == "yellow" ]]; then
    ok "OpenSearch cluster status=$status"
  else
    fail "OpenSearch status=$status"
  fi
else
  fail "OpenSearch unreachable"
fi

if curl -sf "$MINIO_URL" >/dev/null; then
  ok "MinIO health"
else
  fail "MinIO unreachable"
fi

if curl -sf "$BASE_URL/actuator/health" >/dev/null; then
  ok "Backend actuator health"
else
  echo "  SKIP backend actuator"
fi

if [[ -n "$TOKEN" ]]; then
  if curl -sf -H "Authorization: Bearer $TOKEN" "$BASE_URL/api/v1/platform/integrations" >/dev/null; then
    ok "platform/integrations"
  else
    fail "platform/integrations"
  fi
fi

if [[ "$failed" -gt 0 ]]; then
  echo "INTEGRATIONS SMOKE FAILED ($failed)"
  exit 1
fi
echo "INTEGRATIONS SMOKE PASSED"
