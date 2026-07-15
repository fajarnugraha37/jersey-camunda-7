package com.sentinel.enforcement.persistence.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InboxEventData(
    UUID id,
    String consumerName,
    UUID eventId,
    String topic,
    OffsetDateTime createdAt,
    String createdBy,
    OffsetDateTime processedAt,
    String resultReference,
    long version) {}
