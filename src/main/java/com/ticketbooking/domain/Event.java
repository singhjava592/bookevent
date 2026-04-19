package com.ticketbooking.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Check;

@Entity
@Table(
    name = "events",
    indexes = {@Index(name = "idx_events_status", columnList = "status")})
@Check(constraints = "total_seats >= 0")
public class Event {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(name = "event_date", nullable = false)
  private LocalDateTime eventDate;

  @Column(nullable = false, length = 255)
  private String location;

  @Column(name = "total_seats", nullable = false)
  private int totalSeats;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EventStatus status;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void prePersist() {
    var now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
    if (status == null) status = EventStatus.ACTIVE;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public LocalDateTime getEventDate() {
    return eventDate;
  }

  public void setEventDate(LocalDateTime eventDate) {
    this.eventDate = eventDate;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public int getTotalSeats() {
    return totalSeats;
  }

  public void setTotalSeats(int totalSeats) {
    this.totalSeats = totalSeats;
  }

  public EventStatus getStatus() {
    return status;
  }

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void softDelete() {
    this.status = EventStatus.DELETED;
    this.deletedAt = LocalDateTime.now();
  }
}

