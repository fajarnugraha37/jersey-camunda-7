package com.sentinel.enforcement.application.appeal;

import com.sentinel.enforcement.domain.appeal.AppealDecisionOutcome;
import java.util.Objects;

public record DecideAppealCommand(
    AppealDecisionOutcome outcome, String summary, String correlationId, String sourceIp) {
  public DecideAppealCommand {
    Objects.requireNonNull(outcome, "outcome must not be null");
    summary = requireNonBlank(summary, "summary");
    correlationId = requireNonBlank(correlationId, "correlationId");
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
