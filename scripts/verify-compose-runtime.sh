#!/usr/bin/env bash
# Definition-of-done checks for the single-command Compose runtime (issue #20):
# a clean Flyway install, and PostgreSQL/MinIO data surviving a stack restart.
# Expects the stack to be running; safe to run repeatedly.
#
#   ./scripts/verify-compose-runtime.sh
#   BACKEND_URL=http://127.0.0.1:18090 ./scripts/verify-compose-runtime.sh
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8081}"
SMOKE_USER="${SMOKE_USER:-anna}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-anna}"

compose() { docker compose -f "${COMPOSE_FILE}" "$@"; }

passed=0
failed=0
ok()   { printf '  OK  %s\n' "$*"; passed=$((passed + 1)); }
fail() { printf '  FAIL %s\n' "$*"; failed=$((failed + 1)); }

echo "VOX ITSM Compose runtime verification — ${COMPOSE_FILE}"

# 1. Flyway applied the whole chain, with nothing failed or pending.
scripts_on_disk=$(find backend/src/main/resources/db/migration -name 'V*__*.sql' | wc -l | tr -d ' ')
applied=$(compose exec -T postgres psql -U itsm -d itsm -t -A \
  -c "select count(*) from flyway_schema_history where success and type = 'SQL';" | tr -d ' \r')
failures=$(compose exec -T postgres psql -U itsm -d itsm -t -A \
  -c "select count(*) from flyway_schema_history where not success;" | tr -d ' \r')

if [[ "${applied}" == "${scripts_on_disk}" ]]; then
  ok "Flyway applied every migration (${applied}/${scripts_on_disk})"
else
  fail "Flyway applied ${applied} of ${scripts_on_disk} migrations"
fi
if [[ "${failures}" == "0" ]]; then
  ok "no failed migrations recorded"
else
  fail "${failures} failed migrations in flyway_schema_history"
fi

# 2. Create a work item, restart the stack, and prove it is still there.
token() {
  curl -sS --connect-timeout 10 --max-time 20 -X POST \
    "${KEYCLOAK_URL}/realms/itsm/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&client_id=itsm-spa&username=${SMOKE_USER}&password=${SMOKE_PASSWORD}" \
    | tr ',' '\n' | grep -m1 access_token | cut -d'"' -f4
}

TOKEN="$(token)"
if [[ -z "${TOKEN}" ]]; then
  fail "could not obtain a token for ${SMOKE_USER}"
  echo "------------------------------------------------"
  echo "COMPOSE RUNTIME VERIFICATION FAILED (${failed} failed, ${passed} passed)"
  exit 1
fi

MARKER="persistence probe $(date -u +%Y%m%dT%H%M%SZ)"
CREATED=$(curl -sS -X POST "${BACKEND_URL}/api/v1/work-items" \
  -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
  -d "{\"type\":\"INCIDENT\",\"title\":\"${MARKER}\",\"description\":\"restart survival check\",\"service\":\"Platform\",\"impact\":\"LOW\",\"urgency\":\"LOW\"}")
ITEM_ID=$(printf '%s' "${CREATED}" | tr ',' '\n' | grep -m1 '"id"' | cut -d'"' -f4)

if [[ -n "${ITEM_ID}" ]]; then
  ok "created work item ${ITEM_ID}"
else
  fail "could not create a work item: ${CREATED:0:200}"
fi

echo "  ..  restarting the stack (volumes kept)"
compose down >/dev/null
compose up -d >/dev/null

for _ in $(seq 1 120); do
  curl -sf "${BACKEND_URL}/actuator/health" >/dev/null 2>&1 && break
  sleep 5
done

TOKEN="$(token)"
AFTER=$(curl -sS -o /dev/null -w '%{http_code}' \
  -H "Authorization: Bearer ${TOKEN}" "${BACKEND_URL}/api/v1/work-items/${ITEM_ID}")
if [[ "${AFTER}" == "200" ]]; then
  ok "work item survived the restart (HTTP ${AFTER})"
else
  fail "work item missing after restart (HTTP ${AFTER})"
fi

reapplied=$(compose exec -T postgres psql -U itsm -d itsm -t -A \
  -c "select count(*) from flyway_schema_history where success and type = 'SQL';" | tr -d ' \r')
if [[ "${reapplied}" == "${applied}" ]]; then
  ok "migration history unchanged after restart (${reapplied})"
else
  fail "migration history changed after restart (${applied} then ${reapplied})"
fi

BUCKETS=$(compose run --rm --entrypoint sh minio-init -c \
  "mc alias set local http://minio:9000 \${S3_ACCESS_KEY:-minioadmin} \${S3_SECRET_KEY:-minioadmin} >/dev/null && mc ls local" 2>/dev/null || true)
if printf '%s' "${BUCKETS}" | grep -q 'itsm-attachments'; then
  ok "MinIO bucket itsm-attachments survived the restart"
else
  fail "MinIO bucket itsm-attachments missing after restart"
fi

echo "------------------------------------------------"
if [[ "${failed}" -eq 0 ]]; then
  echo "COMPOSE RUNTIME VERIFICATION PASSED  (${passed} checks)"
  exit 0
fi
echo "COMPOSE RUNTIME VERIFICATION FAILED  (${failed} failed, ${passed} passed)"
exit 1
