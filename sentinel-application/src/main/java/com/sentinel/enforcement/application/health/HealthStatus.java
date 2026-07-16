package com.sentinel.enforcement.application.health;

import java.time.Instant;
import java.util.Map;

public record HealthStatus(
    boolean healthy, String database, Map<String, String> dependencies, Instant timestamp) {

  public HealthStatus {
    dependencies = Map.copyOf(dependencies);
  }
}
