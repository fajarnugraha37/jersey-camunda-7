package com.sentinel.enforcement.domain.appeal;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AppealDecision(
    UUID id,
    UUID appealId,
    AppealDecisionOutcome outcome,
    String summary,
    Instant decidedAt,
    String decidedBy,
    Instant createdAt,
    String createdBy,
    long version) {

  public AppealDecision {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(appealId, "appealId must not be null");
    Objects.requireNonNull(outcome, "outcome must not be null");
    summary = requireNonBlank(summary, "summary");
    Objects.requireNonNull(decidedAt, "decidedAt must not be null");
    decidedBy = requireNonBlank(decidedBy, "decidedBy");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    createdBy = requireNonBlank(createdBy, "createdBy");
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
