package com.sentinel.enforcement.persistence.casefile;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CaseAssignmentRotationData(
    UUID caseId,
    long expectedVersion,
    String assignedUnitId,
    String assigneeUserId,
    OffsetDateTime updatedAt,
    String updatedBy,
    UUID newAssignmentId,
    String assignmentReason,
    OffsetDateTime assignedAt,
    String assignedBy,
    OffsetDateTime createdAt,
    String createdBy,
    long newAssignmentVersion) {}
