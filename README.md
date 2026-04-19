## Ticket Booking System (Backend API)

Spring Boot + MySQL backend implementing a **hold-then-confirm** booking flow with **concurrency-safe capacity enforcement**.

**Architecture (layers, flows, concurrency):** see [ARCHITECTURE.md](ARCHITECTURE.md).

### Key behaviors
- **Hold seats** for 5 minutes (returns `holdId`)
- **Confirm booking** using `holdId`
- **Expired holds** auto-marked as `EXPIRED` by a scheduled job
- **Availability** = total - confirmed bookings - active holds
- **Soft delete events** (deleted events behave like "not found")
- **Soft cancel bookings** (`status=CANCELED`)
- **High contention**: uses DB row lock on the event for hold/confirm, plus short-lived availability cache for read load

### Prerequisites
- Java 21
- MySQL running locally

Default DB config is in `src/main/resources/application.yml`:
- DB: `ticket_booking`
- User: `root`
- Pass: `root`

### Run
```bash
mvn spring-boot:run
```

Hibernate (`spring.jpa.hibernate.ddl-auto: update`) creates or updates tables from JPA entities on startup. For production, consider explicit schema management and setting `ddl-auto` to `validate` or `none`.

### Authentication
All APIs are currently **open** (no login/token required). For user-specific rules, requests include `userId`.

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
