package com.sentinel.enforcement.persistence.evidence;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EvidenceUploadSessionRecord(
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
    String classification,
    String status,
    OffsetDateTime expiresAt,
    OffsetDateTime createdAt,
    String createdBy,
    OffsetDateTime updatedAt,
    String updatedBy,
    long version) {}
