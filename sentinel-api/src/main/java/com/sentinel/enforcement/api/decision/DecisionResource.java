package com.sentinel.enforcement.api.decision;

import com.sentinel.enforcement.api.generated.model.CreateAppealRequest;
import com.sentinel.enforcement.api.generated.model.DecisionResponse;
import com.sentinel.enforcement.api.generated.model.AppealResponse;
import com.sentinel.enforcement.api.appeal.ApiAppealMapper;
import com.sentinel.enforcement.api.security.RequestActorResolver;
import com.sentinel.enforcement.api.security.RequestMetadataResolver;
import com.sentinel.enforcement.application.appeal.AppealApplicationService;
import com.sentinel.enforcement.application.appeal.CreateAppealCommand;
import com.sentinel.enforcement.application.decision.ApproveDecisionCommand;
import com.sentinel.enforcement.application.decision.DecisionApplicationService;
import com.sentinel.enforcement.application.decision.PublishDecisionCommand;
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

@Path("/api/v1/decisions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class DecisionResource {
  private final DecisionApplicationService decisionApplicationService;
  private final AppealApplicationService appealApplicationService;
  private final ApiAppealMapper appealMapper = ApiAppealMapper.INSTANCE;

  @Inject
  public DecisionResource(
      DecisionApplicationService decisionApplicationService,
      AppealApplicationService appealApplicationService) {
    this.decisionApplicationService = decisionApplicationService;
    this.appealApplicationService = appealApplicationService;
  }

  @POST
  @Path("/{decisionId}/approve")
  public DecisionResponse approveDecision(
      @PathParam("decisionId") UUID decisionId, @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    return ApiDecisionMapper.INSTANCE.toResponse(
        decisionApplicationService.approveDecision(
            actor,
            decisionId,
            new ApproveDecisionCommand(
                RequestMetadataResolver.correlationId(requestContext),
                RequestMetadataResolver.sourceIp(requestContext))));
  }

  @POST
  @Path("/{decisionId}/publish")
  public DecisionResponse publishDecision(
      @PathParam("decisionId") UUID decisionId, @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    return ApiDecisionMapper.INSTANCE.toResponse(
        decisionApplicationService.publishDecision(
            actor,
            decisionId,
            new PublishDecisionCommand(
                RequestMetadataResolver.correlationId(requestContext),
                RequestMetadataResolver.sourceIp(requestContext))));
  }

  @POST
  @Path("/{decisionId}/appeals")
  public Response createAppeal(
      @PathParam("decisionId") UUID decisionId,
      @Valid CreateAppealRequest request,
      @Context UriInfo uriInfo,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    AppealResponse response =
        appealMapper.toResponse(
            appealApplicationService.createAppeal(
                actor,
                decisionId,
                new CreateAppealCommand(
                    request.getRationale(),
                    request.getSubmittedAt().toInstant(),
                    Boolean.TRUE.equals(request.getSupervisorOverride()),
                    request.getSupervisorOverrideReason(),
                    RequestMetadataResolver.correlationId(requestContext),
                    RequestMetadataResolver.sourceIp(requestContext))));
    return Response.created(
            uriInfo.getBaseUriBuilder().path("/api/v1/appeals/" + response.getId()).build())
        .entity(response)
        .build();
  }
}
