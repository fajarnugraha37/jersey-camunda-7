package com.sentinel.enforcement.persistence.casefile;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CaseAssignmentData(
    UUID id,
    UUID caseId,
    String assignedUnitId,
    String assigneeUserId,
    String assignmentReason,
    OffsetDateTime assignedAt,
    String assignedBy,
    OffsetDateTime createdAt,
    String createdBy,
    OffsetDateTime updatedAt,
    String updatedBy,
    long version) {}
