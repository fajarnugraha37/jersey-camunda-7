package com.sentinel.enforcement.bootstrap;

import java.sql.Connection;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

public final class LiquibaseMigrator {
  private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

  private LiquibaseMigrator() {}

  public static void migrate(DataSource dataSource) {
    String previousDuplicateFileMode = System.getProperty("liquibase.duplicateFileMode");
    try (Connection connection = dataSource.getConnection()) {
      System.setProperty("liquibase.duplicateFileMode", "ERROR");
      var database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(connection));
      try (var liquibase =
          new liquibase.Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
        liquibase.update(new Contexts(), new LabelExpression());
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Liquibase migration failed", exception);
    } finally {
      if (previousDuplicateFileMode == null) {
        System.clearProperty("liquibase.duplicateFileMode");
      } else {
        System.setProperty("liquibase.duplicateFileMode", previousDuplicateFileMode);
      }
    }
  }
}
