package com.sentinel.enforcement.domain.sanction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record SanctionObligation(
    UUID id,
    UUID sanctionId,
    String title,
    String details,
    LocalDate dueDate,
    SanctionObligationStatus status,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version) {

  public SanctionObligation {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(sanctionId, "sanctionId must not be null");
    title = requireNonBlank(title, "title");
    details = requireNonBlank(details, "details");
    Objects.requireNonNull(dueDate, "dueDate must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    createdBy = requireNonBlank(createdBy, "createdBy");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    updatedBy = requireNonBlank(updatedBy, "updatedBy");
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
  }

  public static SanctionObligation create(
      UUID id, UUID sanctionId, String title, String details, LocalDate dueDate, Instant now, String actorId) {
    return new SanctionObligation(
        id,
        sanctionId,
        title,
        details,
        dueDate,
        SanctionObligationStatus.ACTIVE,
        now,
        actorId,
        now,
        actorId,
        0L);
  }

  public SanctionObligation cancel(Instant now, String actorId) {
    return new SanctionObligation(
        id,
        sanctionId,
        title,
        details,
        dueDate,
        SanctionObligationStatus.CANCELLED,
        createdAt,
        createdBy,
        now,
        actorId,
        version + 1);
  }

  private static String requireNonBlank(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }
}
