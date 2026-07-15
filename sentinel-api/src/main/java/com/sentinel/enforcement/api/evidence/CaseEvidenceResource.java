package com.sentinel.enforcement.api.evidence;

import com.sentinel.enforcement.api.generated.model.CreateEvidenceUploadSessionRequest;
import com.sentinel.enforcement.api.generated.model.CreateEvidenceUploadSessionResponse;
import com.sentinel.enforcement.api.security.RequestActorResolver;
import com.sentinel.enforcement.api.security.RequestMetadataResolver;
import com.sentinel.enforcement.application.evidence.EvidenceApplicationService;
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
import java.util.UUID;

@Path("/api/v1/cases/{caseId}/evidence")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class CaseEvidenceResource {
  private final EvidenceApplicationService evidenceApplicationService;
  private final ApiEvidenceMapper mapper = ApiEvidenceMapper.INSTANCE;

  @Inject
  public CaseEvidenceResource(EvidenceApplicationService evidenceApplicationService) {
    this.evidenceApplicationService = evidenceApplicationService;
  }

  @POST
  @Path("/upload-sessions")
  public Response createUploadSession(
      @PathParam("caseId") UUID caseId,
      @Valid CreateEvidenceUploadSessionRequest request,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    CreateEvidenceUploadSessionResponse response =
        mapper.toCreateUploadSessionResponse(
            evidenceApplicationService.createUploadSession(
                actor,
                caseId,
                mapper.toCreateUploadSessionCommand(
                    request,
                    RequestMetadataResolver.correlationId(requestContext),
                    RequestMetadataResolver.sourceIp(requestContext))));
    return Response.status(Response.Status.CREATED).entity(response).build();
  }
}
