package com.sentinel.enforcement.domain.evidence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EvidenceUploadSession(
    UUID id,
    UUID caseId,
    UUID evidenceId,
    int targetVersionNumber,
    String originalFilename,
    String generatedFilename,
    String bucket,
    String objectKey,
    String mediaType,
    long sizeBytes,
    String sha256Checksum,
    EvidenceClassification classification,
    EvidenceUploadSessionStatus status,
    Instant expiresAt,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version) {

  public EvidenceUploadSession {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(caseId, "caseId must not be null");
    Objects.requireNonNull(evidenceId, "evidenceId must not be null");
    originalFilename = requireNonBlank(originalFilename, "originalFilename");
    generatedFilename = requireNonBlank(generatedFilename, "generatedFilename");
    bucket = requireNonBlank(bucket, "bucket");
    objectKey = requireNonBlank(objectKey, "objectKey");
    mediaType = requireNonBlank(mediaType, "mediaType");
    sha256Checksum = requireNonBlank(sha256Checksum, "sha256Checksum");
    Objects.requireNonNull(classification, "classification must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    createdBy = requireNonBlank(createdBy, "createdBy");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    updatedBy = requireNonBlank(updatedBy, "updatedBy");
    if (targetVersionNumber < 1) {
      throw new IllegalArgumentException("targetVersionNumber must be at least 1");
    }
    if (sizeBytes < 0) {
      throw new IllegalArgumentException("sizeBytes must not be negative");
    }
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
  }

  public static EvidenceUploadSession create(
      UUID id,
      UUID caseId,
      UUID evidenceId,
      int targetVersionNumber,
      String originalFilename,
      String generatedFilename,
      String bucket,
      String objectKey,
      String mediaType,
      long sizeBytes,
      String sha256Checksum,
      EvidenceClassification classification,
      Instant now,
      Instant expiresAt,
      String actorId) {
    return new EvidenceUploadSession(
        id,
        caseId,
        evidenceId,
        targetVersionNumber,
        originalFilename,
        generatedFilename,
        bucket,
        objectKey,
        mediaType,
        sizeBytes,
        sha256Checksum,
        classification,
        EvidenceUploadSessionStatus.PENDING,
        expiresAt,
        now,
        actorId,
        now,
        actorId,
        0L);
  }

  public EvidenceUploadSession finalizeSession(Instant now, String actorId) {
    if (status != EvidenceUploadSessionStatus.PENDING) {
      throw new EvidenceConflictException(
          "EVIDENCE_UPLOAD_SESSION_ALREADY_FINALIZED",
          "Evidence upload session " + id + " has already been finalized.");
    }
    if (expiresAt.isBefore(now)) {
      throw new EvidenceConflictException(
          "EVIDENCE_UPLOAD_SESSION_EXPIRED",
          "Evidence upload session " + id + " expired at " + expiresAt + ".");
    }
    return new EvidenceUploadSession(
        id,
        caseId,
        evidenceId,
        targetVersionNumber,
        originalFilename,
        generatedFilename,
        bucket,
        objectKey,
        mediaType,
        sizeBytes,
        sha256Checksum,
        classification,
        EvidenceUploadSessionStatus.FINALIZED,
        expiresAt,
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
