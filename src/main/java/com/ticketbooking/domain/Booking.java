package com.ticketbooking.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
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
  @Column(nullable = false)
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

