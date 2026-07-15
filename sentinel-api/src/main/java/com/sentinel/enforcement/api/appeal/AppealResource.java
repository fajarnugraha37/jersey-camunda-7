package com.sentinel.enforcement.api.appeal;

import com.sentinel.enforcement.api.generated.model.AppealResponse;
import com.sentinel.enforcement.api.generated.model.DecideAppealRequest;
import com.sentinel.enforcement.api.security.RequestActorResolver;
import com.sentinel.enforcement.api.security.RequestMetadataResolver;
import com.sentinel.enforcement.application.appeal.AppealApplicationService;
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

@Path("/api/v1/appeals")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class AppealResource {
  private final AppealApplicationService appealApplicationService;
  private final ApiAppealMapper mapper = ApiAppealMapper.INSTANCE;

  @Inject
  public AppealResource(AppealApplicationService appealApplicationService) {
    this.appealApplicationService = appealApplicationService;
  }

  @POST
  @Path("/{appealId}/decisions")
  public AppealResponse decideAppeal(
      @PathParam("appealId") UUID appealId,
      @Valid DecideAppealRequest request,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    return mapper.toResponse(
        appealApplicationService.decideAppeal(
            actor,
            appealId,
            mapper.toDecideCommand(
                request,
                RequestMetadataResolver.correlationId(requestContext),
                RequestMetadataResolver.sourceIp(requestContext))));
  }
}
