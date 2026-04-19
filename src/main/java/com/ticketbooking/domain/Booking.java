package com.ticketbooking.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Check;

@Entity
@Table(
    name = "bookings",
    indexes = {
      @Index(name = "idx_bookings_event_status", columnList = "event_id,status"),
      @Index(name = "idx_bookings_user_event_status", columnList = "user_id,event_id,status")
    })
@Check(constraints = "quantity > 0")
public class Booking {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_id", nullable = false)
  private Event event;

  @Column(name = "user_id", nullable = false, length = 128)
  private String userId;

  @Column(nullable = false)
  private int quantity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private BookingStatus status;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "canceled_at")
  private LocalDateTime canceledAt;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
    if (status == null) status = BookingStatus.CONFIRMED;
  }

  public Long getId() {
    return id;
  }

  public Event getEvent() {
    return event;
  }

  public void setEvent(Event event) {
    this.event = event;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public int getQuantity() {
    return quantity;
  }

  public void setQuantity(int quantity) {
    this.quantity = quantity;
  }

  public BookingStatus getStatus() {
    return status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getCanceledAt() {
    return canceledAt;
  }

  public void cancel() {
    if (status == BookingStatus.CANCELED) return;
    status = BookingStatus.CANCELED;
    canceledAt = LocalDateTime.now();
  }
}

