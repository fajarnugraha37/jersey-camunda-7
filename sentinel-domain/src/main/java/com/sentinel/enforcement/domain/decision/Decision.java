package com.sentinel.enforcement.domain.decision;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record Decision(
    UUID id,
    UUID caseId,
    UUID recommendationId,
    String title,
    String summary,
    boolean violationProven,
    String sanctionSummary,
    String obligationTitle,
    String obligationDetails,
    LocalDate obligationDueDate,
    LocalDate appealDeadline,
    DecisionStatus status,
    Instant approvedAt,
    String approvedBy,
    Instant publishedAt,
    String publishedBy,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version) {

  public Decision {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(caseId, "caseId must not be null");
    Objects.requireNonNull(recommendationId, "recommendationId must not be null");
    title = requireNonBlank(title, "title");
    summary = requireNonBlank(summary, "summary");
    if (violationProven) {
      sanctionSummary = requireNonBlank(sanctionSummary, "sanctionSummary");
      obligationTitle = requireNonBlank(obligationTitle, "obligationTitle");
      obligationDetails = requireNonBlank(obligationDetails, "obligationDetails");
      Objects.requireNonNull(obligationDueDate, "obligationDueDate must not be null");
    } else {
      sanctionSummary = nullIfBlank(sanctionSummary);
      obligationTitle = nullIfBlank(obligationTitle);
      obligationDetails = nullIfBlank(obligationDetails);
      obligationDueDate = null;
    }
    Objects.requireNonNull(appealDeadline, "appealDeadline must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    createdBy = requireNonBlank(createdBy, "createdBy");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    updatedBy = requireNonBlank(updatedBy, "updatedBy");
    if (approvedBy != null && approvedBy.isBlank()) {
      throw new IllegalArgumentException("approvedBy must not be blank when provided");
    }
    if (publishedBy != null && publishedBy.isBlank()) {
      throw new IllegalArgumentException("publishedBy must not be blank when provided");
    }
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
  }

  public static Decision create(
      UUID id,
      UUID caseId,
      UUID recommendationId,
      String title,
      String summary,
      boolean violationProven,
      String sanctionSummary,
      String obligationTitle,
      String obligationDetails,
      LocalDate obligationDueDate,
      LocalDate appealDeadline,
      Instant now,
      String actorId) {
    return new Decision(
        id,
        caseId,
        recommendationId,
        title,
        summary,
        violationProven,
        sanctionSummary,
        obligationTitle,
        obligationDetails,
        obligationDueDate,
        appealDeadline,
        DecisionStatus.DRAFT,
        null,
        null,
        null,
        null,
        now,
        actorId,
        now,
        actorId,
        0L);
  }

  public Decision approve(Instant now, String actorId) {
    if (status != DecisionStatus.DRAFT) {
      throw new DecisionConflictException(
          "DECISION_APPROVAL_NOT_ALLOWED",
          "Decision " + id + " cannot be approved from status " + status + ".");
    }
    if (createdBy.equals(actorId)) {
      throw new DecisionConflictException(
          "MAKER_CHECKER_VIOLATION",
          "Decision author cannot approve the same draft.");
    }
    return new Decision(
        id,
        caseId,
        recommendationId,
        title,
        summary,
        violationProven,
        sanctionSummary,
        obligationTitle,
        obligationDetails,
        obligationDueDate,
        appealDeadline,
        DecisionStatus.APPROVED,
        now,
        actorId,
        publishedAt,
        publishedBy,
        createdAt,
        createdBy,
        now,
        actorId,
        version + 1);
  }

  public Decision publish(Instant now, String actorId) {
    if (status == DecisionStatus.PUBLISHED) {
      throw new DecisionConflictException(
          "DECISION_PUBLICATION_NOT_ALLOWED",
          "Published decision is immutable.");
    }
    if (status != DecisionStatus.APPROVED) {
      throw new DecisionConflictException(
          "DECISION_PUBLICATION_NOT_ALLOWED",
          "Decision " + id + " must be approved before publication.");
    }
    return new Decision(
        id,
        caseId,
        recommendationId,
        title,
        summary,
        violationProven,
        sanctionSummary,
        obligationTitle,
        obligationDetails,
        obligationDueDate,
        appealDeadline,
        DecisionStatus.PUBLISHED,
        approvedAt,
        approvedBy,
        now,
        actorId,
        createdAt,
        createdBy,
        now,
        actorId,
        version + 1);
  }

  public String auditSummary() {
    return "status="
        + status
        + ";violationProven="
        + violationProven
        + ";appealDeadline="
        + appealDeadline
        + ";version="
        + version;
  }

  private static String requireNonBlank(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }

  private static String nullIfBlank(String value) {
    if (value == null) {
      return null;
    }
    return value.isBlank() ? null : value;
  }
}
