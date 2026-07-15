package com.sentinel.enforcement.api.evidence;

import com.sentinel.enforcement.api.generated.model.CreateEvidenceDownloadSessionRequest;
import com.sentinel.enforcement.api.generated.model.CreateEvidenceDownloadSessionResponse;
import com.sentinel.enforcement.api.generated.model.EvidenceResponse;
import com.sentinel.enforcement.api.generated.model.FinalizeEvidenceVersionRequest;
import com.sentinel.enforcement.api.security.RequestActorResolver;
import com.sentinel.enforcement.api.security.RequestMetadataResolver;
import com.sentinel.enforcement.application.evidence.EvidenceApplicationService;
import com.sentinel.enforcement.application.security.ApplicationActor;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

@Path("/api/v1/evidence")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class EvidenceResource {
  private final EvidenceApplicationService evidenceApplicationService;
  private final ApiEvidenceMapper mapper = ApiEvidenceMapper.INSTANCE;

  @Inject
  public EvidenceResource(EvidenceApplicationService evidenceApplicationService) {
    this.evidenceApplicationService = evidenceApplicationService;
  }

  @POST
  @Path("/{evidenceId}/versions/finalize")
  public EvidenceResponse finalizeEvidenceVersion(
      @jakarta.ws.rs.PathParam("evidenceId") UUID evidenceId,
      @Valid FinalizeEvidenceVersionRequest request,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    return mapper.toResponse(
        evidenceApplicationService.finalizeEvidenceVersion(
            actor,
            evidenceId,
            mapper.toFinalizeCommand(
                request,
                RequestMetadataResolver.correlationId(requestContext),
                RequestMetadataResolver.sourceIp(requestContext))));
  }

  @GET
  @Path("/{evidenceId}")
  public EvidenceResponse getEvidence(
      @jakarta.ws.rs.PathParam("evidenceId") UUID evidenceId,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    return mapper.toResponse(evidenceApplicationService.getEvidence(actor, evidenceId));
  }

  @POST
  @Path("/{evidenceId}/download-sessions")
  public Response createDownloadSession(
      @jakarta.ws.rs.PathParam("evidenceId") UUID evidenceId,
      @Valid CreateEvidenceDownloadSessionRequest request,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    CreateEvidenceDownloadSessionResponse response =
        mapper.toCreateDownloadSessionResponse(
            evidenceApplicationService.createDownloadSession(
                actor,
                evidenceId,
                mapper.toCreateDownloadSessionCommand(
                    request,
                    RequestMetadataResolver.correlationId(requestContext),
                    RequestMetadataResolver.sourceIp(requestContext))));
    return Response.status(Response.Status.CREATED).entity(response).build();
  }
}
