package com.ticketbooking.api;

import com.ticketbooking.api.dto.EventDtos.CreateEventRequest;
import com.ticketbooking.api.dto.EventDtos.EventResponse;
import com.ticketbooking.api.dto.EventDtos.UpdateEventRequest;
import com.ticketbooking.domain.Event;
import com.ticketbooking.service.AvailabilityService;
import com.ticketbooking.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/events")
public class EventController {
  /**
   * Flow/design: Events are soft-deletable; DELETED events behave like 404 for reads and booking actions.
   * Admin manages events; users can read event and availability with current held+booked seats deducted.
   */
  private final EventService eventService;
  private final AvailabilityService availabilityService;

  public EventController(EventService eventService, AvailabilityService availabilityService) {
    this.eventService = eventService;
    this.availabilityService = availabilityService;
  }

  // POST /events: Create an event (name/date/location/totalSeats).
  // ADMIN only.
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public EventResponse create(@Valid @RequestBody CreateEventRequest req) {
    var e = new Event();
    e.setName(req.name());
    e.setEventDate(req.eventDate());
    e.setLocation(req.location());
    e.setTotalSeats(req.totalSeats());
    var saved = eventService.create(e);
    return new EventResponse(
        saved.getId(), saved.getName(), saved.getEventDate(), saved.getLocation(), saved.getTotalSeats());
  }

  // GET /events/{id}: Fetch an active event.
  // Returns 404 for soft-deleted events.
  @GetMapping("/{id}")
  public EventResponse get(@PathVariable long id) {
    var e = eventService.getActive(id);
    return new EventResponse(e.getId(), e.getName(), e.getEventDate(), e.getLocation(), e.getTotalSeats());
  }

  // PUT /events/{id}: Update event details.
  // ADMIN only; 404 if event is soft-deleted.
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public EventResponse update(@PathVariable long id, @Valid @RequestBody UpdateEventRequest req) {
    var patch = new Event();
    patch.setName(req.name());
    patch.setEventDate(req.eventDate());
    patch.setLocation(req.location());
    patch.setTotalSeats(req.totalSeats());
    var saved = eventService.update(id, patch);
    return new EventResponse(
        saved.getId(), saved.getName(), saved.getEventDate(), saved.getLocation(), saved.getTotalSeats());
  }

  // DELETE /events/{id}: Soft-delete the event (kept for audit).
  // ADMIN only; after delete, event behaves like 404.
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable long id) {
    eventService.softDelete(id);
  }

  // GET /events/{id}/availability: Event inventory snapshot (booked + active holds).
  // Response is cached briefly to handle read spikes; writes evict.
  @GetMapping("/{id}/availability")
  public AvailabilityService.Availability availability(@PathVariable long id) {
    return availabilityService.getAvailability(id);
  }
}

