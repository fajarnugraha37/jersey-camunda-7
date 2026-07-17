package com.sentinel.enforcement.application.operations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record MaintenanceOperationRunView(
    UUID runId,
    String operationName,
    String requestedBy,
    Instant requestedAt,
    Instant completedAt,
    LocalDate effectiveDate,
    String resultStatus,
    long affectedRows) {

  public MaintenanceOperationRunView {
    Objects.requireNonNull(runId, "runId must not be null");
    operationName = requireNonBlank(operationName, "operationName");
    requestedBy = requireNonBlank(requestedBy, "requestedBy");
    Objects.requireNonNull(requestedAt, "requestedAt must not be null");
    Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");
    resultStatus = requireNonBlank(resultStatus, "resultStatus");
    if (affectedRows < 0) {
      throw new IllegalArgumentException("affectedRows must not be negative");
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
