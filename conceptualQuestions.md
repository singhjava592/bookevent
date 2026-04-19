# Conceptual questions — Q&A sheet

Questions and answers below match **this codebase as implemented** (see [ARCHITECTURE.md](ARCHITECTURE.md) for structure).

---

**Q:** Why two steps—hold and confirm—instead of one booking API?

**A:** Hold reserves seats for a short TTL (`app.holds.ttl-minutes`, default 5) while checkout runs. Confirm creates the `Booking` and marks the hold `CONFIRMED`. Expired holds are moved to `EXPIRED` by `HoldExpiryJob` so capacity is freed.

---

**Q:** How is “available seats” calculated?

**A:** `totalSeats` on the event minus `SeatHoldRepository.sumActiveHeldSeats` (status `ACTIVE`, `expiresAt > now`) minus `BookingRepository.sumConfirmedBookedSeats` (confirmed, not canceled). Soft-deleted events are not returned for booking. Canceled bookings do not count toward booked seats.

---

**Q:** Many users see one seat left and call hold at the same time. How do you avoid overbooking?

**A:** Hold does not use cached availability. It calls `EventRepository.findActiveByIdForUpdate` (pessimistic write / `SELECT … FOR UPDATE` on the event), then recomputes held and booked from the DB. If `quantity` exceeds what is left, it throws `ConflictException` (409). Concurrent holds on the same event wait on that row lock.

---

**Q:** Why lock the event row instead of per-seat rows?

**A:** The domain stores a seat **count** per event, not individual seat IDs. Locking the event row serializes capacity checks and inserts for that event.

---

**Q:** What is the downside of locking the event?

**A:** All holds and confirms for one event contend on one DB row lock, so throughput for that event is limited and latency can spike under heavy parallel traffic.

---

**Q:** Cached availability—can it cause overbooking?

**A:** No. `AvailabilityService` caches `GET …/availability`. `BookingService` does not read that cache for hold or confirm. After hold, confirm, or cancel, `evictAvailability(eventId)` clears the cache entry for that event.

---

**Q:** Does confirm check “free seats” again like hold?

**A:** No. Confirm loads the hold, locks the same event with `findActiveByIdForUpdate`, then checks user, hold `ACTIVE`, `expiresAt` after `now`, no existing confirmed booking for that user and event, then saves `Booking` and marks the hold `CONFIRMED`. If the hold was already `CONFIRMED`, it returns the existing `confirmedBookingId` without creating another booking.

---

**Q:** User confirms when the hold has expired or the expiry job has run. What happens?

**A:** If `expiresAt` is not after `now`, the service sets the hold to `EXPIRED`, saves it, and throws `ConflictException` (“Hold has expired”). If status is not `ACTIVE` (e.g. `EXPIRED`), it throws (“Hold is not active”). Response is 409, not a new confirmed booking.

---

**Q:** Can the scheduled expiry job and confirm run at the same time?

**A:** The job runs `expireHolds(now)` (`ACTIVE` and `expiresAt < now` → `EXPIRED`). Confirm runs in its own transaction with an event lock and the checks above. Whatever order commits, the hold row ends either confirmed or not; confirm only creates a booking when the hold is still active and not expired per the checks in `confirmHold`.

---

**Q:** Does this work with multiple application instances?

**A:** Yes. The lock is taken in MySQL on the event row, not in process memory, so every instance shares the same serialization.

---

**Q:** What transaction isolation level does the app use?

**A:** The project does not set `spring.jpa` isolation or `@Transactional(isolation = …)`, so Spring uses `Isolation.DEFAULT` (driver default). With stock MySQL InnoDB that is typically **REPEATABLE READ**; it is not configured explicitly in this repo.

---

**Q:** What serializes the “last seat” race on hold?

**A:** The pessimistic lock on the event row. The second transaction blocks until the first releases the lock, then runs its own sum and either inserts a hold or throws.

---

**Q:** Why is `userId` sent in the JSON body?

**A:** `SecurityConfig` uses `permitAll()` on all routes; there is no enforced login on these endpoints. Business rules (hold owner, cancel owner, duplicate booking per user/event) use the `userId` from the request body.

---

**Q:** How do you test last-seat contention?

**A:** `scripts/contention-last-seat-race.sh` creates an event, fills to one remaining seat, runs parallel workers (availability → hold → confirm). Expect a single successful full path; others typically get 409 on hold.

---

**Q:** How is the database schema managed?

**A:** `spring.jpa.hibernate.ddl-auto: update` in `application.yml` so Hibernate aligns tables with entities on startup.

---

**Q:** Which HTTP status codes for errors?

**A:** `NotFoundException` → 404. `ConflictException` → 409 (not enough seats, wrong user, expired/inactive hold, duplicate confirmed booking for same user+event, quantity rules). Validation failures → 400 (via global exception handling).

---

**Q:** Why soft-delete events and soft-cancel bookings?

**A:** Events use a deleted/inactive status so rows stay for audit; booking flows treat them as missing. Bookings are soft-canceled so history remains but confirmed capacity drops.

---

**Q:** One sentence on concurrency.

**A:** Event row pessimistic lock on hold and confirm, capacity from DB sums inside that transaction, availability only from cache on GET and evicted on writes.

---

**Q:** Moderate traffic (e.g. regional events, hundreds of concurrent users, short peaks). How would you handle booking safely?

**A:** Keep **MySQL as the authority**: pessimistic **`FOR UPDATE` on the event** (as in this service), recompute held + booked inside that transaction, keep transactions **short** (no slow remote calls while the lock is held). Use **cached availability** only for reads, with **eviction on writes**. Add **indexes** on hold/booking queries used in the sums, size the **connection pool** for expected concurrency, and return **409** when sold out so clients do not retry in a tight loop. That pattern is appropriate for many production backends that are not single-show global flash sales.

---

**Q:** Very high traffic (e.g. BookMyShow-style spike: one show, huge simultaneous demand). How would you handle it?

**A:** One **row lock per event** caps writes at roughly “one in-flight booking transaction per event at a time,” so it is **not enough alone** for extreme peaks; correctness stays fine, **latency and pool usage** become the problem. Common approach: **shape traffic first**—waiting room, **queue token**, or **rate limit** so the inventory tier sees a bounded arrival rate. Then either **widen the data path** (finer locks: per section/seat row, sharded counters) or a **dedicated hot inventory layer** (e.g. Redis with atomic scripts) **only** with a clear path to **reconcile to MySQL** and proofs against double-sell. **Reads** scale with cache/CDN/replicas; **writes** stay on a controlled primary path. *This repo implements the DB-locked core only, not waiting rooms or Redis.*
