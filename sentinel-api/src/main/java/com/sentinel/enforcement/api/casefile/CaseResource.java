package com.sentinel.enforcement.api.casefile;

import com.sentinel.enforcement.api.generated.model.AssignCaseRequest;
import com.sentinel.enforcement.api.generated.model.CaseAuditEventListResponse;
import com.sentinel.enforcement.api.generated.model.CaseListResponse;
import com.sentinel.enforcement.api.generated.model.CaseResponse;
import com.sentinel.enforcement.api.generated.model.CreateCaseRequest;
import com.sentinel.enforcement.api.generated.model.TransitionCaseRequest;
import com.sentinel.enforcement.api.security.RequestActorResolver;
import com.sentinel.enforcement.api.security.RequestMetadataResolver;
import com.sentinel.enforcement.application.casefile.CaseApplicationService;
import com.sentinel.enforcement.application.casefile.CasePage;
import com.sentinel.enforcement.application.casefile.ListCasesQuery;
import com.sentinel.enforcement.application.security.ApplicationActor;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.UUID;

@Path("/api/v1/cases")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class CaseResource {
  private final CaseApplicationService caseApplicationService;
  private final ApiCaseMapper mapper = ApiCaseMapper.INSTANCE;

  @Inject
  public CaseResource(CaseApplicationService caseApplicationService) {
    this.caseApplicationService = caseApplicationService;
  }

  @POST
  public Response createCase(
      @Valid CreateCaseRequest request,
      @Context UriInfo uriInfo,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    CaseResponse response =
        mapper.toResponse(
            caseApplicationService.createCase(
                actor,
                mapper.toCreateCaseCommand(
                    request,
                    RequestMetadataResolver.correlationId(requestContext),
                    RequestMetadataResolver.sourceIp(requestContext))));
    return Response.created(
            uriInfo.getAbsolutePathBuilder().path(response.getId().toString()).build())
        .entity(response)
        .build();
  }

  @GET
  public CaseListResponse listCases(
      @QueryParam("cursor") String cursor,
      @DefaultValue("20") @QueryParam("limit") @Min(1) @Max(50) int limit,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    ListCasesQuery query = CaseCursorCodec.decode(cursor, limit);
    CasePage casePage = caseApplicationService.listCases(actor, query);
    return mapper.toListResponse(casePage);
  }

  @GET
  @Path("/{caseId}")
  public CaseResponse getCase(
      @PathParam("caseId") UUID caseId, @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    return mapper.toResponse(caseApplicationService.getCase(actor, caseId));
  }

  @POST
  @Path("/{caseId}/assignments")
  public CaseResponse assignCase(
      @PathParam("caseId") UUID caseId,
      @Valid AssignCaseRequest request,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    return mapper.toResponse(
        caseApplicationService.assignCase(
            actor,
            caseId,
            mapper.toAssignCaseCommand(
                request,
                RequestMetadataResolver.correlationId(requestContext),
                RequestMetadataResolver.sourceIp(requestContext))));
  }

  @POST
  @Path("/{caseId}/transitions")
  public CaseResponse transitionCase(
      @PathParam("caseId") UUID caseId,
      @Valid TransitionCaseRequest request,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    return mapper.toResponse(
        caseApplicationService.transitionCase(
            actor,
            caseId,
            mapper.toTransitionCaseCommand(
                request,
                RequestMetadataResolver.correlationId(requestContext),
                RequestMetadataResolver.sourceIp(requestContext))));
  }

  @GET
  @Path("/{caseId}/audit-events")
  public CaseAuditEventListResponse getCaseAuditEvents(
      @PathParam("caseId") UUID caseId,
      @DefaultValue("50") @QueryParam("limit") @Min(1) @Max(100) int limit,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    return mapper.toAuditListResponse(
        caseApplicationService.getCaseAuditEvents(actor, caseId, limit));
  }
}
