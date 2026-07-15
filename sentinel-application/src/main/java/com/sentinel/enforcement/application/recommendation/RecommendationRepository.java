package com.sentinel.enforcement.application.recommendation;

import com.sentinel.enforcement.domain.recommendation.Recommendation;
import com.sentinel.enforcement.domain.recommendation.RecommendationReview;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationRepository {
  void save(Recommendation recommendation);

  Optional<Recommendation> findById(UUID recommendationId);

  Optional<Recommendation> findByCaseId(UUID caseId);

  void submit(Recommendation recommendation);

  void approve(Recommendation recommendation, RecommendationReview recommendationReview);

  boolean existsApprovedForCase(UUID caseId);

  boolean existsSubmittedForCase(UUID caseId);
}
