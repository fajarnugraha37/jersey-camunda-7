package com.sentinel.enforcement.domain.appeal;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Appeal(
    UUID id,
    UUID caseId,
    UUID decisionId,
    String rationale,
    boolean supervisorOverride,
    String supervisorOverrideReason,
    AppealStatus status,
    Instant submittedAt,
    String submittedBy,
    UUID decidedByAppealDecisionId,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version) {

  public Appeal {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(caseId, "caseId must not be null");
    Objects.requireNonNull(decisionId, "decisionId must not be null");
    rationale = requireNonBlank(rationale, "rationale");
    if (supervisorOverride) {
      supervisorOverrideReason = requireNonBlank(supervisorOverrideReason, "supervisorOverrideReason");
    } else if (supervisorOverrideReason != null && supervisorOverrideReason.isBlank()) {
      throw new IllegalArgumentException(
          "supervisorOverrideReason must not be blank when provided");
    }
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(submittedAt, "submittedAt must not be null");
    submittedBy = requireNonBlank(submittedBy, "submittedBy");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    createdBy = requireNonBlank(createdBy, "createdBy");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    updatedBy = requireNonBlank(updatedBy, "updatedBy");
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
  }

  public static Appeal create(
      UUID id,
      UUID caseId,
      UUID decisionId,
      String rationale,
      boolean supervisorOverride,
      String supervisorOverrideReason,
      Instant now,
      String actorId) {
    return new Appeal(
        id,
        caseId,
        decisionId,
        rationale,
        supervisorOverride,
        supervisorOverrideReason,
        AppealStatus.ACTIVE,
        now,
        actorId,
        null,
        now,
        actorId,
        now,
        actorId,
        0L);
  }

  public Appeal decide(UUID appealDecisionId, Instant now, String actorId) {
    if (status != AppealStatus.ACTIVE) {
      throw new AppealConflictException(
          "APPEAL_DECISION_NOT_ALLOWED",
          "Appeal " + id + " is already " + status + ".");
    }
    return new Appeal(
        id,
        caseId,
        decisionId,
        rationale,
        supervisorOverride,
        supervisorOverrideReason,
        AppealStatus.DECIDED,
        submittedAt,
        submittedBy,
        appealDecisionId,
        createdAt,
        createdBy,
        now,
        actorId,
        version + 1);
  }

  public String auditSummary() {
    return "status="
        + status
        + ";supervisorOverride="
        + supervisorOverride
        + ";decidedByAppealDecisionId="
        + (decidedByAppealDecisionId == null ? "-" : decidedByAppealDecisionId)
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
}
