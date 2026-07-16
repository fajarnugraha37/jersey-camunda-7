package com.sentinel.enforcement.observability;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Map;

@Provider
@Priority(Priorities.USER)
public final class RequestMetricsFilter implements ContainerRequestFilter, ContainerResponseFilter {
  private static final String START_TIME_PROPERTY = "requestStartNanos";
  private final MetricsRecorder metricsRecorder;

  public RequestMetricsFilter(MetricsRecorder metricsRecorder) {
    this.metricsRecorder = metricsRecorder;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    requestContext.setProperty(START_TIME_PROPERTY, System.nanoTime());
    metricsRecorder.incrementCounter(
        MetricName.HTTP_REQUEST_TOTAL,
        Map.of(
            "method", requestContext.getMethod(), "path", requestContext.getUriInfo().getPath()));
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {
    Object startedAt = requestContext.getProperty(START_TIME_PROPERTY);
    if (!(startedAt instanceof Long startNanos)) {
      return;
    }
    long durationMillis = (System.nanoTime() - startNanos) / 1_000_000L;
    Map<String, String> tags =
        Map.of(
            "method", requestContext.getMethod(),
            "path", requestContext.getUriInfo().getPath(),
            "status", Integer.toString(responseContext.getStatus()));
    metricsRecorder.recordTiming(MetricName.HTTP_REQUEST_DURATION_MS, durationMillis, tags);
    if (responseContext.getStatus() >= 400) {
      metricsRecorder.incrementCounter(MetricName.HTTP_REQUEST_ERROR_TOTAL, tags);
    }
  }
}
