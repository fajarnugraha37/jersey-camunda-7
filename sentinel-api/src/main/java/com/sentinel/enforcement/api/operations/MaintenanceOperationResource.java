package com.sentinel.enforcement.api.operations;

import com.sentinel.enforcement.api.generated.model.MaintenanceOperationRunResponse;
import com.sentinel.enforcement.api.generated.model.RecalculateOverdueSanctionObligationsRequest;
import com.sentinel.enforcement.api.security.RequestActorResolver;
import com.sentinel.enforcement.api.security.RequestMetadataResolver;
import com.sentinel.enforcement.application.operations.MaintenanceOperationApplicationService;
import com.sentinel.enforcement.application.security.ApplicationActor;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/operations")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class MaintenanceOperationResource {
  private final MaintenanceOperationApplicationService maintenanceOperationApplicationService;
  private final ApiMaintenanceOperationMapper mapper = ApiMaintenanceOperationMapper.INSTANCE;

  @Inject
  public MaintenanceOperationResource(
      MaintenanceOperationApplicationService maintenanceOperationApplicationService) {
    this.maintenanceOperationApplicationService = maintenanceOperationApplicationService;
  }

  @POST
  @Path("/sanction-obligations/recalculate-overdue")
  public MaintenanceOperationRunResponse recalculateOverdueSanctionObligations(
      @Valid RecalculateOverdueSanctionObligationsRequest request,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    return mapper.toResponse(
        maintenanceOperationApplicationService.recalculateOverdueSanctionObligations(
            actor,
            mapper.toCommand(
                request,
                RequestMetadataResolver.correlationId(requestContext),
                RequestMetadataResolver.sourceIp(requestContext))));
  }
}
