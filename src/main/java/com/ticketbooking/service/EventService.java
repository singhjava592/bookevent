package com.ticketbooking.service;

import com.ticketbooking.domain.Event;
import com.ticketbooking.domain.EventStatus;
import com.ticketbooking.repo.EventRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
  private final EventRepository eventRepo;

  public EventService(EventRepository eventRepo) {
    this.eventRepo = eventRepo;
  }

  @Transactional
  public Event create(Event e) {
    return eventRepo.save(e);
  }

  public Event getActive(long id) {
    return eventRepo
        .findByIdAndStatus(id, EventStatus.ACTIVE)
        .orElseThrow(() -> new NotFoundException("Event not found"));
  }

  @Transactional
  public Event update(long id, Event patch) {
    var e = getActive(id);
    e.setName(patch.getName());
    e.setEventDate(patch.getEventDate());
    e.setLocation(patch.getLocation());
    e.setTotalSeats(patch.getTotalSeats());
    return eventRepo.save(e);
  }

  @Transactional
  public void softDelete(long id) {
    var e = getActive(id);
    e.softDelete();
    eventRepo.save(e);
  }
}

