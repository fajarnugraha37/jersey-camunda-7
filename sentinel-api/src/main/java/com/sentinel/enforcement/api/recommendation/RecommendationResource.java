package com.sentinel.enforcement.api.recommendation;

import com.sentinel.enforcement.api.generated.model.RecommendationResponse;
import com.sentinel.enforcement.api.generated.model.ReviewRecommendationRequest;
import com.sentinel.enforcement.api.security.RequestActorResolver;
import com.sentinel.enforcement.api.security.RequestMetadataResolver;
import com.sentinel.enforcement.application.recommendation.RecommendationApplicationService;
import com.sentinel.enforcement.application.recommendation.SubmitRecommendationCommand;
import com.sentinel.enforcement.application.security.ApplicationActor;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/api/v1/recommendations")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class RecommendationResource {
  private final RecommendationApplicationService recommendationApplicationService;
  private final ApiRecommendationMapper mapper = ApiRecommendationMapper.INSTANCE;

  @Inject
  public RecommendationResource(RecommendationApplicationService recommendationApplicationService) {
    this.recommendationApplicationService = recommendationApplicationService;
  }

  @POST
  @Path("/{recommendationId}/submit")
  public RecommendationResponse submitRecommendation(
      @PathParam("recommendationId") UUID recommendationId,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    return mapper.toResponse(
        recommendationApplicationService.submitRecommendation(
            actor,
            recommendationId,
            new SubmitRecommendationCommand(
                RequestMetadataResolver.correlationId(requestContext),
                RequestMetadataResolver.sourceIp(requestContext))));
  }

  @POST
  @Path("/{recommendationId}/reviews")
  public RecommendationResponse approveRecommendation(
      @PathParam("recommendationId") UUID recommendationId,
      @Valid ReviewRecommendationRequest request,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    return mapper.toResponse(
        recommendationApplicationService.approveRecommendation(
            actor,
            recommendationId,
            mapper.toReviewCommand(
                request,
                RequestMetadataResolver.correlationId(requestContext),
                RequestMetadataResolver.sourceIp(requestContext))));
  }
}
