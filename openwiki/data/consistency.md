---
type: Data Consistency
title: Data Consistency — Optimistic Locking, Transactional Outbox, and Constraints
description: Documentation of the Sentinel Enforcement Platform's data consistency mechanisms including optimistic concurrency control, transactional outbox pattern, MyBatis transaction management, and database constraints.
tags: [sentinel, database, consistency, transactions, mybatis]
---

# Data Consistency

The Sentinel Enforcement Platform maintains data consistency through three complementary mechanisms:

1. **Optimistic concurrency control** — version-based conflict detection on every mutable aggregate.
2. **Transactional outbox** — domain events written in the same DB transaction as the aggregate change.
3. **Database constraints** — foreign keys, check constraints, and unique constraints enforced at the schema level.

---

## 1. Optimistic Concurrency Control

### Pattern

Every mutable aggregate table includes a `version BIGINT NOT NULL DEFAULT 0` column with a `CHECK (version >= 0)` constraint. Updates use a **conditional WHERE clause** that checks the version, and increment the version atomically.

### Implementation

**Source:** `sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/casefile/CaseMyBatisMapper.java` (lines 57-75)

```java
@Update("""
    <script>
    UPDATE case_record
    <set>
        status = #{caseRecord.status},
        assigned_unit_id = #{caseRecord.assignedUnitId},
        assignee_user_id = #{caseRecord.assigneeUserId},
        updated_at = #{caseRecord.updatedAt},
        updated_by = #{caseRecord.updatedBy},
        version = #{caseRecord.version}
    </set>
    WHERE id = #{caseRecord.id}
      AND version = #{expectedVersion}
    </script>
    """)
int updateCase(
    @Param("caseRecord") CaseRecordData caseRecord,
    @Param("expectedVersion") long expectedVersion);
```

**Behaviour:**

1. The application reads the aggregate (getting the current `version` value).
2. It performs business logic, calculating the new state and incrementing `version` by 1 in the `SET` clause (`version = #{caseRecord.version}` where `caseRecord.version = expectedVersion + 1`).
3. The `UPDATE` includes `WHERE id = ? AND version = <expectedVersion>`.
4. If **zero rows** are updated, it means another transaction modified the same aggregate concurrently — the method returns 0 and the repository adapter throws a **`ConflictException`**.
5. If exactly one row is updated, the operation succeeded.

### ConflictException Mapping

**Source:** `sentinel-api/src/main/java/com/sentinel/enforcement/api/error/CaseConflictExceptionMapper.java` (and similar mappers for other aggregates)

A `ConflictException` is mapped to an **HTTP 409 Conflict** response with an error code such as `CONCURRENT_MODIFICATION`. All conflict exception mappers follow this pattern:

| Exception | Error Code |
|---|---|
| `CaseConflictException` | `CASE_CONCURRENT_MODIFICATION` |
| `ReportConflictException` | `REPORT_CONCURRENT_MODIFICATION` |
| `EvidenceConflictException` | `EVIDENCE_CONCURRENT_MODIFICATION` |
| `RecommendationConflictException` | `RECOMMENDATION_CONCURRENT_MODIFICATION` |
| `DecisionConflictException` | `DECISION_CONCURRENT_MODIFICATION` |
| `AppealConflictException` | `APPEAL_CONCURRENT_MODIFICATION` |
| `MaintenanceOperationConflictException` | `MAINTENANCE_OPERATION_CONCURRENT_MODIFICATION` |
| `WorkflowReconciliationConflictException` | `WORKFLOW_RECONCILIATION_CONCURRENT_MODIFICATION` |
| `WorkflowTaskConflictException` | `WORKFLOW_TASK_CONCURRENT_MODIFICATION` |

### Check Constraint Enforcement

All version columns are protected at the database level:

```sql
ALTER TABLE case_record ADD CONSTRAINT ck_case_record_version_non_negative CHECK (version >= 0);
```

This check is applied consistently across all mutable tables (see [database-structure.md](database-structure.md) for the complete list).

---

## 2. Transactional Outbox

### Pattern

The **Transactional Outbox** pattern ensures reliable event publishing. When a domain operation changes an aggregate, the corresponding domain event is **not** published directly to Kafka. Instead, it is inserted as an `outbox_event` row **in the same database transaction** as the aggregate modification.

**Source:** `sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/messaging/OutboxRepositoryMyBatisAdapter.java`

### Flow

