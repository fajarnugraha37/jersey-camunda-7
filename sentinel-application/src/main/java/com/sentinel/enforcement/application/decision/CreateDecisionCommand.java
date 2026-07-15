package com.sentinel.enforcement.application.decision;

import java.time.LocalDate;
import java.util.Objects;

public record CreateDecisionCommand(
    String title,
    String summary,
    boolean violationProven,
    String sanctionSummary,
    String obligationTitle,
    String obligationDetails,
    LocalDate obligationDueDate,
    LocalDate appealDeadline,
    String correlationId,
    String sourceIp) {

  public CreateDecisionCommand {
    title = requireNonBlank(title, "title");
    summary = requireNonBlank(summary, "summary");
    correlationId = requireNonBlank(correlationId, "correlationId");
    Objects.requireNonNull(appealDeadline, "appealDeadline must not be null");
    if (violationProven) {
      sanctionSummary = requireNonBlank(sanctionSummary, "sanctionSummary");
      obligationTitle = requireNonBlank(obligationTitle, "obligationTitle");
      obligationDetails = requireNonBlank(obligationDetails, "obligationDetails");
      Objects.requireNonNull(obligationDueDate, "obligationDueDate must not be null");
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
