package com.sentinel.enforcement.api.error;

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
      List<ViolationResponse> violations) {
    return new ErrorResponse(
        "https://sentinel.local/errors/" + code.toLowerCase().replace('_', '-'),
        status.getReasonPhrase(),
        status.getStatusCode(),
        code,
        detail,
        instance,
        correlationId,
        violations);
  }

  public static String correlationId(ContainerRequestContext requestContext) {
    Object correlationId = requestContext.getProperty(CorrelationIdFilter.REQUEST_PROPERTY);
    return correlationId == null ? "unknown" : correlationId.toString();
  }
}
