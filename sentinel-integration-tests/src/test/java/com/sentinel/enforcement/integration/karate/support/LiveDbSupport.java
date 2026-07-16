package com.sentinel.enforcement.integration.karate.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LiveDbSupport {
  private static final String DEFAULT_DB_URL = "jdbc:postgresql://localhost:5432/sentinel";
  private static final String DEFAULT_DB_USERNAME = "sentinel";
  private static final String DEFAULT_DB_PASSWORD = "sentinel";
  private static final Map<String, LockHandle> LOCKS = new ConcurrentHashMap<>();

  private LiveDbSupport() {}

  public static int deleteWorkflowInstance(String caseId) {
    return executeUpdate("DELETE FROM workflow_instance WHERE case_id = ?", UUID.fromString(caseId));
  }

  public static int forceCaseStatus(String caseId, String status) {
    return executeUpdate(
        """
        UPDATE case_record
        SET status = ?,
            version = version + 1,
            updated_at = CURRENT_TIMESTAMP,
            updated_by = 'karate-live-db'
        WHERE id = ?
        """,
        status,
        UUID.fromString(caseId));
  }

  public static String workflowStatus(String caseId) {
    return queryForString(
        "SELECT status FROM workflow_instance WHERE case_id = ? AND workflow_type = 'CASE_MAIN'",
        UUID.fromString(caseId));
  }

  public static long countAuditEventsByType(String caseId, String eventType) {
    return queryForLong(
        "SELECT COUNT(*) FROM audit_event WHERE case_id = ? AND event_type = ?",
        UUID.fromString(caseId),
        eventType);
  }

  public static String sanctionStatusByDecisionId(String decisionId) {
    return queryForString(
        "SELECT status FROM sanction WHERE decision_id = ?", UUID.fromString(decisionId));
  }

  public static String sanctionObligationStatusByDecisionId(String decisionId) {
    return queryForString(
        """
        SELECT obligation.status
        FROM sanction_obligation obligation
        JOIN sanction sanction ON sanction.id = obligation.sanction_id
        WHERE sanction.decision_id = ?
        """,
        UUID.fromString(decisionId));
  }

  public static String appealStatus(String appealId) {
    return queryForString("SELECT status FROM appeal WHERE id = ?", UUID.fromString(appealId));
  }

  public static long countOverdueSanctionObligations(String effectiveDate) {
    return queryForLong(
        "SELECT COUNT(*) FROM sanction_obligation WHERE status = 'ACTIVE' AND due_date < ?",
        LocalDate.parse(effectiveDate));
  }

  public static long maintenanceRunCount() {
    return queryForLong("SELECT COUNT(*) FROM maintenance_operation_run");
  }

  public static long maintenanceRunCount(String runId) {
    return queryForLong(
        "SELECT COUNT(*) FROM maintenance_operation_run WHERE id = ?", UUID.fromString(runId));
  }

  public static long countCaseStatusHistory(String caseId) {
    return queryForLong(
        "SELECT COUNT(*) FROM case_status_history WHERE case_id = ?", UUID.fromString(caseId));
  }

  public static long countAuditEvents(String caseId) {
    return queryForLong(
        "SELECT COUNT(*) FROM audit_event WHERE case_id = ?", UUID.fromString(caseId));
  }

  public static long countCaseAssignments(String caseId) {
    return queryForLong(
        "SELECT COUNT(*) FROM case_assignment WHERE case_id = ?", UUID.fromString(caseId));
  }

  public static long countRecommendations(String caseId) {
    return queryForLong(
        "SELECT COUNT(*) FROM recommendation WHERE case_id = ?", UUID.fromString(caseId));
  }

  public static long countDecisions(String caseId) {
    return queryForLong(
        "SELECT COUNT(*) FROM decision WHERE case_id = ?", UUID.fromString(caseId));
  }

  public static long activeAssignmentCount(String caseId) {
    return queryForLong(
        "SELECT COUNT(*) FROM case_assignment WHERE case_id = ? AND is_active = TRUE",
        UUID.fromString(caseId));
  }

  public static long inactiveReleasedAssignmentCount(String caseId, String releasedBy) {
    return queryForLong(
        """
        SELECT COUNT(*)
        FROM case_assignment
        WHERE case_id = ?
          AND is_active = FALSE
          AND released_at IS NOT NULL
          AND released_by = ?
          AND superseded_by_assignment_id IS NOT NULL
        """,
        UUID.fromString(caseId),
        releasedBy);
  }

  public static String activeAssignee(String caseId) {
    return queryForString(
        """
        SELECT assignee_user_id
        FROM case_assignment
        WHERE case_id = ?
          AND is_active = TRUE
        """,
        UUID.fromString(caseId));
  }

  public static long caseRelationshipCount() {
    return queryForLong("SELECT COUNT(*) FROM case_relationship");
  }

  public static long notificationCountByCaseAndType(String caseId, String notificationType) {
    return queryForLong(
        "SELECT COUNT(*) FROM notification WHERE case_id = ? AND notification_type = ?",
        UUID.fromString(caseId),
        notificationType);
  }

  public static long notificationCountByEventId(String eventId) {
    return queryForLong(
        "SELECT COUNT(*) FROM notification WHERE event_id = ?", UUID.fromString(eventId));
  }

  public static long notificationCountByCaseTypeAndStatus(
      String caseId, String notificationType, String status) {
    return queryForLong(
        """
        SELECT COUNT(*)
        FROM notification
        WHERE case_id = ?
          AND notification_type = ?
          AND status = ?
        """,
        UUID.fromString(caseId),
        notificationType,
        status);
  }

  public static long publishedOutboxCountByTopicAndCaseId(String topic, String caseId) {
    return queryForLong(
        """
        SELECT COUNT(*)
        FROM outbox_event
        WHERE topic = ?
          AND status = 'PUBLISHED'
          AND payload_json ->> 'caseId' = ?
        """,
        topic,
        caseId);
  }

  public static long publishedAuditOutboxCount(String caseId, String auditEventType) {
    return queryForLong(
        """
        SELECT COUNT(*)
        FROM outbox_event
        WHERE topic = 'audit.integration.v1'
          AND status = 'PUBLISHED'
          AND payload_json ->> 'caseId' = ?
          AND payload_json ->> 'auditEventType' = ?
        """,
        caseId,
        auditEventType);
  }

  public static String outboxEventIdForAggregateAndType(String aggregateId, String eventType) {
    return queryForString(
        """
        SELECT event_id::text
        FROM outbox_event
        WHERE aggregate_id = ?
          AND event_type = ?
        """,
        UUID.fromString(aggregateId),
        eventType);
  }

  public static String outboxEnvelopeJson(String eventId) {
    return queryForString(
        """
        SELECT jsonb_build_object(
                 'eventId', event_id,
                 'eventType', event_type,
                 'eventVersion', event_version,
                 'aggregateType', aggregate_type,
                 'aggregateId', aggregate_id,
                 'occurredAt', occurred_at,
                 'correlationId', correlation_id,
                 'causationId', causation_id,
                 'actor', jsonb_build_object('type', actor_type, 'id', actor_id),
                 'payload', payload_json
               )::text
        FROM outbox_event
        WHERE event_id = ?
        """,
        UUID.fromString(eventId));
  }

  public static long inboxEventCount(String consumerName, String eventId) {
    return queryForLong(
        "SELECT COUNT(*) FROM inbox_event WHERE consumer_name = ? AND event_id = ?",
        consumerName,
        UUID.fromString(eventId));
  }

  public static String acquireDecisionLock(String decisionId) {
    try {
      Connection connection = openConnection();
      connection.setAutoCommit(false);
      PreparedStatement statement =
          connection.prepareStatement("SELECT id FROM decision WHERE id = ? FOR UPDATE");
      statement.setObject(1, UUID.fromString(decisionId));
      ResultSet resultSet = statement.executeQuery();
      if (!resultSet.next()) {
        closeQuietly(resultSet, statement, connection);
        throw new IllegalStateException("Decision row was not found for lock acquisition.");
      }
      String lockId = UUID.randomUUID().toString();
      LOCKS.put(lockId, new LockHandle(connection, statement, resultSet));
      return lockId;
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to acquire decision row lock.", exception);
    }
  }

  public static String acquireTableLock(String tableName, String lockMode) {
    if (!tableName.matches("[a-z_]+")) {
      throw new IllegalArgumentException("Unsupported table name for live DB lock.");
    }
    if (!lockMode.matches("[A-Z ]+")) {
      throw new IllegalArgumentException("Unsupported lock mode for live DB lock.");
    }
    try {
      Connection connection = openConnection();
      connection.setAutoCommit(false);
      Statement statement = connection.createStatement();
      statement.execute("LOCK TABLE " + tableName + " IN " + lockMode + " MODE");
      String lockId = UUID.randomUUID().toString();
      LOCKS.put(lockId, new LockHandle(connection, statement, null));
      return lockId;
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to acquire table lock.", exception);
    }
  }

  public static boolean releaseLock(String lockId) {
    LockHandle handle = LOCKS.remove(lockId);
    if (handle == null) {
      return false;
    }
    closeQuietly(handle.resultSet(), handle.statement(), handle.connection());
    return true;
  }

  private static int executeUpdate(String sql, Object... parameters) {
    try (Connection connection = openConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bindParameters(statement, parameters);
      return statement.executeUpdate();
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to execute SQL update.", exception);
    }
  }

  private static String queryForString(String sql, Object... parameters) {
    try (Connection connection = openConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bindParameters(statement, parameters);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return null;
        }
        return resultSet.getString(1);
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to execute SQL query.", exception);
    }
  }

  private static long queryForLong(String sql, Object... parameters) {
    try (Connection connection = openConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bindParameters(statement, parameters);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return 0L;
        }
        return resultSet.getLong(1);
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to execute SQL query.", exception);
    }
  }

  private static void bindParameters(PreparedStatement statement, Object... parameters)
      throws Exception {
    for (int index = 0; index < parameters.length; index++) {
      statement.setObject(index + 1, parameters[index]);
    }
  }

  private static Connection openConnection() throws Exception {
    return DriverManager.getConnection(dbUrl(), dbUsername(), dbPassword());
  }

  private static String dbUrl() {
    return propertyOrDefault("sentinel.dbUrl", "DB_URL", DEFAULT_DB_URL);
  }

  private static String dbUsername() {
    return propertyOrDefault("sentinel.dbUsername", "DB_USERNAME", DEFAULT_DB_USERNAME);
  }

  private static String dbPassword() {
    return propertyOrDefault("sentinel.dbPassword", "DB_PASSWORD", DEFAULT_DB_PASSWORD);
  }

  private static String propertyOrDefault(
      String propertyName, String environmentName, String defaultValue) {
    String propertyValue = System.getProperty(propertyName);
    if (propertyValue != null && !propertyValue.isBlank()) {
      return propertyValue;
    }
    String environmentValue = System.getenv(environmentName);
    if (environmentValue != null && !environmentValue.isBlank()) {
      return environmentValue;
    }
    return defaultValue;
  }

  private static void closeQuietly(ResultSet resultSet, AutoCloseable statement, Connection connection) {
    try {
      if (resultSet != null) {
        resultSet.close();
      }
    } catch (Exception ignored) {
    }
    try {
      if (statement != null) {
        statement.close();
      }
    } catch (Exception ignored) {
    }
    try {
      if (connection != null) {
        connection.close();
      }
    } catch (Exception ignored) {
    }
  }

  private record LockHandle(Connection connection, AutoCloseable statement, ResultSet resultSet) {}
}
