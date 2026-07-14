package com.sentinel.enforcement.persistence.evidence;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EvidenceRecord(
    UUID id,
    UUID caseId,
    String title,
    String classification,
    String storageStatus,
    int latestVersion,
    OffsetDateTime createdAt,
    String createdBy,
    OffsetDateTime updatedAt,
    String updatedBy,
    long version) {}
