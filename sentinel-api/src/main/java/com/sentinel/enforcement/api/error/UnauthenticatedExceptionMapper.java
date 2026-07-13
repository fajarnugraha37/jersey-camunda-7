package com.sentinel.enforcement.api.error;

import com.sentinel.enforcement.api.generated.model.ErrorResponse;
import com.sentinel.enforcement.application.security.UnauthenticatedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

@Provider
public final class UnauthenticatedExceptionMapper
    implements ExceptionMapper<UnauthenticatedException> {
  @Context private ContainerRequestContext requestContext;

  @Override
  public Response toResponse(UnauthenticatedException exception) {
    ErrorResponse error =
        ErrorResponseFactory.create(
            Response.Status.UNAUTHORIZED,
            "UNAUTHENTICATED",
            exception.getMessage(),
            ErrorResponseFactory.requestPath(requestContext),
            ErrorResponseFactory.correlationId(requestContext),
            List.of());
    return Response.status(Response.Status.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"sentinel\"")
        .entity(error)
        .build();
  }
}
