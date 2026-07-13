package com.sentinel.enforcement.persistence.casefile;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CaseStatusHistoryData(
    UUID id,
    UUID caseId,
    String fromStatus,
    String toStatus,
    String transitionReason,
    OffsetDateTime transitionedAt,
    String transitionedBy,
    OffsetDateTime createdAt,
    String createdBy) {}