1. **Application service** opens a database transaction (via `MyBatisTransactionManager.required()`).
2. **Aggregate update** — the repository updates the aggregate row using the optimistic locking pattern.
3. **Outbox insert** — the repository inserts an `outbox_event` row with `status='PENDING'`, the event payload as `JSONB`, and correlation metadata.
4. **Transaction commit** — both the aggregate update and the outbox insert commit atomically.
5. **Asynchronous poller** — the `KafkaOutboxPublisher` background job (in `sentinel-messaging`) polls for `PENDING` outbox events, publishes them to Kafka, and marks them as `PUBLISHED`.

### Outbox Event Table

See [database-structure.md](database-structure.md) for the full `outbox_event` schema (`0005-messaging.yaml`). Key columns for consistency:

- `event_id` (PK) — unique event identifier.
- `status` — `CHECK (status IN ('PENDING', 'PUBLISHED'))`.
- `version` — `BIGINT`, with `CHECK (version >= 0)` (the outbox also uses optimistic locking for lease-take).
- `lease_owner` / `lease_expires_at` — used by the `KafkaOutboxPublisher` to claim events for processing.
- `publish_attempts` — `CHECK (publish_attempts >= 0)`, incremented on each attempt.

### Guarantees

- **Exactly-once delivery to at-least-once:** The outbox insert is transactional with the aggregate change. The publisher may publish the same event multiple times on failure, but the event is idempotent on the consumer side (via inbox deduplication).
- **No dual-write problem:** The application never writes to both the database and Kafka directly — Kafka is written only by the outbox publisher, which reads committed outbox rows.

---

## 3. MyBatis Transaction Manager

### MyBatisTransactionManager

**Source:** `sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/MyBatisTransactionManager.java`

```java
public final class MyBatisTransactionManager implements ApplicationTransactionManager {
    private final SqlSessionFactory sqlSessionFactory;

    @Override
    public <T> T required(TransactionOptions options, Supplier<T> work) {
        SqlSession currentSession = MyBatisSessionContext.currentSession();
        if (currentSession != null) {
            return work.get();  // Reuse existing session (nested call)
        }

        try (SqlSession session = sqlSessionFactory.openSession(
                ExecutorType.SIMPLE,
                toMyBatisIsolationLevel(options.isolation()))) {
            MyBatisSessionContext.bind(session);
            try {
                session.getConnection().setReadOnly(options.readOnly());
                T result = work.get();
                session.commit();
                return result;
            } catch (SQLException exception) {
                session.rollback();
                throw new IllegalStateException(...);
            } catch (RuntimeException | Error exception) {
                session.rollback();
                throw exception;
            } finally {
                MyBatisSessionContext.clear();
            }
        }
    }
}
```

**Key behaviours:**

- **Session reuse:** If a `SqlSession` is already bound to the current thread via `MyBatisSessionContext`, the `required()` method reuses it (nested transaction joining).
- **Read-only support:** If `options.readOnly()` is true, `session.getConnection().setReadOnly(true)` is called before executing the work.
- **Transaction isolation:** Maps `TransactionIsolation` enum (`READ_COMMITTED`, `REPEATABLE_READ`, `SERIALIZABLE`) to MyBatis `TransactionIsolationLevel`.
- **Rollback safety:** On any `RuntimeException`, `Error`, or `SQLException`, the session is rolled back before closing.
- **Thread safety:** Each thread gets its own `SqlSession` via `ThreadLocal`.

### MyBatisSessionContext

**Source:** `sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/MyBatisSessionContext.java`

```java
final class MyBatisSessionContext {
    private static final ThreadLocal<SqlSession> CURRENT_SESSION = new ThreadLocal<>();

    static SqlSession currentSession() { return CURRENT_SESSION.get(); }
    static void bind(SqlSession session) { CURRENT_SESSION.set(session); }
    static void clear() { CURRENT_SESSION.remove(); }
}
```

Package-private class providing `ThreadLocal<SqlSession>` propagation. Used exclusively by `MyBatisTransactionManager` and repository adapters that need to participate in the current transaction.

---

## 4. Foreign Key Constraints

All inter-table relationships are enforced at the database level. The following foreign keys are defined across the schema:

**Source:** `sentinel-persistence/src/main/resources/db/changelog/releases/*.yaml`

