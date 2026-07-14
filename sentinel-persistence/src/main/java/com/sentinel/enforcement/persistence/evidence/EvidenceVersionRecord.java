package com.sentinel.enforcement.persistence.evidence;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EvidenceVersionRecord(
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
    OffsetDateTime uploadedAt,
    String uploadedBy,
    OffsetDateTime createdAt,
    String createdBy) {}
