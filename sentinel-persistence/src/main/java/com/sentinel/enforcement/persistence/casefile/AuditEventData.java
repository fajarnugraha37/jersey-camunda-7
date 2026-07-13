package com.sentinel.enforcement.persistence.casefile;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditEventData(
    UUID eventId,
    String eventType,
    String actorType,
    String actorId,
    String actorRoles,
    String action,
    String resourceType,
    String resourceId,
    UUID caseId,
    OffsetDateTime timestamp,
    String correlationId,
    String sourceIp,
    String result,
    String reason,
    String beforeSummary,
    String afterSummary,
    String metadata) {}
