package com.sentinel.enforcement.api.error;

import com.sentinel.enforcement.api.generated.model.ErrorResponse;
import com.sentinel.enforcement.application.evidence.EvidenceStorageUnavailableException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

@Provider
public final class EvidenceStorageUnavailableExceptionMapper
    implements ExceptionMapper<EvidenceStorageUnavailableException> {
  @Context private ContainerRequestContext requestContext;

  @Override
  public Response toResponse(EvidenceStorageUnavailableException exception) {
    ErrorResponse error =
        ErrorResponseFactory.create(
            Response.Status.SERVICE_UNAVAILABLE,
            "EVIDENCE_STORAGE_UNAVAILABLE",
            exception.getMessage(),
            ErrorResponseFactory.requestPath(requestContext),
            ErrorResponseFactory.correlationId(requestContext),
            List.of());
    return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(error).build();
  }
}
