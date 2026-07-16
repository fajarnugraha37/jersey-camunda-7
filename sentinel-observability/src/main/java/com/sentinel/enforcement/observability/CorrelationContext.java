package com.sentinel.enforcement.observability;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

public final class CorrelationContext {
  public static final String MDC_KEY = "correlationId";
  private static final Pattern SAFE_VALUE = Pattern.compile("^[A-Za-z0-9\\-]{1,100}$");

  private CorrelationContext() {}

  public static String sanitizeOrGenerate(String candidate) {
    if (candidate != null && SAFE_VALUE.matcher(candidate).matches()) {
      return candidate;
    }
    return UUID.randomUUID().toString();
  }

  public static void bind(String correlationId) {
    MDC.put(MDC_KEY, correlationId);
  }

  public static void clear() {
    MDC.remove(MDC_KEY);
  }
}
