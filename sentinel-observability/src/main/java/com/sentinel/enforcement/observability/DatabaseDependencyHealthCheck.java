package com.sentinel.enforcement.observability;

import java.sql.SQLException;
import javax.sql.DataSource;

public final class DatabaseDependencyHealthCheck implements DependencyHealthCheck {
  private final DataSource dataSource;

  public DatabaseDependencyHealthCheck(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public DependencyHealth check() {
    try (var connection = dataSource.getConnection()) {
      boolean healthy = connection.isValid(2);
      return new DependencyHealth("database", healthy ? "UP" : "DOWN");
    } catch (SQLException exception) {
      return new DependencyHealth("database", "DOWN");
    }
  }
}
