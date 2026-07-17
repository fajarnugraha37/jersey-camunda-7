package com.sentinel.enforcement.persistence.casefile;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CaseRelationshipData(
    UUID id,
    UUID parentCaseId,
    UUID childCaseId,
    String relationshipType,
    String relationshipReason,
    OffsetDateTime createdAt,
    String createdBy,
    OffsetDateTime updatedAt,
    String updatedBy,
    long version) {}
