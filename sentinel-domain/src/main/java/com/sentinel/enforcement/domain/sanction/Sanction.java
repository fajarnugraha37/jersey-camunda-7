package com.sentinel.enforcement.domain.sanction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Sanction(
    UUID id,
    UUID caseId,
    UUID decisionId,
    String summary,
    SanctionStatus status,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version) {

  public Sanction {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(caseId, "caseId must not be null");
    Objects.requireNonNull(decisionId, "decisionId must not be null");
    summary = requireNonBlank(summary, "summary");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    createdBy = requireNonBlank(createdBy, "createdBy");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    updatedBy = requireNonBlank(updatedBy, "updatedBy");
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
  }

  public static Sanction create(
      UUID id, UUID caseId, UUID decisionId, String summary, Instant now, String actorId) {
    return new Sanction(
        id, caseId, decisionId, summary, SanctionStatus.ACTIVE, now, actorId, now, actorId, 0L);
  }

  public Sanction cancel(Instant now, String actorId) {
    return new Sanction(
        id, caseId, decisionId, summary, SanctionStatus.CANCELLED, createdAt, createdBy, now, actorId, version + 1);
  }

  private static String requireNonBlank(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }
}
