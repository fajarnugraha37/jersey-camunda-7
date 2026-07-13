package com.sentinel.enforcement.bootstrap;

import com.sentinel.enforcement.application.health.HealthStatus;
import com.sentinel.enforcement.application.health.HealthStatusService;
import java.sql.SQLException;
import java.time.Clock;
import javax.sql.DataSource;

public final class DatabaseHealthService implements HealthStatusService {
  private final DataSource dataSource;
  private final Clock clock;

  public DatabaseHealthService(DataSource dataSource, Clock clock) {
    this.dataSource = dataSource;
    this.clock = clock;
  }

  @Override
  public HealthStatus currentStatus() {
    try (var connection = dataSource.getConnection()) {
      boolean healthy = connection.isValid(2);
      return new HealthStatus(healthy, healthy ? "UP" : "DOWN", clock.instant());
    } catch (SQLException exception) {
      return new HealthStatus(false, "DOWN", clock.instant());
    }
  }
}
