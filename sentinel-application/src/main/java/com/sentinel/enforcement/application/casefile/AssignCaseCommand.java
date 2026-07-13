package com.sentinel.enforcement.application.casefile;

import java.util.Objects;

public record AssignCaseCommand(
    String assignedUnitId,
    String assigneeUserId,
    long expectedVersion,
    String reason,
    String correlationId,
    String sourceIp) {

  public AssignCaseCommand {
    assignedUnitId = requireNonBlank(assignedUnitId, "assignedUnitId");
    assigneeUserId = requireNonBlank(assigneeUserId, "assigneeUserId");
    reason = requireNonBlank(reason, "reason");
    correlationId = requireNonBlank(correlationId, "correlationId");
    if (expectedVersion < 0) {
      throw new IllegalArgumentException("expectedVersion must not be negative");
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
