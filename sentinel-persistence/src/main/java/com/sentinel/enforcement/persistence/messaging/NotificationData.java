package com.sentinel.enforcement.persistence.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationData(
    UUID id,
    String consumerName,
    UUID eventId,
    UUID caseId,
    String notificationType,
    String title,
    String body,
    String status,
    OffsetDateTime createdAt,
    String createdBy,
    OffsetDateTime updatedAt,
    String updatedBy,
    long version) {}
