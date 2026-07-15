package com.sentinel.enforcement.application.recommendation;

import java.util.Objects;

public record CreateRecommendationCommand(
    String title,
    String summary,
    String proposedDecision,
    String proposedSanction,
    String correlationId,
    String sourceIp) {

  public CreateRecommendationCommand {
    title = requireNonBlank(title, "title");
    summary = requireNonBlank(summary, "summary");
    proposedDecision = requireNonBlank(proposedDecision, "proposedDecision");
    correlationId = requireNonBlank(correlationId, "correlationId");
    if (proposedSanction != null && proposedSanction.isBlank()) {
      throw new IllegalArgumentException("proposedSanction must not be blank when provided");
    }
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
