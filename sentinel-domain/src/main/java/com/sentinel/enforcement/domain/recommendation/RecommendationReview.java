package com.sentinel.enforcement.domain.recommendation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RecommendationReview(
    UUID id,
    UUID recommendationId,
    RecommendationReviewOutcome outcome,
    String reviewSummary,
    Instant reviewedAt,
    String reviewedBy,
    Instant createdAt,
    String createdBy,
    long version) {

  public RecommendationReview {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(recommendationId, "recommendationId must not be null");
    Objects.requireNonNull(outcome, "outcome must not be null");
    reviewSummary = requireNonBlank(reviewSummary, "reviewSummary");
    Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");
    reviewedBy = requireNonBlank(reviewedBy, "reviewedBy");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    createdBy = requireNonBlank(createdBy, "createdBy");
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
  }

  private static String requireNonBlank(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }
}
