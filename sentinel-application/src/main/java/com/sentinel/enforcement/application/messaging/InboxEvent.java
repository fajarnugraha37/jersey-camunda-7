package com.sentinel.enforcement.application.messaging;

import java.time.Instant;
import java.util.UUID;

public record InboxEvent(
    UUID id,
    String consumerName,
    UUID eventId,
    String topic,
    Instant createdAt,
    String createdBy,
    Instant processedAt,
    String resultReference,
    long version) {}
