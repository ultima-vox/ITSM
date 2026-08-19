#!/usr/bin/env bash
set -euo pipefail

# vox ITSM — smoke test for local installation
# Run after setup.sh to verify all services are operational.
#
# Usage: ./scripts/smoke-setup.sh

BOLD='\033[1m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

pass() { echo -e "${GREEN}✓${NC} $*"; }
fail() { echo -e "${RED}✗${NC} $*"; ERRORS=$((ERRORS + 1)); }
ERRORS=0

echo -e "${BOLD}=== vox ITSM smoke tests ===${NC}"
echo ""

# ── Infrastructure ──────────────────────────────────────────────────────────

echo -e "${BOLD}Infrastructure:${NC}"

if docker compose exec -T postgres pg_isready -U itsm -d itsm >/dev/null 2>&1; then
  pass "PostgreSQL reachable"
else
  fail "PostgreSQL unreachable"
fi

if docker compose exec -T redis redis-cli ping 2>/dev/null | grep -q PONG; then
  pass "Redis reachable"
else
  fail "Redis unreachable"
fi

if docker compose exec -T rabbitmq rabbitmq-diagnostics -q ping >/dev/null 2>&1; then
  pass "RabbitMQ reachable"
else
  fail "RabbitMQ unreachable"
fi

if curl -sf http://localhost:9200/_cluster/health >/dev/null 2>&1; then
  pass "OpenSearch reachable"
else
  fail "OpenSearch unreachable"
fi

if curl -sf http://localhost:9000/minio/health/live >/dev/null 2>&1; then
  pass "MinIO reachable"
else
  fail "MinIO unreachable"
fi

if curl -sf http://localhost:8081/realms/itsm/.well-known/openid-configuration >/dev/null 2>&1; then
  pass "Keycloak realm 'itsm' active"
else
  fail "Keycloak realm not ready"
fi

# ── Database ────────────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}Database:${NC}"

MIGRATIONS=$(docker compose exec -T postgres psql -U itsm -d itsm -t -c \
  "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true;" 2>/dev/null | tr -d ' ')
if [ -n "$MIGRATIONS" ] && [ "$MIGRATIONS" -gt 0 ] 2>/dev/null; then
  pass "$MIGRATIONS Flyway migrations applied"
else
  fail "No migrations applied (backend not started yet?)"
fi

TABLES=$(docker compose exec -T postgres psql -U itsm -d itsm -t -c \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public';" 2>/dev/null | tr -d ' ')
if [ -n "$TABLES" ] && [ "$TABLES" -gt 5 ] 2>/dev/null; then
  pass "$TABLES tables created"
else
  fail "Expected 5+ tables, found: $TABLES"
fi

# ── Backend API ─────────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}Backend API:${NC}"

BACKEND_UP=false
if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
  pass "Backend /actuator/health OK"
  BACKEND_UP=true
else
  fail "Backend not reachable at localhost:8080"
fi

if [ "$BACKEND_UP" = true ]; then
  # OpenAPI docs are ADMIN-gated outside profile dev, so a rejection is the healthy answer.
  DOCS_CODE=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/swagger-ui.html 2>/dev/null || echo "000")
  case "$DOCS_CODE" in
    200) pass "Swagger UI available (open — expected only under profile dev)" ;;
    401|403) pass "Swagger UI gated (HTTP $DOCS_CODE)" ;;
    *) fail "Swagger UI returned HTTP $DOCS_CODE" ;;
  esac

  # Public endpoint
  HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/v1/work-items 2>/dev/null || echo "000")
  if [ "$HTTP_CODE" = "401" ] || [ "$HTTP_CODE" = "200" ]; then
    pass "Work items endpoint responds (HTTP $HTTP_CODE)"
  else
    fail "Work items endpoint returned HTTP $HTTP_CODE"
  fi
fi

# ── Frontend ────────────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}Frontend:${NC}"

if curl -sf http://localhost/healthz >/dev/null 2>&1; then
  pass "Compose frontend at http://localhost"
elif curl -sf http://localhost:5173 >/dev/null 2>&1; then
  pass "Dev server running at http://localhost:5173"
elif curl -sf http://localhost:80 >/dev/null 2>&1; then
  pass "Frontend at http://localhost:80"
else
  fail "Frontend not reachable (docker compose up -d --build, or npm run dev)"
fi

# ── Keycloak auth ───────────────────────────────────────────────────────────

echo ""
echo -e "${BOLD}Authentication:${NC}"

TOKEN_RESP=$(curl -sf -X POST \
  'http://localhost:8081/realms/itsm/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password&client_id=itsm-spa&username=anna&password=anna' 2>/dev/null || echo "")
if echo "$TOKEN_RESP" | grep -q "access_token"; then
  pass "Keycloak password grant works (user: anna)"
else
  fail "Keycloak token endpoint failed"
fi

# ── Summary ─────────────────────────────────────────────────────────────────

echo ""
if [ "$ERRORS" -eq 0 ]; then
  echo -e "${GREEN}${BOLD}All checks passed!${NC}"
else
  echo -e "${RED}${BOLD}$ERRORS check(s) failed${NC}"
fi

exit $ERRORS
