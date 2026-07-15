package com.sentinel.enforcement.domain.decision;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record DecisionVersion(
    UUID id,
    UUID decisionId,
    int versionNumber,
    String title,
    String summary,
    boolean violationProven,
    String sanctionSummary,
    String obligationTitle,
    String obligationDetails,
    LocalDate obligationDueDate,
    LocalDate appealDeadline,
    Instant publishedAt,
    String publishedBy,
    Instant createdAt,
    String createdBy) {

  public DecisionVersion {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(decisionId, "decisionId must not be null");
    if (versionNumber < 1) {
      throw new IllegalArgumentException("versionNumber must be at least 1");
    }
    title = requireNonBlank(title, "title");
    summary = requireNonBlank(summary, "summary");
    if (violationProven) {
      sanctionSummary = requireNonBlank(sanctionSummary, "sanctionSummary");
      obligationTitle = requireNonBlank(obligationTitle, "obligationTitle");
      obligationDetails = requireNonBlank(obligationDetails, "obligationDetails");
      Objects.requireNonNull(obligationDueDate, "obligationDueDate must not be null");
    }
    Objects.requireNonNull(appealDeadline, "appealDeadline must not be null");
    Objects.requireNonNull(publishedAt, "publishedAt must not be null");
    publishedBy = requireNonBlank(publishedBy, "publishedBy");
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
