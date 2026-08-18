#!/usr/bin/env bash
#
# Phase E — runtime verification of the tenant-isolation and realtime work.
#
# The unit tests cover the authorization LOGIC, but three things can only fail at
# runtime and are exactly the things most likely to be wrong:
#
#   1. SpEL inside @PreAuthorize is a string. The compiler never checks it, so a
#      typo in `authentication.principal.merchantId` becomes a 403 on every call
#      (or, worse, a silently permissive expression) only when a request arrives.
#   2. The ROLE_ prefix contract between UserPrincipal and hasAnyRole().
#   3. The live STOMP path: handshake -> destination authz -> Kafka -> Redis -> topic.
#
# Usage:
#   export JWT_SECRET=... QR_SIGNATURE_SECRET=...      # required; services fail fast
#   ./scripts/smoke-tenant-isolation.sh [GATEWAY_URL]
#
# Requires: curl, jq. Optional: websocat (for the STOMP checks).
set -uo pipefail

GATEWAY="${1:-http://localhost:8081}"
PASS=0
FAIL=0

c_pass() { printf '  \033[32mPASS\033[0m  %s\n' "$1"; PASS=$((PASS+1)); }
c_fail() { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; FAIL=$((FAIL+1)); }
head()   { printf '\n\033[1m%s\033[0m\n' "$1"; }

need() { command -v "$1" >/dev/null 2>&1 || { echo "missing dependency: $1"; exit 2; }; }
need curl
need jq

: "${JWT_SECRET:?JWT_SECRET must be exported — services refuse to start without it}"
: "${QR_SIGNATURE_SECRET:?QR_SIGNATURE_SECRET must be exported}"

# HTTP status for a request. Args: METHOD URL [TOKEN] [BODY]
status() {
  local method="$1" url="$2" token="${3:-}" body="${4:-}"
  local args=(-s -o /dev/null -w '%{http_code}' -X "$method" "$url")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
  curl "${args[@]}"
}

login() { # email password -> accessToken
  curl -s -X POST "$GATEWAY/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$2\"}" | jq -r '.accessToken // empty'
}

head "0. Preconditions"
if [ "$(status GET "$GATEWAY/actuator/health")" = "200" ]; then
  c_pass "gateway is up at $GATEWAY"
else
  c_fail "gateway not reachable at $GATEWAY — start the stack first (docker compose up -d)"
  exit 1
fi

# ---------------------------------------------------------------------------
# Fill these in for your environment. Two merchants with separate owners are
# required; the whole point is proving A cannot see B.
# ---------------------------------------------------------------------------
OWNER_A_EMAIL="${OWNER_A_EMAIL:-owner-a@example.com}"
OWNER_A_PASS="${OWNER_A_PASS:-}"
OWNER_B_EMAIL="${OWNER_B_EMAIL:-owner-b@example.com}"
OWNER_B_PASS="${OWNER_B_PASS:-}"
MERCHANT_B_ID="${MERCHANT_B_ID:-}"

if [ -z "$OWNER_A_PASS" ] || [ -z "$OWNER_B_PASS" ] || [ -z "$MERCHANT_B_ID" ]; then
  echo
  echo "Set OWNER_A_PASS, OWNER_B_PASS and MERCHANT_B_ID to run the tenant checks."
  echo "Create two merchants with separate MERCHANT_OWNER users first."
  exit 2
fi

TOKEN_A="$(login "$OWNER_A_EMAIL" "$OWNER_A_PASS")"
TOKEN_B="$(login "$OWNER_B_EMAIL" "$OWNER_B_PASS")"
[ -n "$TOKEN_A" ] && c_pass "owner A logged in" || { c_fail "owner A login failed"; exit 1; }
[ -n "$TOKEN_B" ] && c_pass "owner B logged in" || { c_fail "owner B login failed"; exit 1; }

head "1. Token hygiene"
# A token now carries a type claim; decode the payload and check it.
payload() { echo "$1" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null; }
[ "$(payload "$TOKEN_A" | jq -r '.type')" = "ACCESS" ] \
  && c_pass "access token carries type=ACCESS" \
  || c_fail "access token missing type claim"

REFRESH_A="$(curl -s -X POST "$GATEWAY/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$OWNER_A_EMAIL\",\"password\":\"$OWNER_A_PASS\"}" | jq -r '.refreshToken')"
# An access token must NOT be accepted as a refresh token.
code="$(status POST "$GATEWAY/api/auth/refresh" "" "{\"refreshToken\":\"$TOKEN_A\"}")"
[ "$code" = "401" ] && c_pass "access token rejected as refresh token" \
  || c_fail "access token accepted at /refresh (got $code, want 401)"
code="$(status POST "$GATEWAY/api/auth/refresh" "" "{\"refreshToken\":\"$REFRESH_A\"}")"
[ "$code" = "200" ] && c_pass "genuine refresh token accepted" \
  || c_fail "refresh token rejected (got $code, want 200)"

head "2. @PreAuthorize wiring (catches SpEL typos and the ROLE_ prefix contract)"
# If the expressions or the prefix are wrong, EVERYTHING 403s — including calls
# that should succeed. This check is what distinguishes "secure" from "broken".
code="$(status GET "$GATEWAY/api/branches/merchant/$(payload "$TOKEN_A" | jq -r '.merchantId')" "$TOKEN_A")"
[ "$code" = "200" ] && c_pass "owner A can read their own branches ($code)" \
  || c_fail "owner A blocked from own branches (got $code) — likely a SpEL or ROLE_ prefix fault"

