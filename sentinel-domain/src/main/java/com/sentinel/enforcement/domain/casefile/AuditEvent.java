package com.sentinel.enforcement.domain.casefile;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AuditEvent(
    UUID eventId,
    String eventType,
    String actorType,
    String actorId,
    String actorRoles,
    String action,
    String resourceType,
    String resourceId,
    UUID caseId,
    Instant timestamp,
    String correlationId,
    String sourceIp,
    String result,
    String reason,
    String beforeSummary,
    String afterSummary,
    String metadata) {

  public AuditEvent {
    Objects.requireNonNull(eventId, "eventId must not be null");
    eventType = requireNonBlank(eventType, "eventType");
    actorType = requireNonBlank(actorType, "actorType");
    actorId = requireNonBlank(actorId, "actorId");
    actorRoles = requireNonBlank(actorRoles, "actorRoles");
    action = requireNonBlank(action, "action");
    resourceType = requireNonBlank(resourceType, "resourceType");
    resourceId = requireNonBlank(resourceId, "resourceId");
    Objects.requireNonNull(caseId, "caseId must not be null");
    Objects.requireNonNull(timestamp, "timestamp must not be null");
    correlationId = requireNonBlank(correlationId, "correlationId");
    result = requireNonBlank(result, "result");
    metadata = Objects.requireNonNullElse(metadata, "");
    if (sourceIp != null && sourceIp.isBlank()) {
      throw new IllegalArgumentException("sourceIp must not be blank when provided");
    }
    if (reason != null && reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank when provided");
    }
  }

  private static String requireNonBlank(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }
}
