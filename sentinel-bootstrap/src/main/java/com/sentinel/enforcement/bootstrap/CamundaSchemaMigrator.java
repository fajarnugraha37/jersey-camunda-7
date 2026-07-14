package com.sentinel.enforcement.bootstrap;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.jdbc.ScriptRunner;

public final class CamundaSchemaMigrator {
  private static final List<String> POSTGRES_CREATE_RESOURCES =
      List.of(
          "org/camunda/bpm/engine/db/create/activiti.postgres.create.engine.sql",
          "org/camunda/bpm/engine/db/create/activiti.postgres.create.history.sql",
          "org/camunda/bpm/engine/db/create/activiti.postgres.create.identity.sql",
          "org/camunda/bpm/engine/db/create/activiti.postgres.create.case.engine.sql",
          "org/camunda/bpm/engine/db/create/activiti.postgres.create.case.history.sql",
          "org/camunda/bpm/engine/db/create/activiti.postgres.create.decision.engine.sql",
          "org/camunda/bpm/engine/db/create/activiti.postgres.create.decision.history.sql");
  private static final String ACT_GE_PROPERTY = "ACT_GE_PROPERTY";

  private CamundaSchemaMigrator() {}

  public static void migrate(DataSource dataSource) {
    try (Connection connection = dataSource.getConnection()) {
      if (tableExists(connection, ACT_GE_PROPERTY)) {
        return;
      }

      boolean previousAutoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try {
        ScriptRunner scriptRunner = new ScriptRunner(connection);
        scriptRunner.setStopOnError(true);
        scriptRunner.setLogWriter(null);
        scriptRunner.setErrorLogWriter(null);
        for (String resource : POSTGRES_CREATE_RESOURCES) {
          runScript(scriptRunner, resource);
        }
        connection.commit();
      } catch (RuntimeException exception) {
        connection.rollback();
        throw exception;
      } finally {
        connection.setAutoCommit(previousAutoCommit);
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Camunda schema migration failed.", exception);
    }
  }

  private static void runScript(ScriptRunner scriptRunner, String resource) {
    try (InputStream inputStream =
            CamundaSchemaMigrator.class.getClassLoader().getResourceAsStream(resource);
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      if (inputStream == null) {
        throw new IllegalStateException("Missing Camunda schema resource: " + resource);
      }
      scriptRunner.runScript(reader);
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Failed to execute Camunda schema resource: " + resource, exception);
    }
  }

  private static boolean tableExists(Connection connection, String tableName) throws SQLException {
    try (ResultSet resultSet =
        connection.getMetaData().getTables(null, null, tableName.toLowerCase(), null)) {
      return resultSet.next();
    }
  }
}
