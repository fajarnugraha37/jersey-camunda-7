package com.sentinel.enforcement.observability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryMetricsRecorder implements MetricsRecorder {
  private final List<MetricSample> samples = new CopyOnWriteArrayList<>();

  @Override
  public void incrementCounter(String metricName, Map<String, String> tags) {
    samples.add(new MetricSample(metricName, 1L, tags));
  }

  @Override
  public void recordTiming(String metricName, long durationMillis, Map<String, String> tags) {
    samples.add(new MetricSample(metricName, durationMillis, tags));
  }

  public List<MetricSample> snapshot() {
    return new ArrayList<>(samples);
  }

  public record MetricSample(String metricName, long value, Map<String, String> tags) {}
}
