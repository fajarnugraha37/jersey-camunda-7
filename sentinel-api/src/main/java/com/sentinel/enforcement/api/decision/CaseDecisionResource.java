package com.sentinel.enforcement.api.decision;

import com.sentinel.enforcement.api.generated.model.CreateDecisionRequest;
import com.sentinel.enforcement.api.generated.model.DecisionResponse;
import com.sentinel.enforcement.api.security.RequestActorResolver;
import com.sentinel.enforcement.api.security.RequestMetadataResolver;
import com.sentinel.enforcement.application.decision.DecisionApplicationService;
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

@Path("/api/v1/cases/{caseId}/decisions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class CaseDecisionResource {
  private final DecisionApplicationService decisionApplicationService;
  private final ApiDecisionMapper decisionMapper = ApiDecisionMapper.INSTANCE;

  @Inject
  public CaseDecisionResource(DecisionApplicationService decisionApplicationService) {
    this.decisionApplicationService = decisionApplicationService;
  }

  @POST
  public Response createDecision(
      @PathParam("caseId") UUID caseId,
      @Valid CreateDecisionRequest request,
      @Context UriInfo uriInfo,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    DecisionResponse response =
        decisionMapper.toResponse(
            decisionApplicationService.createDecision(
                actor,
                caseId,
                decisionMapper.toCreateCommand(
                    request,
                    RequestMetadataResolver.correlationId(requestContext),
                    RequestMetadataResolver.sourceIp(requestContext))));
    return Response.created(
            uriInfo.getBaseUriBuilder().path("/api/v1/decisions/" + response.getId()).build())
        .entity(response)
        .build();
  }
}
