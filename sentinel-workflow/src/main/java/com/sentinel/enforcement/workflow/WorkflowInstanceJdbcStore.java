package com.sentinel.enforcement.workflow;

import com.sentinel.enforcement.application.workflow.StartedWorkflowInstance;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

final class WorkflowInstanceJdbcStore {
  private static final String INSERT_SQL =
      """
      INSERT INTO workflow_instance (
          case_id,
          process_instance_id,
          process_definition_id,
          process_definition_version,
          business_key,
          status,
          created_at,
          updated_at
      ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
      ON CONFLICT (case_id) DO UPDATE
      SET process_instance_id = EXCLUDED.process_instance_id,
          process_definition_id = EXCLUDED.process_definition_id,
          process_definition_version = EXCLUDED.process_definition_version,
          business_key = EXCLUDED.business_key,
          status = 'ACTIVE',
          updated_at = EXCLUDED.updated_at
      """;

  private static final String FIND_BY_CASE_ID_SQL =
      """
      SELECT case_id, process_instance_id, process_definition_id, process_definition_version, business_key, status
      FROM workflow_instance
      WHERE case_id = ?
      """;

  private static final String MARK_COMPLETED_SQL =
      """
      UPDATE workflow_instance
      SET status = 'COMPLETED',
          updated_at = ?
      WHERE process_instance_id = ?
      """;

  private static final String MARK_CANCELLED_SQL =
      """
      UPDATE workflow_instance
      SET status = 'CANCELLED',
          updated_at = ?
      WHERE case_id = ?
      """;

  private final DataSource dataSource;

  WorkflowInstanceJdbcStore(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  void saveStarted(StartedWorkflowInstance startedWorkflowInstance, Instant now) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
      statement.setObject(1, startedWorkflowInstance.caseId());
      statement.setString(2, startedWorkflowInstance.processInstanceId());
      statement.setString(3, startedWorkflowInstance.processDefinitionId());
      statement.setInt(4, startedWorkflowInstance.processDefinitionVersion());
      statement.setString(5, startedWorkflowInstance.businessKey());
      statement.setTimestamp(6, Timestamp.from(now));
      statement.setTimestamp(7, Timestamp.from(now));
      statement.executeUpdate();
    } catch (SQLException exception) {
      throw new IllegalStateException("Failed to persist workflow correlation.", exception);
    }
  }

  Optional<WorkflowInstanceRecord> findByCaseId(UUID caseId) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(FIND_BY_CASE_ID_SQL)) {
      statement.setObject(1, caseId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(
            new WorkflowInstanceRecord(
                resultSet.getObject("case_id", UUID.class),
                resultSet.getString("process_instance_id"),
                resultSet.getString("process_definition_id"),
                resultSet.getInt("process_definition_version"),
                resultSet.getString("business_key"),
                resultSet.getString("status")));
      }
    } catch (SQLException exception) {
      throw new IllegalStateException("Failed to load workflow correlation.", exception);
    }
  }

  void markCompleted(String processInstanceId, Instant now) {
    updateStatus(MARK_COMPLETED_SQL, now, processInstanceId);
  }

  void markCancelled(UUID caseId, Instant now) {
    updateStatus(MARK_CANCELLED_SQL, now, caseId);
  }

  private void updateStatus(String sql, Instant now, Object identifier) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setTimestamp(1, Timestamp.from(now));
      statement.setObject(2, identifier);
      statement.executeUpdate();
    } catch (SQLException exception) {
      throw new IllegalStateException("Failed to update workflow correlation status.", exception);
    }
  }
}
