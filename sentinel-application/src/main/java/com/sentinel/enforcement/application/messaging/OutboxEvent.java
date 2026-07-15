package com.sentinel.enforcement.application.messaging;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(
    UUID eventId,
    String topic,
    String messageKey,
    EventEnvelope envelope,
    String status,
    Instant availableAt,
    String leaseOwner,
    Instant leaseExpiresAt,
    int publishAttempts,
    String lastError,
    Instant publishedAt,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version) {}
