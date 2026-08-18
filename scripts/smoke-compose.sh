#!/usr/bin/env bash
# Live-mode smoke against the full Compose stack (no mock).
# Usage:
#   ./scripts/smoke-compose.sh
#   FRONTEND_URL=http://127.0.0.1:28000 BACKEND_URL=http://127.0.0.1:28080 \
#     KEYCLOAK_URL=http://127.0.0.1:28081 ./scripts/smoke-compose.sh
set -euo pipefail

FRONTEND_URL="${FRONTEND_URL:-http://127.0.0.1}"
BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://127.0.0.1:8081}"
FRONTEND_URL="${FRONTEND_URL%/}"
BACKEND_URL="${BACKEND_URL%/}"
KEYCLOAK_URL="${KEYCLOAK_URL%/}"

passed=0
failed=0

ok()   { printf '  OK  %s\n' "$*"; passed=$((passed + 1)); }
fail() { printf '  FAIL %s\n' "$*"; failed=$((failed + 1)); }

http_code() {
  curl -sS -o /tmp/itsm-compose-body.$$ -w "%{http_code}" \
    --connect-timeout 10 --max-time 20 -L "$@" || true
}

check() {
  local name="$1"
  local url="$2"
  shift 2 || true
  local code
  code=$(http_code "$url" "$@")
  if [[ "${code}" =~ ^2[0-9][0-9]$ ]]; then
    ok "$name (${code}) $url"
  else
    fail "$name status ${code:-000} $url"
  fi
}

cleanup() { rm -f /tmp/itsm-compose-body.$$ /tmp/itsm-compose-token.$$ 2>/dev/null || true; }
trap cleanup EXIT

echo "VOX ITSM Compose smoke — frontend ${FRONTEND_URL}  backend ${BACKEND_URL}"

check "backend health" "${BACKEND_URL}/actuator/health"
check "backend swagger" "${BACKEND_URL}/swagger-ui.html"
check "backend openapi" "${BACKEND_URL}/v3/api-docs"
check "frontend healthz" "${FRONTEND_URL}/healthz"
check "frontend index" "${FRONTEND_URL}/"
check "keycloak realm" "${KEYCLOAK_URL}/realms/itsm/.well-known/openid-configuration"

TOKEN=""
if curl -sS --connect-timeout 10 --max-time 20 -X POST \
  "${KEYCLOAK_URL}/realms/itsm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=itsm-spa&username=anna&password=anna" \
  -o /tmp/itsm-compose-token.$$; then
  TOKEN=$(sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p' /tmp/itsm-compose-token.$$ | head -1)
fi

if [[ -n "${TOKEN}" ]]; then
  ok "keycloak password grant (anna)"
  check "work-items via backend" "${BACKEND_URL}/api/v1/work-items" \
    -H "Authorization: Bearer ${TOKEN}" -H "Accept: application/json"
  check "work-items via frontend proxy" "${FRONTEND_URL}/api/v1/work-items" \
    -H "Authorization: Bearer ${TOKEN}" -H "Accept: application/json"
else
  fail "keycloak password grant (anna)"
fi

echo "------------------------------------------------"
if [[ "${failed}" -eq 0 ]]; then
  echo "COMPOSE SMOKE PASSED  (${passed} checks)"
  exit 0
fi
echo "COMPOSE SMOKE FAILED  (${failed} failed, ${passed} passed)"
exit 1
