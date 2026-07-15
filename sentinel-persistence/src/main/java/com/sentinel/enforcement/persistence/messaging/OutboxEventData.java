package com.sentinel.enforcement.persistence.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OutboxEventData(
    UUID eventId,
    String topic,
    String messageKey,
    String eventType,
    int eventVersion,
    String aggregateType,
    UUID aggregateId,
    OffsetDateTime occurredAt,
    String correlationId,
    String causationId,
    String actorType,
    String actorId,
    String payloadJson,
    String status,
    OffsetDateTime availableAt,
    String leaseOwner,
    OffsetDateTime leaseExpiresAt,
    int publishAttempts,
    String lastError,
    OffsetDateTime publishedAt,
    OffsetDateTime createdAt,
    String createdBy,
    OffsetDateTime updatedAt,
    String updatedBy,
    long version) {}
