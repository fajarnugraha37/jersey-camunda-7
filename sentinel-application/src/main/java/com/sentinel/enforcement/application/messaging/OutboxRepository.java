package com.sentinel.enforcement.application.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {

  void enqueue(OutboxEvent outboxEvent);

  List<OutboxEvent> claimPending(
      String leaseOwner, Instant now, Duration leaseDuration, int batchSize, String updatedBy);

  void markPublished(UUID eventId, Instant publishedAt, String updatedBy);

  void releaseForRetry(
      UUID eventId, Instant now, Instant nextAttemptAt, String lastError, String updatedBy);

  long countPending();
}
