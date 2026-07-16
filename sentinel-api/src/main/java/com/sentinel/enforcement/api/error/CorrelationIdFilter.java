package com.sentinel.enforcement.api.error;

import com.sentinel.enforcement.observability.CorrelationContext;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
@Priority(Priorities.AUTHENTICATION - 10)
public final class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {
  public static final String HEADER_NAME = "X-Correlation-Id";
  public static final String REQUEST_PROPERTY = "correlationId";

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String inbound = requestContext.getHeaderString(HEADER_NAME);
    String correlationId = CorrelationContext.sanitizeOrGenerate(inbound);
    requestContext.setProperty(REQUEST_PROPERTY, correlationId);
    CorrelationContext.bind(correlationId);
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {
    Object correlationId = requestContext.getProperty(REQUEST_PROPERTY);
    if (correlationId != null) {
      responseContext.getHeaders().putSingle(HEADER_NAME, correlationId.toString());
    }
    CorrelationContext.clear();
  }
}
