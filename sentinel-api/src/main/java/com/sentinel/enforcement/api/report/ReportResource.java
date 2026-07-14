package com.sentinel.enforcement.api.report;

import com.sentinel.enforcement.api.generated.model.CreateReportRequest;
import com.sentinel.enforcement.api.generated.model.ReportResponse;
import com.sentinel.enforcement.api.generated.model.TriageReportRequest;
import com.sentinel.enforcement.api.security.RequestMetadataResolver;
import com.sentinel.enforcement.api.security.RequestActorResolver;
import com.sentinel.enforcement.application.report.ReportApplicationService;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.domain.report.Report;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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

@Path("/api/v1/reports")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class ReportResource {
  private final ReportApplicationService reportApplicationService;
  private final ApiReportMapper mapper = ApiReportMapper.INSTANCE;

  @Inject
  public ReportResource(ReportApplicationService reportApplicationService) {
    this.reportApplicationService = reportApplicationService;
  }

  @POST
  public Response createReport(
      @Valid CreateReportRequest request,
      @Context UriInfo uriInfo,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    Report report = reportApplicationService.createReport(actor, mapper.toCommand(request));
    return Response.created(uriInfo.getAbsolutePathBuilder().path(report.id().toString()).build())
        .entity(mapper.toResponse(report))
        .build();
  }

  @GET
  @Path("/{reportId}")
  public ReportResponse getReport(
      @PathParam("reportId") UUID reportId, @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    return mapper.toResponse(reportApplicationService.getReport(actor, reportId));
  }

  @POST
  @Path("/{reportId}/triage")
  public ReportResponse triageReport(
      @PathParam("reportId") UUID reportId,
      @Valid TriageReportRequest request,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    return mapper.toResponse(
        reportApplicationService.triageReport(
            actor,
            reportId,
            mapper.toTriageCommand(
                request,
                RequestMetadataResolver.correlationId(requestContext),
                RequestMetadataResolver.sourceIp(requestContext))));
  }
}
