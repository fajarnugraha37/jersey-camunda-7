package com.sentinel.enforcement.application.recommendation;

import java.util.UUID;

public final class RecommendationNotFoundException extends RuntimeException {
  public RecommendationNotFoundException(UUID recommendationId) {
    super("Recommendation " + recommendationId + " was not found.");
  }
}
