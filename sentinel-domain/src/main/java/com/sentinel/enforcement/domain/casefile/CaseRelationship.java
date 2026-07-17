package com.sentinel.enforcement.domain.casefile;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CaseRelationship(
    UUID id,
    UUID parentCaseId,
    UUID childCaseId,
    CaseRelationshipType relationshipType,
    String relationshipReason,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version) {

  public CaseRelationship {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(parentCaseId, "parentCaseId must not be null");
    Objects.requireNonNull(childCaseId, "childCaseId must not be null");
    if (parentCaseId.equals(childCaseId)) {
      throw new IllegalArgumentException("parentCaseId and childCaseId must be different");
    }
    Objects.requireNonNull(relationshipType, "relationshipType must not be null");
    relationshipReason = requireNonBlank(relationshipReason, "relationshipReason");
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
