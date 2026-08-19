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

cleanup() { rm -f /tmp/itsm-compose-body.$$ /tmp/itsm-compose-token.$$ /tmp/itsm-compose-admin.$$ 2>/dev/null || true; }
trap cleanup EXIT

echo "VOX ITSM Compose smoke — frontend ${FRONTEND_URL}  backend ${BACKEND_URL}"

check "backend health" "${BACKEND_URL}/actuator/health"
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

jwt_claim() {
  local payload
  payload=$(printf '%s' "$1" | cut -d. -f2)
  case $(( ${#payload} % 4 )) in
    2) payload="${payload}==" ;;
    3) payload="${payload}=" ;;
  esac
  printf '%s' "${payload}" | tr '_-' '/+' | base64 -d 2>/dev/null \
    | sed -n "s/.*\"$2\":\"\([^\"]*\)\".*/\1/p"
}

if [[ -n "${TOKEN}" ]]; then
  ok "keycloak password grant (anna)"
  if [[ -n "$(jwt_claim "${TOKEN}" sub)" ]]; then
    ok "access token carries sub claim"
  else
    fail "access token has no sub claim (client is missing the 'basic' client scope)"
  fi
  check "work-items via backend" "${BACKEND_URL}/api/v1/work-items" \
    -H "Authorization: Bearer ${TOKEN}" -H "Accept: application/json"
  check "work-items via frontend proxy" "${FRONTEND_URL}/api/v1/work-items" \
    -H "Authorization: Bearer ${TOKEN}" -H "Accept: application/json"
else
  fail "keycloak password grant (anna)"
fi

# API documentation is ADMIN-only: anonymous must be rejected, ADMIN must pass.
for path in /swagger-ui.html /v3/api-docs; do
  code=$(http_code "${BACKEND_URL}${path}")
  if [[ "${code}" == "401" || "${code}" == "403" ]]; then
    ok "api docs reject anonymous (${code}) ${path}"
  else
    fail "api docs reachable without authentication (${code}) ${path}"
  fi
done

ADMIN_TOKEN=""
if curl -sS --connect-timeout 10 --max-time 20 -X POST \
  "${KEYCLOAK_URL}/realms/itsm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=itsm-spa&username=${ITSM_ADMIN_USER:-admin}&password=${ITSM_ADMIN_PASSWORD:-admin}" \
  -o /tmp/itsm-compose-admin.$$; then
  ADMIN_TOKEN=$(tr ',' '\n' < /tmp/itsm-compose-admin.$$ | grep -m1 access_token | cut -d'"' -f4)
fi

if [[ -n "${ADMIN_TOKEN}" ]]; then
  ok "keycloak password grant (admin)"
  check "backend swagger as ADMIN" "${BACKEND_URL}/swagger-ui.html"     -H "Authorization: Bearer ${ADMIN_TOKEN}"
  check "backend openapi as ADMIN" "${BACKEND_URL}/v3/api-docs"     -H "Authorization: Bearer ${ADMIN_TOKEN}"
else
  fail "keycloak password grant (admin)"
fi

echo "------------------------------------------------"
if [[ "${failed}" -eq 0 ]]; then
  echo "COMPOSE SMOKE PASSED  (${passed} checks)"
  exit 0
fi
echo "COMPOSE SMOKE FAILED  (${failed} failed, ${passed} passed)"
exit 1
