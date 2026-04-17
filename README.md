## Ticket Booking System (Backend API)

Spring Boot + MySQL backend implementing a **hold-then-confirm** booking flow with **concurrency-safe capacity enforcement**.

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

Flyway will create tables automatically on startup.

### Authentication (for now)
To keep the focus on booking correctness, this project uses HTTP Basic with in-memory users:
- **ADMIN**: `admin/admin`
- **USER**: `user1/user1`

### Example API calls
Create event (ADMIN):
```bash
curl -u admin:admin -X POST http://localhost:8080/events \
  -H 'Content-Type: application/json' \
  -d '{"name":"Concert","eventDate":"2026-05-01T18:00:00","location":"Bangalore","totalSeats":100}'
```

Check availability:
```bash
curl -u user1:user1 http://localhost:8080/events/1/availability
```

Hold seats (USER):
```bash
curl -u user1:user1 -X POST http://localhost:8080/events/1/holds \
  -H 'Content-Type: application/json' \
  -d '{"quantity":2}'
```

Confirm booking using returned `holdId`:
```bash
curl -u user1:user1 -X POST http://localhost:8080/holds/<holdId>/confirm
```

View booking:
```bash
curl -u user1:user1 http://localhost:8080/bookings/<bookingId>
```

Cancel booking (soft cancel):
```bash
curl -u user1:user1 -X POST http://localhost:8080/bookings/<bookingId>/cancel
```

Soft delete event (ADMIN):
```bash
curl -u admin:admin -X DELETE http://localhost:8080/events/1
```

