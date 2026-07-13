package com.sentinel.enforcement.api.error;

import com.sentinel.enforcement.api.generated.model.ErrorResponse;
import com.sentinel.enforcement.api.generated.model.Violation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Comparator;
import java.util.List;

@Provider
public final class ConstraintViolationExceptionMapper
    implements ExceptionMapper<ConstraintViolationException> {
  @Context private ContainerRequestContext requestContext;

  @Override
  public Response toResponse(ConstraintViolationException exception) {
    List<Violation> violations =
        exception.getConstraintViolations().stream()
            .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
            .map(this::toViolation)
            .toList();

    ErrorResponse error =
        ErrorResponseFactory.create(
            Response.Status.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Request validation failed.",
            requestContext.getUriInfo().getRequestUri().getPath(),
            ErrorResponseFactory.correlationId(requestContext),
            violations);
    return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
  }

  private Violation toViolation(ConstraintViolation<?> violation) {
    return new Violation()
        .field(violation.getPropertyPath().toString())
        .message(violation.getMessage());
  }
}
