package com.sentinel.enforcement.persistence.recommendation;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RecommendationReviewRecord(
    UUID id,
    UUID recommendationId,
    String outcome,
    String reviewSummary,
    OffsetDateTime reviewedAt,
    String reviewedBy,
    OffsetDateTime createdAt,
    String createdBy,
    long version) {}
