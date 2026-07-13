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
}