| FK Name | Child Table | Parent Table | Column |
|---|---|---|---|
| `fk_case_record_report` | `case_record` | `report` | `report_id` |
| `fk_evidence_case` | `evidence` | `case_record` | `case_id` |
| `fk_evidence_upload_session_case` | `evidence_upload_session` | `case_record` | `case_id` |
| `fk_evidence_upload_session_evidence` | `evidence_upload_session` | `evidence` | `evidence_id` |
| `fk_case_status_history_case` | `case_status_history` | `case_record` | `case_id` |
| `fk_case_assignment_case` | `case_assignment` | `case_record` | `case_id` |
| `fk_audit_event_case` | `audit_event` | `case_record` | `case_id` |
| `fk_recommendation_case` | `recommendation` | `case_record` | `case_id` |
| `fk_recommendation_review_recommendation` | `recommendation_review` | `recommendation` | `recommendation_id` |
| `fk_decision_case` | `decision` | `case_record` | `case_id` |
| `fk_decision_recommendation` | `decision` | `recommendation` | `recommendation_id` |
| `fk_decision_version_decision` | `decision_version` | `decision` | `decision_id` |
| `fk_sanction_case` | `sanction` | `case_record` | `case_id` |
| `fk_sanction_decision` | `sanction` | `decision` | `decision_id` |
| `fk_sanction_obligation_sanction` | `sanction_obligation` | `sanction` | `sanction_id` |
| `fk_appeal_case` | `appeal` | `case_record` | `case_id` |
| `fk_appeal_decision` | `appeal` | `decision` | `decision_id` |
| `fk_appeal_decision_appeal` | `appeal_decision` | `appeal` | `appeal_id` |
| `fk_notification_case` | `notification` | `case_record` | `case_id` |
| `fk_case_relationship_parent_case` | `case_relationship` | `case_record` | `parent_case_id` |
| `fk_case_relationship_child_case` | `case_relationship` | `case_record` | `child_case_id` |

---

## 5. Check Constraints

**Source:** `sentinel-persistence/src/main/resources/db/changelog/releases/*.yaml`

| Constraint | Table | Expression |
|---|---|---|
| `ck_report_status` | `report` | `status IN ('SUBMITTED', 'TRIAGED')` |
| `ck_report_version_non_negative` | `report` | `version >= 0` |
| `ck_case_record_status` | `case_record` | `status IN ('CREATED','UNDER_TRIAGE','UNDER_INVESTIGATION','PENDING_REVIEW','PENDING_DECISION','DECIDED','UNDER_APPEAL','ENFORCEMENT_IN_PROGRESS','CLOSED','CANCELLED')` |
| `ck_case_record_classification` | `case_record` | `classification IN ('PUBLIC','CONFIDENTIAL','SECRET')` |
| `ck_case_record_version_non_negative` | `case_record` | `version >= 0` |
| `ck_case_assignment_version_non_negative` | `case_assignment` | `version >= 0` |
| `ck_case_assignment_release_state` | `case_assignment` | Composite check on active/inactive state (see [database-structure.md](database-structure.md)) |
| `ck_evidence_classification` | `evidence` | `classification IN ('PUBLIC','CONFIDENTIAL','SECRET')` |
| `ck_evidence_storage_status` | `evidence` | `storage_status IN ('PENDING_UPLOAD','ACTIVE')` |
| `ck_evidence_latest_version_non_negative` | `evidence` | `latest_version >= 0` |
| `ck_evidence_version_non_negative` | `evidence` | `version >= 0` |
| `ck_workflow_instance_status` | `workflow_instance` | `status IN ('ACTIVE','COMPLETED','CANCELLED')` |
| `ck_workflow_instance_type` | `workflow_instance` | `workflow_type IN ('CASE_MAIN','APPEAL')` |
| `ck_workflow_instance_definition_version_positive` | `workflow_instance` | `process_definition_version > 0` |
| `ck_outbox_event_status` | `outbox_event` | `status IN ('PENDING','PUBLISHED')` |
| `ck_outbox_event_event_version_positive` | `outbox_event` | `event_version > 0` |
| `ck_outbox_event_publish_attempts_non_negative` | `outbox_event` | `publish_attempts >= 0` |
| `ck_outbox_event_version_non_negative` | `outbox_event` | `version >= 0` |
| `ck_inbox_event_version_non_negative` | `inbox_event` | `version >= 0` |
| `ck_notification_status` | `notification` | `status IN ('GENERATED','SENT','FAILED')` |
| `ck_notification_version_non_negative` | `notification` | `version >= 0` |
| `ck_recommendation_status` | `recommendation` | `status IN ('DRAFT','SUBMITTED','APPROVED')` |
| `ck_recommendation_version_non_negative` | `recommendation` | `version >= 0` |
| `ck_recommendation_review_outcome` | `recommendation_review` | `outcome IN ('APPROVED')` |
| `ck_recommendation_review_version_non_negative` | `recommendation_review` | `version >= 0` |
| `ck_decision_status` | `decision` | `status IN ('DRAFT','APPROVED','PUBLISHED')` |
| `ck_decision_version_non_negative` | `decision` | `version >= 0` |
| `ck_sanction_status` | `sanction` | `status IN ('ACTIVE','CANCELLED')` |
| `ck_sanction_version_non_negative` | `sanction` | `version >= 0` |
| `ck_sanction_obligation_status` | `sanction_obligation` | `status IN ('ACTIVE','OVERDUE','SATISFIED','CANCELLED')` |
| `ck_sanction_obligation_version_non_negative` | `sanction_obligation` | `version >= 0` |
| `ck_appeal_status` | `appeal` | `status IN ('ACTIVE','DECIDED')` |
| `ck_appeal_version_non_negative` | `appeal` | `version >= 0` |
| `ck_appeal_decision_outcome` | `appeal_decision` | `outcome IN ('DENIED','GRANTED')` |
| `ck_appeal_decision_version_non_negative` | `appeal_decision` | `version >= 0` |
| `ck_case_relationship_type` | `case_relationship` | `relationship_type IN ('MERGE','DERIVATION','SPLIT')` |
| `ck_case_relationship_not_self` | `case_relationship` | `parent_case_id <> child_case_id` |
| `ck_case_relationship_version_non_negative` | `case_relationship` | `version >= 0` |
| `ck_maintenance_operation_run_status` | `maintenance_operation_run` | `result_status IN ('RUNNING','COMPLETED','FAILED')` |
| `ck_maintenance_operation_run_affected_rows_non_negative` | `maintenance_operation_run` | `affected_rows >= 0` |

