package com.sentinel.enforcement.api.security;

import com.sentinel.enforcement.api.error.ErrorResponseFactory;
import jakarta.ws.rs.container.ContainerRequestContext;

public final class RequestMetadataResolver {
  private RequestMetadataResolver() {}

  public static String correlationId(ContainerRequestContext requestContext) {
    return ErrorResponseFactory.correlationId(requestContext);
  }

  public static String sourceIp(ContainerRequestContext requestContext) {
    String forwardedFor = requestContext.getHeaderString("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    String realIp = requestContext.getHeaderString("X-Real-IP");
    return realIp == null || realIp.isBlank() ? null : realIp;
  }
}
