package com.sentinel.enforcement.application.operations;

import java.time.LocalDate;
import java.util.Objects;

public record RecalculateOverdueSanctionObligationsCommand(
    LocalDate effectiveDate, String correlationId, String sourceIp) {

  public RecalculateOverdueSanctionObligationsCommand {
    Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");
    correlationId = requireNonBlank(correlationId, "correlationId");
    sourceIp = normalizeOptional(sourceIp);
  }

  private static String requireNonBlank(String value, String fieldName) {
    String normalized = normalizeOptional(value);
    if (normalized == null) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return normalized;
  }

  private static String normalizeOptional(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
