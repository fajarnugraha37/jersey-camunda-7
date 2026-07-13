package com.sentinel.enforcement.api.error;

import com.sentinel.enforcement.api.generated.model.ErrorResponse;
import com.sentinel.enforcement.application.casefile.CaseNotFoundException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

@Provider
public final class CaseNotFoundExceptionMapper implements ExceptionMapper<CaseNotFoundException> {
  @Context private ContainerRequestContext requestContext;

  @Override
  public Response toResponse(CaseNotFoundException exception) {
    ErrorResponse error =
        ErrorResponseFactory.create(
            Response.Status.NOT_FOUND,
            "CASE_NOT_FOUND",
            exception.getMessage(),
            ErrorResponseFactory.requestPath(requestContext),
            ErrorResponseFactory.correlationId(requestContext),
            List.of());
    return Response.status(Response.Status.NOT_FOUND).entity(error).build();
  }
}
