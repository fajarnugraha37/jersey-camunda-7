package com.sentinel.enforcement.application.messaging;

import java.time.Instant;
import java.util.UUID;

public record NotificationRecord(
    UUID id,
    String consumerName,
    UUID eventId,
    UUID caseId,
    String notificationType,
    String title,
    String body,
    String status,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version) {}
