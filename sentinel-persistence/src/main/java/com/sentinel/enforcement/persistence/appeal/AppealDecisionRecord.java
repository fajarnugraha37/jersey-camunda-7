package com.sentinel.enforcement.persistence.appeal;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppealDecisionRecord(
    UUID id,
    UUID appealId,
    String outcome,
    String summary,
    OffsetDateTime decidedAt,
    String decidedBy,
    OffsetDateTime createdAt,
    String createdBy,
    long version) {}
