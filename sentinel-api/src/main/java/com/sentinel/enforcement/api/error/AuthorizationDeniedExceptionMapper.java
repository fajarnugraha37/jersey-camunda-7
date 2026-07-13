package com.sentinel.enforcement.api.error;

import com.sentinel.enforcement.api.generated.model.ErrorResponse;
import com.sentinel.enforcement.application.security.AuthorizationDeniedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

@Provider
public final class AuthorizationDeniedExceptionMapper
    implements ExceptionMapper<AuthorizationDeniedException> {
  @Context private ContainerRequestContext requestContext;

  @Override
  public Response toResponse(AuthorizationDeniedException exception) {
    ErrorResponse error =
        ErrorResponseFactory.create(
            Response.Status.FORBIDDEN,
            "FORBIDDEN",
            exception.getMessage(),
            ErrorResponseFactory.requestPath(requestContext),
            ErrorResponseFactory.correlationId(requestContext),
            List.of());
    return Response.status(Response.Status.FORBIDDEN).entity(error).build();
  }
}
