#!/usr/bin/env bash
#
# Contention test: many clients race for the last seat(s) on an event.
#
# Flow per racer (unique userId each time):
#   1) GET /events/{id}/availability
#   2) If availableSeats >= QUANTITY → POST /events/{id}/holds
#   3) If hold 201 → POST /holds/{holdId}/confirm
#
# With row locking in BookingService, only one client should complete the full
# hold+confirm path when exactly one seat remains and everyone requests qty 1.
#
# Prerequisites: jq, curl; API running (mvn spring-boot:run).
#
# Usage:
#   # Create a new event, book 99/100 seats, then race 40 workers for the last seat:
#   ./scripts/contention-last-seat-race.sh full
#
#   # Only prepare data (prints EXPORT_EVENT_ID=...):
#   ./scripts/contention-last-seat-race.sh setup
#
#   # Race on existing event (e.g. 2) with 50 parallel workers:
#   ./scripts/contention-last-seat-race.sh race 2 50
#
# Env overrides:
#   BASE_URL=http://localhost:8080
#   TOTAL_SEATS=100   (seats to fill before race = TOTAL_SEATS - 1)
#   QUANTITY=1        (requested quantity per hold)
#
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"
TOTAL_SEATS="${TOTAL_SEATS:-100}"
SEATS_TO_BOOK_BEFORE_RACE=$((TOTAL_SEATS - 1))
QUANTITY="${QUANTITY:-1}"

die() { echo "ERROR: $*" >&2; exit 1; }

need_jq() {
  command -v jq >/dev/null 2>&1 || die "install jq (e.g. brew install jq)"
}

curl_json_post() {
  local url="$1" body="$2"
  curl -sS -X POST "$url" -H 'Content-Type: application/json' -d "$body"
}

get_availability() {
  curl -sS "$BASE_URL/events/$1/availability"
}

setup_event_and_fill() {
  need_jq
  local create_resp event_id name i user json hold_out hold_code hold_body hold_id conf_out conf_code

  name="Contention test $(date +%s)"
  create_resp=$(curl_json_post "$BASE_URL/events" "$(jq -n \
    --arg name "$name" \
    --argjson ts "$(date +%s)" \
    --arg loc "Lab" \
    --argjson seats "$TOTAL_SEATS" \
    '{name: $name, eventDate: ("2030-06-01T18:00:00"), location: $loc, totalSeats: $seats}')")

  event_id=$(echo "$create_resp" | jq -e -r '.id') || die "create event failed: $create_resp"

  echo "Created event id=$event_id ($name), booking $SEATS_TO_BOOK_BEFORE_RACE seats..."

  for i in $(seq 0 $((SEATS_TO_BOOK_BEFORE_RACE - 1))); do
    user="setup-user-$i"
    json=$(jq -n --arg u "$user" --argjson q 1 '{userId: $u, quantity: $q}')
    hold_out=$(curl -sS -w "\n%{http_code}" -X POST "$BASE_URL/events/$event_id/holds" \
      -H 'Content-Type: application/json' -d "$json")
    hold_code=$(echo "$hold_out" | tail -n1)
    hold_body=$(echo "$hold_out" | sed '$d')
    if [[ "$hold_code" != "201" ]]; then
      die "setup hold $i failed HTTP $hold_code: $hold_body"
    fi
    hold_id=$(echo "$hold_body" | jq -r .holdId)
    conf_out=$(curl -sS -w "\n%{http_code}" -X POST "$BASE_URL/holds/$hold_id/confirm" \
      -H 'Content-Type: application/json' -d "$(jq -n --arg u "$user" '{userId: $u}')")
    conf_code=$(echo "$conf_out" | tail -n1)
    conf_body=$(echo "$conf_out" | sed '$d')
    if [[ "$conf_code" != "201" ]]; then
      die "setup confirm $i failed HTTP $conf_code: $conf_body"
    fi
    if (( (i + 1) % 25 == 0 )); then
      echo "  booked $((i + 1)) / $SEATS_TO_BOOK_BEFORE_RACE"
    fi
  done

  local av
  av=$(get_availability "$event_id")
  local avail
  avail=$(echo "$av" | jq -r .availableSeats)
  echo "Availability before race: $av"
  [[ "$avail" == "1" ]] || die "expected 1 available seat, got $avail"

  echo "EXPORT_EVENT_ID=$event_id"
  echo "$event_id"
}

