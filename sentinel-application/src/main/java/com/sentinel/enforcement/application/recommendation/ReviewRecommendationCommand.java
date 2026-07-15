package com.sentinel.enforcement.application.recommendation;

import java.util.Objects;

public record ReviewRecommendationCommand(
    String reviewSummary, String correlationId, String sourceIp) {
  public ReviewRecommendationCommand {
    reviewSummary = requireNonBlank(reviewSummary, "reviewSummary");
    correlationId = requireNonBlank(correlationId, "correlationId");
    if (sourceIp != null && sourceIp.isBlank()) {
      throw new IllegalArgumentException("sourceIp must not be blank when provided");
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
