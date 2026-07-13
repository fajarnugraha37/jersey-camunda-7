package com.sentinel.enforcement.domain.casefile;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CaseAssignment(
    UUID id,
    UUID caseId,
    String assignedUnitId,
    String assigneeUserId,
    String assignmentReason,
    Instant assignedAt,
    String assignedBy,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version) {

  public CaseAssignment {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(caseId, "caseId must not be null");
    assignedUnitId = requireNonBlank(assignedUnitId, "assignedUnitId");
    assigneeUserId = requireNonBlank(assigneeUserId, "assigneeUserId");
    assignmentReason = requireNonBlank(assignmentReason, "assignmentReason");
    Objects.requireNonNull(assignedAt, "assignedAt must not be null");
    assignedBy = requireNonBlank(assignedBy, "assignedBy");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    createdBy = requireNonBlank(createdBy, "createdBy");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    updatedBy = requireNonBlank(updatedBy, "updatedBy");
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
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
