#!/usr/bin/env bash
# Post-deploy smoke test. Requires curl, jq, and a running stack with demo seeding enabled.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
EMAIL="${NEPSE_EMAIL:-demo@nepse.local}"
PASSWORD="${NEPSE_PASSWORD:-demo1234}"
ROOT="${BASE_URL%/api}"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }
pass() { echo "ok - $*"; }

health="$(curl -fsS "$ROOT/actuator/health")"
[ "$(jq -r .status <<<"$health")" = "UP" ] || fail "health endpoint is not UP"
pass "health is UP"

login="$(curl -fsS -X POST "$BASE_URL/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"
token="$(jq -r .accessToken <<<"$login")"
[ -n "$token" ] && [ "$token" != "null" ] || fail "login did not return an access token"
pass "demo login"

check() {
  local name="$1" path="$2"
  local code
  code="$(curl -sS -o "$TMP" -w '%{http_code}' "$BASE_URL$path" \
    -H "Authorization: Bearer $token")"
  [ "$code" = "200" ] || fail "$name returned HTTP $code"
  jq -e . "$TMP" >/dev/null || fail "$name did not return JSON"
  pass "$name"
}

check "portfolio" "/portfolio"
check "trades" "/portfolio/trades"
check "portfolio history" "/portfolio/history?days=30"
check "recommendations" "/analysis/recommendations"
check "averaging opportunities" "/analysis/opportunities"
check "stock detail" "/market/stocks/NICA?range=1M"
check "alerts" "/alerts"
check "alert events" "/alerts/events"
check "settings" "/settings"
check "realized gains" "/reports/realized-gains"

holdings="$(curl -fsS "$BASE_URL/portfolio" -H "Authorization: Bearer $token" | jq -r .summary.holdingsCount)"
if [ "$holdings" -lt 1 ]; then
  fail "demo portfolio has no holdings; was seeding disabled?"
fi
pass "demo portfolio has $holdings holdings"

echo "All smoke checks passed."
