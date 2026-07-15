package com.sentinel.enforcement.api.recommendation;

import com.sentinel.enforcement.api.generated.model.CreateRecommendationRequest;
import com.sentinel.enforcement.api.generated.model.RecommendationResponse;
import com.sentinel.enforcement.api.generated.model.RecommendationStatusValue;
import com.sentinel.enforcement.api.generated.model.ReviewRecommendationRequest;
import com.sentinel.enforcement.application.recommendation.CreateRecommendationCommand;
import com.sentinel.enforcement.application.recommendation.ReviewRecommendationCommand;
import com.sentinel.enforcement.domain.recommendation.Recommendation;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class ApiRecommendationMapper {
  public static final ApiRecommendationMapper INSTANCE = new ApiRecommendationMapper();

  private ApiRecommendationMapper() {}

  public CreateRecommendationCommand toCreateCommand(
      CreateRecommendationRequest request, String correlationId, String sourceIp) {
    if (request == null && correlationId == null && sourceIp == null) {
      return null;
    }
    return new CreateRecommendationCommand(
        request == null ? null : request.getTitle(),
        request == null ? null : request.getSummary(),
        request == null ? null : request.getProposedDecision(),
        request == null ? null : request.getProposedSanction(),
        correlationId,
        sourceIp);
  }

  public ReviewRecommendationCommand toReviewCommand(
      ReviewRecommendationRequest request, String correlationId, String sourceIp) {
    if (request == null && correlationId == null && sourceIp == null) {
      return null;
    }
    return new ReviewRecommendationCommand(
        request == null ? null : request.getReviewSummary(), correlationId, sourceIp);
  }

  public RecommendationResponse toResponse(Recommendation recommendation) {
    return new RecommendationResponse()
        .id(recommendation.id())
        .caseId(recommendation.caseId())
        .title(recommendation.title())
        .summary(recommendation.summary())
        .proposedDecision(recommendation.proposedDecision())
        .proposedSanction(recommendation.proposedSanction())
        .status(RecommendationStatusValue.fromValue(recommendation.status().name()))
        .submittedAt(toOffsetDateTime(recommendation.submittedAt()))
        .submittedBy(recommendation.submittedBy())
        .approvedReviewId(recommendation.approvedReviewId())
        .createdAt(toOffsetDateTime(recommendation.createdAt()))
        .createdBy(recommendation.createdBy())
        .updatedAt(toOffsetDateTime(recommendation.updatedAt()))
        .updatedBy(recommendation.updatedBy())
        .version(recommendation.version());
  }

  private OffsetDateTime toOffsetDateTime(Instant instant) {
    return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
