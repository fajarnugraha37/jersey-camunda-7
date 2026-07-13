package com.sentinel.enforcement.domain.casefile;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CaseStatusHistoryEntry(
    UUID id,
    UUID caseId,
    CaseStatus fromStatus,
    CaseStatus toStatus,
    String transitionReason,
    Instant transitionedAt,
    String transitionedBy,
    Instant createdAt,
    String createdBy) {

  public CaseStatusHistoryEntry {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(caseId, "caseId must not be null");
    Objects.requireNonNull(toStatus, "toStatus must not be null");
    transitionReason = requireNonBlank(transitionReason, "transitionReason");
    Objects.requireNonNull(transitionedAt, "transitionedAt must not be null");
    transitionedBy = requireNonBlank(transitionedBy, "transitionedBy");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    createdBy = requireNonBlank(createdBy, "createdBy");
  }

  private static String requireNonBlank(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }
}
