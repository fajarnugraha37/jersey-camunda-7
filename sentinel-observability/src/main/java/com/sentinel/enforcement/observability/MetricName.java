package com.sentinel.enforcement.observability;

public final class MetricName {
  public static final String HTTP_REQUEST_TOTAL = "http.request.total";
  public static final String HTTP_REQUEST_ERROR_TOTAL = "http.request.error.total";
  public static final String HTTP_REQUEST_DURATION_MS = "http.request.duration.ms";

  private MetricName() {}
}
