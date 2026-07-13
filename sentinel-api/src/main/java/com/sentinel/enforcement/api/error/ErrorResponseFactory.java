package com.sentinel.enforcement.api.error;

import com.sentinel.enforcement.api.generated.model.ErrorResponse;
import com.sentinel.enforcement.api.generated.model.Violation;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import java.util.List;

public final class ErrorResponseFactory {
  private ErrorResponseFactory() {}

  public static ErrorResponse create(
      Response.Status status,
      String code,
      String detail,
      String instance,
      String correlationId,
      List<Violation> violations) {
    return new ErrorResponse()
        .type("https://sentinel.local/errors/" + code.toLowerCase().replace('_', '-'))
        .title(status.getReasonPhrase())
        .status(status.getStatusCode())
        .code(code)
        .detail(detail)
        .instance(instance)
        .correlationId(correlationId)
        .violations(violations);
  }

  public static String correlationId(ContainerRequestContext requestContext) {
    if (requestContext == null) {
      return "unknown";
    }
    try {
      Object correlationId = requestContext.getProperty(CorrelationIdFilter.REQUEST_PROPERTY);
      return correlationId == null ? "unknown" : correlationId.toString();
    } catch (RuntimeException exception) {
      return "unknown";
    }
  }

  public static String requestPath(ContainerRequestContext requestContext) {
    if (requestContext == null) {
      return "unknown";
    }
    try {
      return requestContext.getUriInfo().getRequestUri().getPath();
    } catch (RuntimeException exception) {
      return "unknown";
    }
  }
}