---

## 6. Unique Constraints

**Source:** `sentinel-persistence/src/main/resources/db/changelog/releases/*.yaml`

| Constraint | Table | Columns | Purpose |
|---|---|---|---|
| `case_number` (unique index) | `case_record` | `case_number` | Human-readable case number uniqueness |
| `uk_recommendation_case` (inline UNIQUE) | `recommendation` | `case_id` | One recommendation per case |
| `uk_decision_case` (inline UNIQUE) | `decision` | `case_id` | One decision per case |
| `uk_inbox_event_consumer_event` | `inbox_event` | `consumer_name`, `event_id` | Inbox deduplication (see below) |
| `uk_notification_consumer_event` | `notification` | `consumer_name`, `event_id` | Notification deduplication |
| `uk_decision_version_decision_version` | `decision_version` | `decision_id`, `version_number` | No duplicate version numbers per decision |
| `uk_case_assignment_active_case` | `case_assignment` | `active_case_id` | At most one active assignment per case (`DEFERRABLE INITIALLY IMMEDIATE`) |
| `uk_case_relationship_edge` | `case_relationship` | `parent_case_id`, `child_case_id`, `relationship_type` | No duplicate edges |

---

## 7. Inbox Deduplication

**Source:** `sentinel-persistence/src/main/resources/db/changelog/releases/0005-messaging.yaml`

The `inbox_event` table uses its `id` (UUID) as the **primary key**. This is the mechanism for deduplication:

```
inbox_event.id = PK (UUID)
```

When consuming a Kafka message, the consumer constructs the inbox event ID deterministically (e.g., from the Kafka message key or a known event ID). The `INSERT` for the `inbox_event` uses `id` as the primary key:

- If the `id` already exists, the insert fails with a **unique violation** (PostgreSQL SQL state `23505`).
- The consumer catches this and considers the event already processed — **duplicate processing is prevented**.

Additionally, the **`uk_inbox_event_consumer_event`** unique constraint on `(consumer_name, event_id)` provides a secondary deduplication key.

**Source:** `sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/PersistenceExceptionClassifier.java`

```java
public final class PersistenceExceptionClassifier {
    private static final String SQL_STATE_UNIQUE_VIOLATION = "23505";

    public static boolean isUniqueViolation(Throwable throwable) {
        SQLException sqlException = findSQLException(throwable);
        return sqlException != null && SQL_STATE_UNIQUE_VIOLATION.equals(sqlException.getSQLState());
    }
}
```

This classifier is used by `InboxRepositoryMyBatisAdapter` to detect duplicate inserts and handle them gracefully (treat as success since the event was already processed).

---

## Source Files Reference

| File | Path |
|---|---|
| CaseMyBatisMapper.java | `sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/casefile/CaseMyBatisMapper.java` |
| MyBatisTransactionManager.java | `sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/MyBatisTransactionManager.java` |
| MyBatisSessionContext.java | `sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/MyBatisSessionContext.java` |
| PersistenceExceptionClassifier.java | `sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/PersistenceExceptionClassifier.java` |
| OutboxRepositoryMyBatisAdapter.java | `sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/messaging/OutboxRepositoryMyBatisAdapter.java` |
| Release YAML files | `sentinel-persistence/src/main/resources/db/changelog/releases/*.yaml` |
| CaseConflictExceptionMapper.java | `sentinel-api/src/main/java/com/sentinel/enforcement/api/error/CaseConflictExceptionMapper.java` |
