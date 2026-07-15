package com.sentinel.enforcement.application.appeal;

import java.time.Instant;
import java.util.Objects;

public record CreateAppealCommand(
    String rationale,
    Instant submittedAt,
    boolean supervisorOverride,
    String supervisorOverrideReason,
    String correlationId,
    String sourceIp) {

  public CreateAppealCommand {
    rationale = requireNonBlank(rationale, "rationale");
    Objects.requireNonNull(submittedAt, "submittedAt must not be null");
    correlationId = requireNonBlank(correlationId, "correlationId");
    if (supervisorOverride) {
      supervisorOverrideReason =
          requireNonBlank(supervisorOverrideReason, "supervisorOverrideReason");
    } else if (supervisorOverrideReason != null && supervisorOverrideReason.isBlank()) {
      throw new IllegalArgumentException(
          "supervisorOverrideReason must not be blank when provided");
    }
    if (sourceIp != null && sourceIp.isBlank()) {
      throw new IllegalArgumentException("sourceIp must not be blank when provided");
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
