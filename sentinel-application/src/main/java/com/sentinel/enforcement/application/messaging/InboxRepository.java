package com.sentinel.enforcement.application.messaging;

import java.time.Instant;
import java.util.UUID;

public interface InboxRepository {

  boolean beginProcessing(InboxEvent inboxEvent);

  void completeProcessing(
      String consumerName,
      UUID eventId,
      Instant processedAt,
      String resultReference,
      String updatedBy);
}
