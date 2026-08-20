#!/usr/bin/env bash
# Every metric the alert rules and the dashboard read must actually be exported, otherwise
# the alert is silently dead. Scrapes the running backend and compares.
#
#   ./scripts/verify-observability.sh
#   BACKEND_URL=http://127.0.0.1:28080 ./scripts/verify-observability.sh
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8081}"
ADMIN_USER="${ITSM_ADMIN_USER:-admin}"
ADMIN_PASSWORD="${ITSM_ADMIN_PASSWORD:-admin}"
RULES="${RULES:-deploy/observability/prometheus-rules.yml}"
DASHBOARD="${DASHBOARD:-deploy/observability/grafana-dashboard.json}"

scrape="$(mktemp)"
trap 'rm -f "${scrape}"' EXIT

TOKEN=$(curl -sS --connect-timeout 10 --max-time 20 -X POST \
  "${KEYCLOAK_URL}/realms/itsm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=itsm-spa&username=${ADMIN_USER}&password=${ADMIN_PASSWORD}" \
  | tr ',' '\n' | grep -m1 access_token | cut -d'"' -f4)

code=$(curl -sS -o "${scrape}" -w '%{http_code}' \
  -H "Authorization: Bearer ${TOKEN}" "${BACKEND_URL}/actuator/prometheus")
if [[ "${code}" != "200" ]]; then
  echo "  FAIL /actuator/prometheus returned ${code}"
  exit 1
fi

# Metric names look like foo_bar_seconds_count / jvm_memory_used_bytes and appear before a
# label brace or a range selector in both files.
metrics=$( { cat "${RULES}"; cat "${DASHBOARD}"; } \
  | grep -oE '\b(up|[a-z][a-z0-9_]*_(seconds_count|seconds_bucket|seconds_sum|bytes|total|active|max))\b' \
  | sort -u )

passed=0
failed=0
for metric in ${metrics}; do
  if [[ "${metric}" == "up" ]]; then
    continue  # produced by Prometheus itself, not by the application
  fi
  if grep -q "^${metric}{" "${scrape}" || grep -q "^${metric} " "${scrape}"; then
    printf '  OK  %s exported\n' "${metric}"
    passed=$((passed + 1))
  else
    printf '  FAIL %s referenced by alerts/dashboard but never exported\n' "${metric}"
    failed=$((failed + 1))
  fi
done

echo "------------------------------------------------"
if [[ "${failed}" -eq 0 ]]; then
  echo "OBSERVABILITY CONTRACT PASSED  (${passed} metrics)"
  exit 0
fi
echo "OBSERVABILITY CONTRACT FAILED  (${failed} missing, ${passed} present)"
exit 1
