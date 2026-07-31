#!/usr/bin/env bash
# Lightweight API smoke checks for VOX ITSM (CI / Unix twin of smoke-api.ps1).
# Usage:
#   ./scripts/smoke-api.sh
#   ./scripts/smoke-api.sh http://localhost:8080
#   TOKEN=... ./scripts/smoke-api.sh http://localhost:8080
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"
TOKEN="${TOKEN:-${2:-}}"

passed=0
failed=0

green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
red()   { printf '\033[0;31m%s\033[0m\n' "$*"; }
cyan()  { printf '\033[0;36m%s\033[0m\n' "$*"; }
yellow(){ printf '\033[0;33m%s\033[0m\n' "$*"; }

ok()   { green "  OK  $*"; passed=$((passed + 1)); }
fail() { red   "  FAIL $*"; failed=$((failed + 1)); }

# GET url [extra curl args...]
# Sets global _http_code
http_get() {
  local url="$1"
  shift || true
  _http_code=$(curl -sS -o /tmp/itsm-smoke-body.$$ -w "%{http_code}" \
    --connect-timeout 10 --max-time 15 \
    -L "$@" "$url" 2>/tmp/itsm-smoke-err.$$ || true)
  if [[ -z "${_http_code}" || "${_http_code}" == "000" ]]; then
    _http_code="000"
  fi
}

check() {
  local name="$1"
  local url="$2"
  shift 2 || true
  http_get "$url" "$@"
  if [[ "${_http_code}" =~ ^2[0-9][0-9]$ ]]; then
    ok "$name (${_http_code}) $url"
  else
    local err=""
    if [[ -s /tmp/itsm-smoke-err.$$ ]]; then
      err=" — $(head -c 200 /tmp/itsm-smoke-err.$$)"
    fi
    fail "$name status ${_http_code} $url${err}"
  fi
}

cleanup() {
  rm -f /tmp/itsm-smoke-body.$$ /tmp/itsm-smoke-err.$$ 2>/dev/null || true
}
trap cleanup EXIT

echo ""
cyan "VOX ITSM API smoke — ${BASE_URL}"
echo "------------------------------------------------"

# 1. Health (public)
check "actuator health" "${BASE_URL}/actuator/health"

# 2. Swagger UI (public; follow redirects)
check "swagger-ui" "${BASE_URL}/swagger-ui.html"

# OpenAPI docs JSON (public)
check "openapi docs" "${BASE_URL}/v3/api-docs"

# 3. Optional work-items with Bearer token
if [[ -n "${TOKEN}" ]]; then
  check "work-items list" "${BASE_URL}/api/v1/work-items" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Accept: application/json"
else
  yellow "  SKIP work-items list (no TOKEN)"
fi

echo "------------------------------------------------"
if [[ "${failed}" -eq 0 ]]; then
  green "SMOKE PASSED  (${passed} checks)"
  echo ""
  exit 0
fi

red "SMOKE FAILED  (${failed} failed, ${passed} passed)"
echo ""
exit 1
