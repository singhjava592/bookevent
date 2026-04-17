package com.ticketbooking.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "seat_holds")
public class SeatHold {
  @Id
  @Column(length = 36)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_id", nullable = false)
  private Event event;

  @Column(name = "user_id", nullable = false, length = 128)
  private String userId;

  @Column(nullable = false)
  private int quantity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private HoldStatus status;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "confirmed_booking_id")
  private Long confirmedBookingId;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
    if (id == null) id = UUID.randomUUID().toString();
    if (status == null) status = HoldStatus.ACTIVE;
  }

  public String getId() {
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

  public HoldStatus getStatus() {
    return status;
  }

  public void setStatus(HoldStatus status) {
    this.status = status;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(LocalDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Long getConfirmedBookingId() {
    return confirmedBookingId;
  }

  public void markConfirmed(long bookingId) {
    this.status = HoldStatus.CONFIRMED;
    this.confirmedBookingId = bookingId;
  }
}

