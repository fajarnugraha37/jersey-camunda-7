package com.sentinel.enforcement.domain.evidence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Evidence(
    UUID id,
    UUID caseId,
    String title,
    EvidenceClassification classification,
    EvidenceStorageStatus storageStatus,
    int latestVersion,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version) {

  public Evidence {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(caseId, "caseId must not be null");
    title = requireNonBlank(title, "title");
    Objects.requireNonNull(classification, "classification must not be null");
    Objects.requireNonNull(storageStatus, "storageStatus must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    createdBy = requireNonBlank(createdBy, "createdBy");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    updatedBy = requireNonBlank(updatedBy, "updatedBy");
    if (latestVersion < 0) {
      throw new IllegalArgumentException("latestVersion must not be negative");
    }
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
  }

  public static Evidence create(
      UUID id,
      UUID caseId,
      String title,
      EvidenceClassification classification,
      Instant now,
      String actorId) {
    return new Evidence(
        id,
        caseId,
        title,
        classification,
        EvidenceStorageStatus.PENDING_UPLOAD,
        0,
        now,
        actorId,
        now,
        actorId,
        0L);
  }

  public Evidence activate(int newLatestVersion, Instant now, String actorId) {
    if (newLatestVersion < 1) {
      throw new IllegalArgumentException("newLatestVersion must be at least 1");
    }
    return new Evidence(
        id,
        caseId,
        title,
        classification,
        EvidenceStorageStatus.ACTIVE,
        newLatestVersion,
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
