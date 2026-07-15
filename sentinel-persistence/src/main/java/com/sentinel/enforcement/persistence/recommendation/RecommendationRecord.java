package com.sentinel.enforcement.persistence.recommendation;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RecommendationRecord(
    UUID id,
    UUID caseId,
    String title,
    String summary,
    String proposedDecision,
    String proposedSanction,
    String status,
    OffsetDateTime submittedAt,
    String submittedBy,
    UUID approvedReviewId,
    OffsetDateTime createdAt,
    String createdBy,
    OffsetDateTime updatedAt,
    String updatedBy,
    long version) {}
