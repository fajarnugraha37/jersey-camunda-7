package com.sentinel.enforcement.api.recommendation;

import com.sentinel.enforcement.api.generated.model.CreateRecommendationRequest;
import com.sentinel.enforcement.api.generated.model.RecommendationResponse;
import com.sentinel.enforcement.api.security.RequestActorResolver;
import com.sentinel.enforcement.api.security.RequestMetadataResolver;
import com.sentinel.enforcement.application.recommendation.RecommendationApplicationService;
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
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.UUID;

@Path("/api/v1/cases/{caseId}/recommendations")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class CaseRecommendationResource {
  private final RecommendationApplicationService recommendationApplicationService;
  private final ApiRecommendationMapper mapper = ApiRecommendationMapper.INSTANCE;

  @Inject
  public CaseRecommendationResource(
      RecommendationApplicationService recommendationApplicationService) {
    this.recommendationApplicationService = recommendationApplicationService;
  }

  @POST
  public Response createRecommendation(
      @PathParam("caseId") UUID caseId,
      @Valid CreateRecommendationRequest request,
      @Context UriInfo uriInfo,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    RecommendationResponse response =
        mapper.toResponse(
            recommendationApplicationService.createRecommendation(
                actor,
                caseId,
                mapper.toCreateCommand(
                    request,
                    RequestMetadataResolver.correlationId(requestContext),
                    RequestMetadataResolver.sourceIp(requestContext))));
    return Response.created(
            uriInfo.getBaseUriBuilder().path("/api/v1/recommendations/" + response.getId()).build())
        .entity(response)
        .build();
  }
}
