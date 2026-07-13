package com.sentinel.enforcement.domain.casefile;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CaseRecord(
    UUID id,
    String caseNumber,
    UUID reportId,
    String title,
    String summary,
    String jurisdictionCode,
    CaseStatus status,
    String assignedUnitId,
    String assigneeUserId,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version) {

  private static final Set<String> ASSIGNMENT_ALLOWED_ROLES =
      Set.of("TRIAGE_OFFICER", "SUPERVISOR");

  public CaseRecord {
    Objects.requireNonNull(id, "id must not be null");
    caseNumber = requireNonBlank(caseNumber, "caseNumber");
    Objects.requireNonNull(reportId, "reportId must not be null");
    title = requireNonBlank(title, "title");
    summary = requireNonBlank(summary, "summary");
    jurisdictionCode = requireNonBlank(jurisdictionCode, "jurisdictionCode");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    createdBy = requireNonBlank(createdBy, "createdBy");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    updatedBy = requireNonBlank(updatedBy, "updatedBy");
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
    if (assignedUnitId != null && assignedUnitId.isBlank()) {
      throw new IllegalArgumentException("assignedUnitId must not be blank when provided");
    }
    if (assigneeUserId != null && assigneeUserId.isBlank()) {
      throw new IllegalArgumentException("assigneeUserId must not be blank when provided");
    }
  }

  public static CaseRecord create(
      UUID id,
      String caseNumber,
      UUID reportId,
      String title,
      String summary,
      String jurisdictionCode,
      Instant now,
      String actorId) {
    return new CaseRecord(
        id,
        caseNumber,
        reportId,
        title,
        summary,
        jurisdictionCode,
        CaseStatus.CREATED,
        null,
        null,
        now,
        actorId,
        now,
        actorId,
        0L);
  }

  public CaseRecord assignTo(
      String nextAssignedUnitId, String nextAssigneeUserId, CaseActionContext context) {
    validateExpectedVersion(context);
    if (status.isTerminal()) {
      throw new CaseConflictException(
          "CASE_ASSIGNMENT_NOT_ALLOWED",
          "Case " + caseNumber + " cannot be assigned after it reaches " + status + ".");
    }
    if (!context.hasAnyRole(ASSIGNMENT_ALLOWED_ROLES.toArray(String[]::new))) {
      throw new CaseConflictException(
          "CASE_ASSIGNMENT_NOT_ALLOWED",
          "Actor does not have role permission to assign case " + caseNumber + ".");
    }
    return new CaseRecord(
        id,
        caseNumber,
        reportId,
        title,
        summary,
        jurisdictionCode,
        status,
        requireNonBlank(nextAssignedUnitId, "assignedUnitId"),
        requireNonBlank(nextAssigneeUserId, "assigneeUserId"),
        createdAt,
        createdBy,
        context.timestamp(),
        context.actorId(),
        version + 1);
  }

  public CaseRecord transitionTo(CaseStatus targetStatus, CaseActionContext context) {
    validateExpectedVersion(context);
    Objects.requireNonNull(targetStatus, "targetStatus must not be null");
    if (status == targetStatus) {
      throw new CaseConflictException(
          "CASE_TRANSITION_NOT_ALLOWED",
          "Case " + caseNumber + " is already in status " + status + ".");
    }
    if (!isAllowedTarget(targetStatus)) {
      throw new CaseConflictException(
          "CASE_TRANSITION_NOT_ALLOWED",
          "Case cannot transition from " + status + " to " + targetStatus + ".");
    }
    if (!context.hasAnyRole(requiredRolesFor(status, targetStatus))) {
      throw new CaseConflictException(
          "CASE_TRANSITION_NOT_ALLOWED",
          "Actor does not have role permission to transition case "
              + caseNumber
              + " from "
              + status
              + " to "
              + targetStatus
              + ".");
    }
    return new CaseRecord(
        id,
        caseNumber,
        reportId,
        title,
        summary,
        jurisdictionCode,
        targetStatus,
        assignedUnitId,
        assigneeUserId,
        createdAt,
        createdBy,
        context.timestamp(),
        context.actorId(),
        version + 1);
  }

  public String auditSummary() {
    return "status="
        + status
        + ";assignedUnitId="
        + valueOrDash(assignedUnitId)
        + ";assigneeUserId="
        + valueOrDash(assigneeUserId)
        + ";version="
        + version;
  }

  private void validateExpectedVersion(CaseActionContext context) {
    if (context.expectedVersion() != version) {
      throw new CaseConflictException(
          "CONCURRENT_MODIFICATION",
          "Case "
              + caseNumber
              + " expected version "
              + context.expectedVersion()
              + " but current version is "
              + version
              + ".");
    }
  }

  private boolean isAllowedTarget(CaseStatus targetStatus) {
    return switch (status) {
      case CREATED -> targetStatus == CaseStatus.UNDER_TRIAGE;
      case UNDER_TRIAGE ->
          targetStatus == CaseStatus.UNDER_INVESTIGATION || targetStatus == CaseStatus.CANCELLED;
      case UNDER_INVESTIGATION ->
          targetStatus == CaseStatus.PENDING_REVIEW || targetStatus == CaseStatus.CANCELLED;
      case PENDING_REVIEW ->
          targetStatus == CaseStatus.UNDER_INVESTIGATION
              || targetStatus == CaseStatus.PENDING_DECISION;
      case PENDING_DECISION ->
          targetStatus == CaseStatus.UNDER_INVESTIGATION || targetStatus == CaseStatus.DECIDED;
      case DECIDED ->
          targetStatus == CaseStatus.UNDER_APPEAL
              || targetStatus == CaseStatus.ENFORCEMENT_IN_PROGRESS;
      case UNDER_APPEAL ->
          targetStatus == CaseStatus.DECIDED || targetStatus == CaseStatus.ENFORCEMENT_IN_PROGRESS;
      case ENFORCEMENT_IN_PROGRESS -> targetStatus == CaseStatus.CLOSED;
      case CLOSED, CANCELLED -> false;
    };
  }

  private String[] requiredRolesFor(CaseStatus fromStatus, CaseStatus targetStatus) {
    return switch (fromStatus) {
      case CREATED -> new String[] {"TRIAGE_OFFICER", "SUPERVISOR"};
      case UNDER_TRIAGE -> new String[] {"TRIAGE_OFFICER", "SUPERVISOR"};
      case UNDER_INVESTIGATION -> new String[] {"INVESTIGATOR", "SUPERVISOR"};
      case PENDING_REVIEW -> new String[] {"CASE_REVIEWER", "SUPERVISOR"};
      case PENDING_DECISION -> new String[] {"DECISION_MAKER", "SUPERVISOR"};
      case DECIDED ->
          targetStatus == CaseStatus.UNDER_APPEAL
              ? new String[] {"APPEAL_OFFICER", "SUPERVISOR"}
              : new String[] {"DECISION_MAKER", "SUPERVISOR"};
      case UNDER_APPEAL -> new String[] {"APPEAL_OFFICER", "SUPERVISOR"};
      case ENFORCEMENT_IN_PROGRESS -> new String[] {"SUPERVISOR"};
      case CLOSED, CANCELLED -> new String[0];
    };
  }

  private static String requireNonBlank(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }

  private static String valueOrDash(String value) {
    return value == null ? "-" : value;
  }
}