head "3. Tenant isolation"
code="$(status GET "$GATEWAY/api/branches/merchant/$MERCHANT_B_ID" "$TOKEN_A")"
[ "$code" = "403" ] && c_pass "owner A denied merchant B's branches ($code)" \
  || c_fail "owner A reached merchant B's branches (got $code, want 403)"

code="$(status GET "$GATEWAY/api/auth/users?merchantId=$MERCHANT_B_ID" "$TOKEN_A")"
if [ "$code" = "200" ]; then
  # Permitted, but the param must have been ignored — verify no B users leaked.
  leaked="$(curl -s -H "Authorization: Bearer $TOKEN_A" \
    "$GATEWAY/api/auth/users?merchantId=$MERCHANT_B_ID" \
    | jq --arg b "$MERCHANT_B_ID" '[.[] | select(.merchantId == $b)] | length')"
  [ "$leaked" = "0" ] && c_pass "listUsers ignored the merchantId param (no B users returned)" \
    || c_fail "listUsers leaked $leaked users from merchant B"
else
  c_pass "listUsers denied the cross-tenant param ($code)"
fi

head "4. Privilege escalation"
code="$(status POST "$GATEWAY/api/auth/users" "$TOKEN_A" \
  '{"name":"esc","email":"esc-probe@example.com","password":"Passw0rd!123","role":"SUPER_ADMIN"}')"
[ "$code" = "401" ] || [ "$code" = "403" ] \
  && c_pass "owner cannot create a SUPER_ADMIN ($code)" \
  || c_fail "owner created a SUPER_ADMIN (got $code) — escalation still open"

head "5. Analytics tenant scoping"
rev_a="$(curl -s -H "Authorization: Bearer $TOKEN_A" "$GATEWAY/api/analytics/today" | jq -r '.todayRevenue // "null"')"
rev_a_as_b="$(curl -s -H "Authorization: Bearer $TOKEN_A" "$GATEWAY/api/analytics/today?merchantId=$MERCHANT_B_ID" | jq -r '.todayRevenue // "null"')"
[ "$rev_a" = "$rev_a_as_b" ] \
  && c_pass "analytics ignored the merchantId override (both $rev_a)" \
  || c_fail "analytics honoured a cross-tenant merchantId ($rev_a vs $rev_a_as_b)"

# Tables must no longer 404 through the phantom URL, and must be scoped.
code="$(status GET "$GATEWAY/api/tables/all" "$TOKEN_A")"
[ "$code" = "200" ] && c_pass "/api/tables/all reachable ($code) — phantom URL fixed" \
  || c_fail "/api/tables/all returned $code"

head "6. Public surface is still public"
for path in "/api/auth/login" "/actuator/health"; do
  code="$(status GET "$GATEWAY$path")"
  [ "$code" != "401" ] && [ "$code" != "403" ] && c_pass "$path not gated ($code)" \
    || c_fail "$path is gated ($code)"
done
# ...and the things that were accidentally public are not.
code="$(status GET "$GATEWAY/api/tables/all")"
[ "$code" = "401" ] || [ "$code" = "403" ] \
  && c_pass "GET /api/tables/all requires auth ($code)" \
  || c_fail "GET /api/tables/all is anonymous (got $code) — the wildcard hole is back"
code="$(status GET "$GATEWAY/actuator/env")"
[ "$code" != "200" ] && c_pass "/actuator/env not exposed ($code)" \
  || c_fail "/actuator/env is public — configuration leak"

head "7. STOMP destination authorization"
if command -v websocat >/dev/null 2>&1; then
  MERCHANT_A_ID="$(payload "$TOKEN_A" | jq -r '.merchantId')"
  try_subscribe() { # token destination -> prints ERROR if the broker rejected
    printf 'CONNECT\naccept-version:1.2\nhost:localhost\n\n\0SUBSCRIBE\nid:sub-0\ndestination:%s\n\n\0' "$2" \
      | timeout 8 websocat -n1 --protocol v12.stomp "${GATEWAY/http/ws}/ws/websocket?token=$1" 2>&1 || true
  }
  out="$(try_subscribe "$TOKEN_A" "/topic/merchant/$MERCHANT_A_ID/branch/1/kitchen")"
  echo "$out" | grep -q 'ERROR' \
    && c_fail "owner A rejected from their OWN kitchen topic" \
    || c_pass "owner A may subscribe to their own kitchen topic"

  out="$(try_subscribe "$TOKEN_A" "/topic/merchant/$MERCHANT_B_ID/branch/1/kitchen")"
  echo "$out" | grep -q 'ERROR' \
    && c_pass "owner A denied merchant B's kitchen topic" \
    || c_fail "owner A SUBSCRIBED to merchant B's kitchen topic — cross-tenant leak"
else
  echo "  SKIP  websocat not installed; install it to exercise the STOMP checks"
  echo "        (the same logic is covered by StompAuthInterceptorTest, 12 tests)"
fi

head "Summary"
printf '  %d passed, %d failed\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
