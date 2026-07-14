package com.sentinel.enforcement.api.error;

import com.sentinel.enforcement.api.generated.model.ErrorResponse;
import com.sentinel.enforcement.application.evidence.EvidenceObjectMissingException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

@Provider
public final class EvidenceObjectMissingExceptionMapper
    implements ExceptionMapper<EvidenceObjectMissingException> {
  @Context private ContainerRequestContext requestContext;

  @Override
  public Response toResponse(EvidenceObjectMissingException exception) {
    ErrorResponse error =
        ErrorResponseFactory.create(
            Response.Status.CONFLICT,
            "EVIDENCE_OBJECT_NOT_FOUND",
            exception.getMessage(),
            ErrorResponseFactory.requestPath(requestContext),
            ErrorResponseFactory.correlationId(requestContext),
            List.of());
    return Response.status(Response.Status.CONFLICT).entity(error).build();
  }
}
