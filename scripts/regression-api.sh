#!/usr/bin/env bash
# Regression smoke test for the ticket-booking API (curl-based).
#
# Prerequisites:
#   - App running: mvn spring-boot:run  (from repo root)
#   - Optional: jq for JSON parsing (brew install jq). Without jq, install jq or set IDs manually.
#
# Usage:
#   chmod +x scripts/regression-api.sh
#   ./scripts/regression-api.sh
#   BASE_URL=http://localhost:8080 ./scripts/regression-api.sh
#
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"

die() {
  echo "ERROR: $*" >&2
  exit 1
}

need_jq() {
  command -v jq >/dev/null 2>&1 || die "jq is required for this script (brew install jq)"
}

http_get() {
  local url="$1"
  local expect="${2:-200}"
  local code
  code=$(curl -sS -o /tmp/reg-api.body -w "%{http_code}" "$url")
  if [[ "$code" != "$expect" ]]; then
    cat /tmp/reg-api.body >&2
    die "GET $url expected HTTP $expect, got $code"
  fi
  cat /tmp/reg-api.body
}

http_post_json() {
  local url="$1"
  local json="$2"
  local expect="${3:-200}"
  local code
  code=$(curl -sS -o /tmp/reg-api.body -w "%{http_code}" \
    -X POST "$url" \
    -H 'Content-Type: application/json' \
    -d "$json")
  if [[ "$code" != "$expect" ]]; then
    cat /tmp/reg-api.body >&2
    die "POST $url expected HTTP $expect, got $code"
  fi
  cat /tmp/reg-api.body
}

http_put_json() {
  local url="$1"
  local json="$2"
  local expect="${3:-200}"
  local code
  code=$(curl -sS -o /tmp/reg-api.body -w "%{http_code}" \
    -X PUT "$url" \
    -H 'Content-Type: application/json' \
    -d "$json")
  if [[ "$code" != "$expect" ]]; then
    cat /tmp/reg-api.body >&2
    die "PUT $url expected HTTP $expect, got $code"
  fi
  cat /tmp/reg-api.body
}

http_delete() {
  local url="$1"
  local expect="${2:-204}"
  local code
  code=$(curl -sS -o /tmp/reg-api.body -w "%{http_code}" -X DELETE "$url")
  if [[ "$code" != "$expect" ]]; then
    cat /tmp/reg-api.body >&2
    die "DELETE $url expected HTTP $expect, got $code"
  fi
  [[ ! -s /tmp/reg-api.body ]] || cat /tmp/reg-api.body
}

echo "== Health: reachability (GET /events on empty DB may 404 — we create an event first) =="
need_jq

CREATE_BODY=$(cat <<'JSON'
{"name":"Regression Event","eventDate":"2026-06-15T19:00:00","location":"Test Venue","totalSeats":50}
JSON
)

echo "== POST /events (create) =="
CREATE_RESP=$(http_post_json "$BASE_URL/events" "$CREATE_BODY" 201)
echo "$CREATE_RESP" | jq .
EVENT_ID=$(echo "$CREATE_RESP" | jq -r '.id')

echo "== GET /events/$EVENT_ID =="
http_get "$BASE_URL/events/$EVENT_ID" | jq .

echo "== GET /events/$EVENT_ID/availability =="
http_get "$BASE_URL/events/$EVENT_ID/availability" | jq .

echo "== POST /events/$EVENT_ID/holds =="
HOLD_RESP=$(http_post_json "$BASE_URL/events/$EVENT_ID/holds" \
  '{"userId":"regression-user","quantity":3}' 201)
echo "$HOLD_RESP" | jq .
HOLD_ID=$(echo "$HOLD_RESP" | jq -r '.holdId')

echo "== GET /events/$EVENT_ID/availability (after hold) =="
http_get "$BASE_URL/events/$EVENT_ID/availability" | jq .

echo "== POST /holds/$HOLD_ID/confirm =="
CONFIRM_RESP=$(http_post_json "$BASE_URL/holds/$HOLD_ID/confirm" \
  '{"userId":"regression-user"}' 201)
echo "$CONFIRM_RESP" | jq .
BOOKING_ID=$(echo "$CONFIRM_RESP" | jq -r '.bookingId')

echo "== GET /bookings/$BOOKING_ID =="
http_get "$BASE_URL/bookings/$BOOKING_ID" | jq .

echo "== GET /events/$EVENT_ID/availability (after confirm) =="
http_get "$BASE_URL/events/$EVENT_ID/availability" | jq .

echo "== POST /bookings/$BOOKING_ID/cancel =="
http_post_json "$BASE_URL/bookings/$BOOKING_ID/cancel" \
  '{"userId":"regression-user"}' 200 | jq .

echo "== GET /bookings/$BOOKING_ID (expect CANCELED) =="
http_get "$BASE_URL/bookings/$BOOKING_ID" | jq .

echo "== GET /events/$EVENT_ID/availability (after cancel) =="
http_get "$BASE_URL/events/$EVENT_ID/availability" | jq .

echo "== PUT /events/$EVENT_ID =="
UPDATE_BODY=$(cat <<JSON
{"name":"Regression Event Updated","eventDate":"2026-06-16T19:00:00","location":"Test Venue 2","totalSeats":50}
JSON
)
http_put_json "$BASE_URL/events/$EVENT_ID" "$UPDATE_BODY" | jq .

echo "== DELETE /events/$EVENT_ID (soft delete) =="
http_delete "$BASE_URL/events/$EVENT_ID" 204

echo "== GET /events/$EVENT_ID (expect 404) =="
code=$(curl -sS -o /tmp/reg-api.body -w "%{http_code}" "$BASE_URL/events/$EVENT_ID")
if [[ "$code" != "404" ]]; then
  cat /tmp/reg-api.body >&2
  die "GET deleted event expected HTTP 404, got $code"
fi
echo "(404 as expected)"

echo ""
echo "All regression curls passed."
