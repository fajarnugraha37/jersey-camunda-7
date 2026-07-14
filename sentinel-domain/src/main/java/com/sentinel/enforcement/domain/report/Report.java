package com.sentinel.enforcement.domain.report;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Report(
    UUID id,
    String title,
    String description,
    String jurisdictionCode,
    String reporterName,
    ReportStatus status,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version) {

  public Report {
    Objects.requireNonNull(id, "id must not be null");
    title = Objects.requireNonNull(title, "title must not be null");
    description = Objects.requireNonNull(description, "description must not be null");
    jurisdictionCode =
        Objects.requireNonNull(jurisdictionCode, "jurisdictionCode must not be null");
    reporterName = Objects.requireNonNull(reporterName, "reporterName must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    updatedBy = Objects.requireNonNull(updatedBy, "updatedBy must not be null");
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
  }

  public Report triage(String actorId, long expectedVersion, String reason, Instant now) {
    Objects.requireNonNull(now, "now must not be null");
    if (expectedVersion != version) {
      throw new ReportConflictException(
          "CONCURRENT_MODIFICATION",
          "Report " + id + " expected version " + expectedVersion + " but current version is " + version + ".");
    }
    if (status != ReportStatus.SUBMITTED) {
      throw new ReportConflictException(
          "REPORT_TRIAGE_NOT_ALLOWED",
          "Report " + id + " cannot be triaged from status " + status + ".");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
    if (actorId == null || actorId.isBlank()) {
      throw new IllegalArgumentException("actorId must not be blank");
    }
    return new Report(
        id,
        title,
        description,
        jurisdictionCode,
        reporterName,
        ReportStatus.TRIAGED,
        createdAt,
        createdBy,
        now,
        actorId,
        version + 1);
  }
}
