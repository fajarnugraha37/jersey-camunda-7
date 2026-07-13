package com.sentinel.enforcement.api.health;

import com.sentinel.enforcement.application.health.HealthStatus;
import com.sentinel.enforcement.application.health.HealthStatusService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public final class HealthResource {
  private final HealthStatusService healthStatusService;

  public HealthResource(HealthStatusService healthStatusService) {
    this.healthStatusService = healthStatusService;
  }

  @GET
  public Response getHealth() {
    HealthStatus status = healthStatusService.currentStatus();
    int httpStatus =
        status.healthy()
            ? Response.Status.OK.getStatusCode()
            : Response.Status.SERVICE_UNAVAILABLE.getStatusCode();
    return Response.status(httpStatus)
        .entity(
            new HealthResponse(
                status.healthy() ? "UP" : "DOWN", status.database(), status.timestamp()))
        .build();
  }
}
