package com.ticketbooking.service;

import com.ticketbooking.repo.SeatHoldRepository;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HoldExpiryJob {
  private final SeatHoldRepository holdRepo;

  public HoldExpiryJob(SeatHoldRepository holdRepo) {
    this.holdRepo = holdRepo;
  }

  @Scheduled(fixedDelayString = "PT30S")
  @Transactional
  public void expireHolds() {
    holdRepo.expireHolds(LocalDateTime.now());
  }
}

