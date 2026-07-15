package com.sentinel.enforcement.persistence.appeal;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppealRecord(
    UUID id,
    UUID caseId,
    UUID decisionId,
    String rationale,
    boolean supervisorOverride,
    String supervisorOverrideReason,
    String status,
    OffsetDateTime submittedAt,
    String submittedBy,
    UUID decidedByAppealDecisionId,
    OffsetDateTime createdAt,
    String createdBy,
    OffsetDateTime updatedAt,
    String updatedBy,
    long version) {}
