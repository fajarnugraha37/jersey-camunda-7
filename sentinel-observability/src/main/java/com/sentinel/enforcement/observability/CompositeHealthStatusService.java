package com.sentinel.enforcement.observability;

import com.sentinel.enforcement.application.health.HealthStatus;
import com.sentinel.enforcement.application.health.HealthStatusService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompositeHealthStatusService implements HealthStatusService {
  private final List<DependencyHealthCheck> dependencyHealthChecks;
  private final Clock clock;

  public CompositeHealthStatusService(
      List<DependencyHealthCheck> dependencyHealthChecks, Clock clock) {
    this.dependencyHealthChecks = List.copyOf(dependencyHealthChecks);
    this.clock = clock;
  }

  @Override
  public HealthStatus currentStatus() {
    Map<String, String> dependencies = new LinkedHashMap<>();
    boolean healthy = true;
    for (DependencyHealthCheck dependencyHealthCheck : dependencyHealthChecks) {
      DependencyHealth dependencyHealth = dependencyHealthCheck.check();
      dependencies.put(dependencyHealth.name(), dependencyHealth.status());
      healthy = healthy && "UP".equals(dependencyHealth.status());
    }
    Instant timestamp = clock.instant();
    return new HealthStatus(
        healthy, dependencies.getOrDefault("database", "DOWN"), dependencies, timestamp);
  }
}
