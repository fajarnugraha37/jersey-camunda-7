package com.sentinel.enforcement.domain.evidence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EvidenceVersion(
    UUID id,
    UUID evidenceId,
    int versionNumber,
    String originalFilename,
    String generatedFilename,
    String bucket,
    String objectKey,
    String mediaType,
    long sizeBytes,
    String sha256Checksum,
    Instant uploadedAt,
    String uploadedBy,
    Instant createdAt,
    String createdBy) {

  public EvidenceVersion {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(evidenceId, "evidenceId must not be null");
    originalFilename = requireNonBlank(originalFilename, "originalFilename");
    generatedFilename = requireNonBlank(generatedFilename, "generatedFilename");
    bucket = requireNonBlank(bucket, "bucket");
    objectKey = requireNonBlank(objectKey, "objectKey");
    mediaType = requireNonBlank(mediaType, "mediaType");
    sha256Checksum = requireNonBlank(sha256Checksum, "sha256Checksum");
    Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");
    uploadedBy = requireNonBlank(uploadedBy, "uploadedBy");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    createdBy = requireNonBlank(createdBy, "createdBy");
    if (versionNumber < 1) {
      throw new IllegalArgumentException("versionNumber must be at least 1");
    }
    if (sizeBytes < 0) {
      throw new IllegalArgumentException("sizeBytes must not be negative");
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
