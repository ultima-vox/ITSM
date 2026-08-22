#!/usr/bin/env bash
# Live smoke for the release, worklog, on-call and announcement endpoints.
# Usage:
#   ./scripts/smoke-modules.sh
#   BACKEND_URL=http://127.0.0.1:18080 ./scripts/smoke-modules.sh
set -uo pipefail

BACKEND="${BACKEND_URL:-http://127.0.0.1:8080}"
KC="${KEYCLOAK_URL:-http://127.0.0.1:8081}"

passed=0
failed=0
ok()   { printf '  OK   %s\n' "$*"; passed=$((passed + 1)); }
bad()  { printf '  FAIL %s\n' "$*"; failed=$((failed + 1)); }

token() {
  curl -s -X POST "$KC/realms/itsm/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=itsm-spa \
    -d "username=$1" -d "password=$2" | python -c "import sys,json;print(json.load(sys.stdin).get('access_token',''))"
}

ADMIN_TOKEN="$(token admin admin)"
[ -n "$ADMIN_TOKEN" ] || { echo "no admin token"; exit 1; }
AUTH=(-H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json')

req() { # method path body -> prints "status<TAB>body"
  local method="$1" path="$2" body="${3:-}"
  if [ -n "$body" ]; then
    curl -s -w '\n%{http_code}' -X "$method" "${AUTH[@]}" -d "$body" "$BACKEND$path"
  else
    curl -s -w '\n%{http_code}' -X "$method" "${AUTH[@]}" "$BACKEND$path"
  fi
}

status() { printf '%s' "$1" | tail -n1; }
payload() { printf '%s' "$1" | sed '$d'; }

json() { python -c "import sys,json;d=json.load(sys.stdin);print(eval('d'+sys.argv[1]))" "$1"; }

echo "VOX ITSM module smoke — releases, worklogs, on-call, announcements — $BACKEND"

# --- releases -------------------------------------------------------------
r="$(req POST /api/v1/releases '{"name":"Smoke release","type":"MINOR","description":"endpoint check","deploymentPlan":"Blue-green","rollbackPlan":"Back to blue"}')"
[ "$(status "$r")" = "201" ] && ok "create release (201)" || bad "create release ($(status "$r")) $(payload "$r")"
REL_ID="$(payload "$r" | json "['id']")"
REL_VER="$(payload "$r" | json "['version']")"

r="$(req GET /api/v1/releases)"
[ "$(status "$r")" = "200" ] && ok "list releases (200, total $(payload "$r" | json "['total']"))" || bad "list releases ($(status "$r"))"

r="$(req POST "/api/v1/releases/$REL_ID/transitions" "{\"target\":\"BUILD\",\"expectedVersion\":$REL_VER}")"
[ "$(status "$r")" = "200" ] && ok "release PLANNING -> BUILD (200)" || bad "release transition ($(status "$r")) $(payload "$r")"
REL_VER="$(payload "$r" | json "['version']")"

r="$(req POST "/api/v1/releases/$REL_ID/transitions" "{\"target\":\"DEPLOYING\",\"expectedVersion\":$REL_VER}")"
[ "$(status "$r")" = "409" ] && ok "illegal release jump refused (409)" || bad "illegal release jump ($(status "$r")) $(payload "$r")"

r="$(req GET "/api/v1/releases/$REL_ID/changes")"
[ "$(status "$r")" = "200" ] && ok "release content (200, deployable $(payload "$r" | json "['deployable']"))" || bad "release content ($(status "$r"))"

# --- worklogs -------------------------------------------------------------
r="$(req GET '/api/v1/work-items?size=1')"
WI_ID="$(payload "$r" | python -c "
import sys,json
d=json.load(sys.stdin)
items=d['items'] if isinstance(d,dict) and 'items' in d else d
print(items[0]['id'] if items else '')
")"
if [ -n "$WI_ID" ]; then
  STARTED="$(python -c "import datetime;print((datetime.datetime.now(datetime.UTC)-datetime.timedelta(hours=1)).strftime('%Y-%m-%dT%H:%M:%SZ'))")"
  r="$(req POST "/api/v1/work-items/$WI_ID/worklogs" "{\"minutes\":45,\"startedAt\":\"$STARTED\",\"note\":\"endpoint check\",\"billable\":true}")"
  [ "$(status "$r")" = "201" ] && ok "log time (201)" || bad "log time ($(status "$r")) $(payload "$r")"
  WL_ID="$(payload "$r" | json "['id']")"

  r="$(req GET "/api/v1/work-items/$WI_ID/worklogs")"
  [ "$(status "$r")" = "200" ] && ok "worklog rollup (200, total $(payload "$r" | json "['totalMinutes']")m billable $(payload "$r" | json "['billableMinutes']")m)" \
    || bad "worklog rollup ($(status "$r"))"

  r="$(req POST "/api/v1/work-items/$WI_ID/worklogs" "{\"minutes\":0,\"startedAt\":\"$STARTED\"}")"
  [ "$(status "$r")" = "400" ] && ok "zero-minute entry refused (400)" || bad "zero-minute entry ($(status "$r"))"

  r="$(req DELETE "/api/v1/work-items/$WI_ID/worklogs/$WL_ID")"
  [ "$(status "$r")" = "204" ] && ok "delete own worklog (204)" || bad "delete worklog ($(status "$r"))"
else
  bad "no work item available for the worklog checks"
fi

# --- on-call --------------------------------------------------------------
r="$(req POST /api/v1/oncall/schedules '{"scheduleKey":"smoke-rota","name":"Smoke rota","timeZone":"UTC","rotationHours":168,"rotationStart":"2026-08-03T09:00:00Z","participants":["anna","boris","clara"]}')"
[ "$(status "$r")" = "201" ] && ok "create rotation (201)" || bad "create rotation ($(status "$r")) $(payload "$r")"

r="$(req GET '/api/v1/oncall/schedules/smoke-rota/current?at=2026-08-11T10:00:00Z')"
SUBJ="$(payload "$r" | json "['subject']")"
[ "$SUBJ" = "boris" ] && ok "rotation resolves week 2 to boris" || bad "rotation resolved '$SUBJ' ($(status "$r"))"

r="$(req POST /api/v1/oncall/schedules/smoke-rota/overrides '{"subject":"dave","startsAt":"2026-08-11T08:00:00Z","endsAt":"2026-08-11T20:00:00Z","reason":"cover"}')"
[ "$(status "$r")" = "201" ] && ok "add override (201)" || bad "add override ($(status "$r")) $(payload "$r")"

r="$(req GET '/api/v1/oncall/schedules/smoke-rota/current?at=2026-08-11T10:00:00Z')"
SUBJ="$(payload "$r" | json "['subject']")"
[ "$SUBJ" = "dave" ] && ok "override wins over the rotation" || bad "override resolved '$SUBJ'"

r="$(req POST /api/v1/oncall/policies '{"policyKey":"work-item.escalation","name":"Smoke escalation","steps":[{"delayMinutes":0,"targetType":"SCHEDULE","targetRef":"smoke-rota"},{"delayMinutes":15,"targetType":"SUBJECT","targetRef":"duty-manager"}]}')"
[ "$(status "$r")" = "201" ] && ok "create escalation policy (201)" || bad "create policy ($(status "$r")) $(payload "$r")"

r="$(req GET '/api/v1/oncall/policies/work-item.escalation/chain?at=2026-08-11T10:00:00Z')"
CHAIN="$(payload "$r" | python -c "import sys,json;print(','.join(x['subject'] for x in json.load(sys.stdin)))")"
[ "$CHAIN" = "dave,duty-manager" ] && ok "escalation chain resolves to $CHAIN" || bad "escalation chain '$CHAIN' ($(status "$r"))"

# --- announcements --------------------------------------------------------
r="$(req POST /api/v1/announcements '{"title":"Smoke outage","body":"Endpoint check","severity":"CRITICAL","audience":"ALL","startsAt":"2026-01-01T00:00:00Z","published":true}')"
[ "$(status "$r")" = "201" ] && ok "create announcement (201)" || bad "create announcement ($(status "$r")) $(payload "$r")"
ANN_ID="$(payload "$r" | json "['id']")"

r="$(req GET /api/v1/announcements/active)"
COUNT="$(payload "$r" | python -c "import sys,json;print(len(json.load(sys.stdin)))")"
[ "$(status "$r")" = "200" ] && [ "$COUNT" -ge 1 ] && ok "active announcements (200, $COUNT)" || bad "active announcements ($(status "$r"), $COUNT)"

r="$(req POST "/api/v1/announcements/$ANN_ID/retire" '')"
[ "$(status "$r")" = "200" ] && ok "retire announcement (200)" || bad "retire announcement ($(status "$r"))"

r="$(req GET /api/v1/announcements/active)"
COUNT="$(payload "$r" | python -c "import sys,json;print(len(json.load(sys.stdin)))")"
[ "$COUNT" = "0" ] && ok "retired announcement leaves the banner" || bad "retired announcement still active ($COUNT)"

# --- reports --------------------------------------------------------------
r="$(req GET /api/v1/reports/workload)"
if [ "$(status "$r")" = "200" ]; then
  HAS="$(payload "$r" | python -c "import sys,json;d=json.load(sys.stdin);print('releases' in d and 'effort' in d)")"
  [ "$HAS" = "True" ] && ok "workload report carries releases + effort" || bad "workload report missing the new snapshots"
else
  bad "workload report ($(status "$r"))"
fi

# --- cleanup --------------------------------------------------------------
req DELETE "/api/v1/oncall/policies/work-item.escalation" >/dev/null
req DELETE "/api/v1/oncall/schedules/smoke-rota" >/dev/null
req DELETE "/api/v1/announcements/$ANN_ID" >/dev/null

echo "------------------------------------------------"
if [ "$failed" -eq 0 ]; then
  echo "MODULE SMOKE PASSED  ($passed checks)"
else
  echo "MODULE SMOKE FAILED  ($failed failed, $passed passed)"
  exit 1
fi
