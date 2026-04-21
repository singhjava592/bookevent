
### Example API calls
Create event (ADMIN):
```bash
curl -X POST http://localhost:8080/events \
  -H 'Content-Type: application/json' \
  -d '{"name":"Concert","eventDate":"2026-05-01T18:00:00","location":"Bangalore","totalSeats":100}'
```

Check availability:
```bash
curl http://localhost:8080/events/1/availability
```

Hold seats (USER):
```bash
curl -X POST http://localhost:8080/events/1/holds \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user1","quantity":2}'
```

Confirm booking using returned `holdId`:
```bash
curl -X POST http://localhost:8080/holds/<holdId>/confirm \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user1"}'
```

View booking:
```bash
curl http://localhost:8080/bookings/<bookingId>
```

Cancel booking (soft cancel; returns JSON with a message):
```bash
curl -X POST http://localhost:8080/bookings/<bookingId>/cancel \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user1"}'
```
Example response: `{"bookingId":2,"message":"Booking 2 has been cancelled successfully."}`

Soft delete event (ADMIN):
```bash
curl -X DELETE http://localhost:8080/events/1
```

### Contention test (last seat)
Script `scripts/contention-last-seat-race.sh` creates an event with 100 seats, confirms 99 single-seat bookings, then starts many parallel workers that each check `GET /events/{id}/availability`, attempt `POST /events/{id}/holds`, and `POST /holds/{holdId}/confirm` if the hold succeeds. Only one worker should win; others typically get **409** on hold (`Not enough seats available`).

```bash
chmod +x scripts/contention-last-seat-race.sh
./scripts/contention-last-seat-race.sh full 40          # setup + race (40 workers)
./scripts/contention-last-seat-race.sh race 2 50         # race on existing event id 2
```

Requires `jq` and `curl`. Optional: `FORCE_RACE=1` if `availableSeats` is not exactly 1 before the race.
