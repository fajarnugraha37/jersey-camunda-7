package com.sentinel.enforcement.api.error;

import com.sentinel.enforcement.api.generated.model.ErrorResponse;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public final class GenericExceptionMapper implements ExceptionMapper<Throwable> {
  private static final Logger LOGGER = LoggerFactory.getLogger(GenericExceptionMapper.class);

  @Context private ContainerRequestContext requestContext;

  @Override
  public Response toResponse(Throwable exception) {
    LOGGER.error("Unhandled error", exception);
    ErrorResponse error =
        ErrorResponseFactory.create(
            Response.Status.INTERNAL_SERVER_ERROR,
            "UNEXPECTED_ERROR",
            "Unexpected server error.",
            ErrorResponseFactory.requestPath(requestContext),
            ErrorResponseFactory.correlationId(requestContext),
            List.of());
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(error).build();
  }
}
