package com.sentinel.enforcement.api.error;

import com.sentinel.enforcement.api.generated.model.ErrorResponse;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

@Provider
public final class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {
  @Context private ContainerRequestContext requestContext;

  @Override
  public Response toResponse(NotFoundException exception) {
    ErrorResponse error =
        ErrorResponseFactory.create(
            Response.Status.NOT_FOUND,
            "RESOURCE_NOT_FOUND",
            "Requested resource was not found.",
            ErrorResponseFactory.requestPath(requestContext),
            ErrorResponseFactory.correlationId(requestContext),
            List.of());
    return Response.status(Response.Status.NOT_FOUND).entity(error).build();
  }
}
