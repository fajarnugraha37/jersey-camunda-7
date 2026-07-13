package com.sentinel.enforcement.application.casefile;

import java.util.Objects;
import java.util.UUID;

public record CreateCaseCommand(
    UUID reportId, String title, String summary, String correlationId, String sourceIp) {

  public CreateCaseCommand {
    Objects.requireNonNull(reportId, "reportId must not be null");
    title = requireNonBlank(title, "title");
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
