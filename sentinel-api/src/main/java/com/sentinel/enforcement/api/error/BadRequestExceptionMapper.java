package com.sentinel.enforcement.api.error;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

@Provider
public final class BadRequestExceptionMapper implements ExceptionMapper<BadRequestException> {
  @Context private ContainerRequestContext requestContext;

  @Override
  public Response toResponse(BadRequestException exception) {
    ErrorResponse error =
        ErrorResponseFactory.create(
            Response.Status.BAD_REQUEST,
            "MALFORMED_REQUEST",
            "Request could not be parsed.",
            requestContext.getUriInfo().getRequestUri().getPath(),
            ErrorResponseFactory.correlationId(requestContext),
            List.of());
    return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
  }
}
