package com.sentinel.enforcement.persistence.decision;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SanctionRecord(
    UUID id,
    UUID caseId,
    UUID decisionId,
    String summary,
    String status,
    OffsetDateTime createdAt,
    String createdBy,
    OffsetDateTime updatedAt,
    String updatedBy,
    long version) {}
