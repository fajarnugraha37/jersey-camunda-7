package com.sentinel.enforcement.observability;

import java.util.Map;

public interface MetricsRecorder {

  void incrementCounter(String metricName, Map<String, String> tags);

  void recordTiming(String metricName, long durationMillis, Map<String, String> tags);
}