# Worker: wait until BARRIER_EPOCH (seconds), then run one race attempt.
racer_worker() {
  local worker_id="$1"
  local event_id="$2"
  local quantity="$3"
  local barrier="$4"
  local user_id="race-${worker_id}-$$-${RANDOM}${RANDOM}"

  while [[ $(date +%s) -lt "$barrier" ]]; do sleep 0.02; done

  local av_json avail
  av_json=$(curl -sS "$BASE_URL/events/$event_id/availability" 2>/dev/null) || {
    echo "worker $worker_id RESULT=ERROR_AVAILABILITY"
    return
  }
  avail=$(echo "$av_json" | jq -r '.availableSeats // empty' 2>/dev/null) || avail=""
  if [[ -z "$avail" ]]; then
    echo "worker $worker_id RESULT=ERROR_PARSE_AVAIL"
    return
  fi
  if (( avail < quantity )); then
    echo "worker $worker_id RESULT=SKIP_LOW_AVAIL avail=$avail"
    return
  fi

  local hold_payload hold_out hold_code hold_body hold_id conf_out conf_code conf_body booking_id
  hold_payload=$(jq -n --arg u "$user_id" --argjson q "$quantity" '{userId: $u, quantity: $q}')
  hold_out=$(curl -sS -w "\n%{http_code}" -X POST "$BASE_URL/events/$event_id/holds" \
    -H 'Content-Type: application/json' -d "$hold_payload" 2>/dev/null) || {
    echo "worker $worker_id RESULT=ERROR_HOLD_CURL"
    return
  }
  hold_code=$(echo "$hold_out" | tail -n1)
  hold_body=$(echo "$hold_out" | sed '$d')

  if [[ "$hold_code" != "201" ]]; then
    echo "worker $worker_id RESULT=HOLD_FAIL code=$hold_code body=$(echo "$hold_body" | head -c 200)"
    return
  fi

  hold_id=$(echo "$hold_body" | jq -r .holdId)
  conf_out=$(curl -sS -w "\n%{http_code}" -X POST "$BASE_URL/holds/$hold_id/confirm" \
    -H 'Content-Type: application/json' -d "$(jq -n --arg u "$user_id" '{userId: $u}')" 2>/dev/null) || {
    echo "worker $worker_id RESULT=ERROR_CONFIRM_CURL hold=$hold_id"
    return
  }
  conf_code=$(echo "$conf_out" | tail -n1)
  conf_body=$(echo "$conf_out" | sed '$d')

  if [[ "$conf_code" == "201" ]]; then
    booking_id=$(echo "$conf_body" | jq -r .bookingId)
    echo "worker $worker_id RESULT=SUCCESS bookingId=$booking_id holdId=$hold_id user=$user_id"
    return
  fi
  echo "worker $worker_id RESULT=CONFIRM_FAIL code=$conf_code hold=$hold_id body=$conf_body"
}

run_race() {
  need_jq
  local event_id="${1:?event id required}"
  local workers="${2:-40}"

  local av pre_avail
  av=$(get_availability "$event_id")
  pre_avail=$(echo "$av" | jq -r .availableSeats)
  echo "Pre-race availability: availableSeats=$pre_avail (event $event_id, workers=$workers, quantity=$QUANTITY)"
  if [[ "$pre_avail" != "1" ]] && [[ "${FORCE_RACE:-}" != "1" ]]; then
    echo "WARNING: for a clear demo, leave exactly 1 seat free; currently availableSeats=$pre_avail. Set FORCE_RACE=1 to run anyway." >&2
  fi

  local tmp barrier i
  tmp=$(mktemp -d)
  trap 'rm -rf "$tmp"' RETURN

  barrier=$(($(date +%s) + 2))
  echo "Barrier at epoch $barrier (workers sync then burst holds)..."

  for i in $(seq 1 "$workers"); do
    ( racer_worker "$i" "$event_id" "$QUANTITY" "$barrier" >"$tmp/w$i.log" 2>&1 ) &
  done
  wait

  local success_count=0 hold_fail=0 skip=0
  local line f
  for f in "$tmp"/w*.log; do
    [[ -f "$f" ]] || continue
    line=$(cat "$f")
    echo "$line"
    if [[ "$line" == *RESULT=SUCCESS* ]]; then
      success_count=$((success_count + 1))
    elif [[ "$line" == *RESULT=HOLD_FAIL* ]]; then
      hold_fail=$((hold_fail + 1))
    elif [[ "$line" == *RESULT=SKIP_LOW_AVAIL* ]]; then
      skip=$((skip + 1))
    fi
  done

  av=$(get_availability "$event_id")
  local post_avail
  post_avail=$(echo "$av" | jq -r .availableSeats)
  echo "Post-race availability: $av"

  echo "----"
  echo "Summary: SUCCESS=$success_count HOLD_FAIL=$hold_fail SKIP_LOW_AVAIL=$skip (workers=$workers)"
  echo "Expected for 1 remaining seat and quantity=1: SUCCESS=1, post-race availableSeats=0"
  if [[ "$QUANTITY" == "1" ]] && (( success_count == 1 )) && [[ "$post_avail" == "0" ]]; then
    echo "PASS: single winner; last seat is now booked."
  elif [[ "$QUANTITY" == "1" ]] && (( success_count == 1 )); then
    echo "PARTIAL: one winner but post-race availableSeats=$post_avail (expected 0 if one seat was left)."
  elif (( success_count > 1 )); then
    echo "FAIL: more than one confirmation — investigate capacity / locking."
  else
    echo "FAIL or inconclusive: SUCCESS=$success_count — check HOLD_FAIL bodies (often 409 Not enough seats)."
  fi
}

cmd="${1:-}"
case "$cmd" in
  setup)
    setup_event_and_fill
    ;;
  race)
    run_race "${2:?event id}" "${3:-40}"
    ;;
  full)
    eid=$(setup_event_and_fill | tail -n1)
    echo ""
    run_race "$eid" "${2:-40}"
    ;;
  *)
    echo "Usage: $0 setup | race <eventId> [workers] | full [workers]" >&2
    exit 1
    ;;
esac
