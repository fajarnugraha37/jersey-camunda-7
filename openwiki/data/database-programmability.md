---
type: Database Programmability
title: Database Programmability — Migrations, Functions, and Procedures
description: Documentation of the Sentinel database migration framework (Liquibase + CamundaSchemaMigrator), the generate_case_number() function, the recalculate_overdue_sanction_obligations() stored procedure, and rollback support.
tags: [sentinel, database, liquibase, camunda, migration, plpgsql]
---

# Database Programmability

## Migration Framework

The Sentinel Enforcement Platform uses a **dual migration strategy**:

1. **Liquibase** — manages the application schema (all Sentinel-specific tables, indexes, constraints, functions, and procedures).
2. **CamundaSchemaMigrator** — manages the Camunda BPM engine schema via official Camunda SQL scripts.

**Source:** `sentinel-persistence/src/main/resources/db/changelog/db.changelog-master.yaml` (master changelog)

### Master Changelog

The master changelog at `db/changelog/db.changelog-master.yaml` includes 11 release files in strict order:

```yaml
databaseChangeLog:
  - include:
      file: db/changelog/releases/0001-foundation.yaml
  - include:
      file: db/changelog/releases/0002-case-management.yaml
  - include:
      file: db/changelog/releases/0003-workflow.yaml
  - include:
      file: db/changelog/releases/0004-evidence.yaml
  - include:
      file: db/changelog/releases/0005-messaging.yaml
  - include:
      file: db/changelog/releases/0006-phase7-decision-appeal.yaml
  - include:
      file: db/changelog/releases/0007-phase8-case-authorization.yaml
  - include:
      file: db/changelog/releases/0008-phase8-observability-notification.yaml
  - include:
      file: db/changelog/releases/0009-advanced-persistence-assignment.yaml
  - include:
      file: db/changelog/releases/0010-advanced-persistence-case-relationships.yaml
  - include:
      file: db/changelog/releases/0011-advanced-persistence-maintenance-operations.yaml
```

**Source path:** `/sentinel-persistence/src/main/resources/db/changelog/db.changelog-master.yaml`

Each release file contains one or more changesets with unique `id` attributes, an `author`, the schema changes, and a `rollback` section.

### Release Files Summary

| File | Changesets | Scope |
|---|---|---|
| `0001-foundation.yaml` | 1 | `report` table, jurisdiction+created_at index, version check |
| `0002-case-management.yaml` | 4 | `case_number_sequence`, `generate_case_number()`, `case_record`, `case_assignment`, `case_status_history`, `audit_event` |
| `0003-workflow.yaml` | 1 | `workflow_instance` table with status/version checks |
| `0004-evidence.yaml` | 2 | report status check + indexes; `evidence`, `evidence_upload_session`, `evidence_version` |
| `0005-messaging.yaml` | 1 | `outbox_event`, `inbox_event`, `notification` with status checks and deduplication constraints |
| `0006-phase7-decision-appeal.yaml` | 3 | workflow_type on `workflow_instance`; `recommendation`, `recommendation_review`; `decision`, `decision_version`, `sanction`, `sanction_obligation`, `appeal`, `appeal_decision` |
| `0007-phase8-case-authorization.yaml` | 1 | `classification` column on `case_record` |
| `0008-phase8-observability-notification.yaml` | 1 | Expanded notification status to `('GENERATED','SENT','FAILED')` |
| `0009-advanced-persistence-assignment.yaml` | 1 | Active-flag and rotation tracking on `case_assignment` |
| `0010-advanced-persistence-case-relationships.yaml` | 1 | `case_relationship` table |
| `0011-advanced-persistence-maintenance-operations.yaml` | 1 | `OVERDUE` status on `sanction_obligation`; `maintenance_operation_run` table; `recalculate_overdue_sanction_obligations` procedure |

---

## PostgreSQL Functions and Procedures

### `generate_case_number()`

**Source:** `sentinel-persistence/src/main/resources/db/changelog/releases/0002-case-management.yaml` (changeset `0002-case-number-sequence`)

```sql
CREATE OR REPLACE FUNCTION generate_case_number(
    p_jurisdiction_code VARCHAR,
    p_case_type VARCHAR,
    p_calendar_year INTEGER
)
RETURNS VARCHAR
LANGUAGE plpgsql
AS '
DECLARE
    v_sequence BIGINT;
BEGIN
    INSERT INTO case_number_sequence (jurisdiction_code, calendar_year, next_value)
    VALUES (p_jurisdiction_code, p_calendar_year, 1)
    ON CONFLICT (jurisdiction_code, calendar_year)
    DO UPDATE
      SET next_value = case_number_sequence.next_value + 1
    RETURNING next_value INTO v_sequence;

    RETURN p_jurisdiction_code
        || ''-''
        || p_case_type
        || ''-''
        || p_calendar_year
        || ''-''
        || LPAD(v_sequence::TEXT, 8, ''0'');
END;
';
```

**Behaviour:**

- Uses an **UPSERT** (`INSERT ... ON CONFLICT DO UPDATE`) on the `case_number_sequence` table to atomically increment the counter for the given jurisdiction/year combo.
- Returns a formatted case number: `{JURISDICTION}-{TYPE}-{YEAR}-{00000001..99999999}`.
- Example output: `HQ-ENF-2026-00000042`.
- The function is **not** a literal function called per-insert; it is invoked from application code within a database transaction.

### `recalculate_overdue_sanction_obligations()`

**Source:** `sentinel-persistence/src/main/resources/db/changelog/releases/0011-advanced-persistence-maintenance-operations.yaml`

```sql
CREATE OR REPLACE PROCEDURE recalculate_overdue_sanction_obligations(
    IN p_effective_date DATE,
    IN p_actor VARCHAR,
    IN p_run_id UUID
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_requested_at TIMESTAMPTZ := now();
    v_affected_rows BIGINT := 0;
BEGIN
    -- Creates a RUNNING maintenance_operation_run record
    INSERT INTO maintenance_operation_run (...) VALUES (...);

    -- Updates all ACTIVE obligations past due_date to OVERDUE
    UPDATE sanction_obligation
    SET
        status = 'OVERDUE',
        updated_at = v_requested_at,
        updated_by = p_actor,
        version = version + 1
    WHERE status = 'ACTIVE'
      AND due_date < p_effective_date;

    GET DIAGNOSTICS v_affected_rows = ROW_COUNT;

    -- Finalises maintenance_operation_run as COMPLETED
    UPDATE maintenance_operation_run SET ... WHERE id = p_run_id;
END;
$$;
```

**Behaviour:**

- This is a **stored procedure** (not a function), called by the `MaintenanceOperationApplicationService`.
- It performs a bulk status transition of sanction obligations from `ACTIVE` to `OVERDUE`.
- It records execution progress in the `maintenance_operation_run` table for auditability.

---

## Dual Migration Execution Order

**Source:** `sentinel-bootstrap/src/main/java/com/sentinel/enforcement/bootstrap/ApplicationRuntime.java` (method `migrate()`)

```java
public static void migrate(AppConfiguration configuration) {
    try (HikariDataSource dataSource = createDataSource(configuration)) {
        LiquibaseMigrator.migrate(dataSource);       // 1st: application schema
        CamundaSchemaMigrator.migrate(dataSource);    // 2nd: Camunda engine schema
    }
}
```

**Execution order:**

1. **Liquibase** runs first against the application database — creates all Sentinel tables, constraints, indexes, functions, and procedures.
2. **CamundaSchemaMigrator** runs second — executes the official Camunda SQL scripts for PostgreSQL to create `ACT_*` tables for the embedded workflow engine.

**Entry point for migration-only (no server start):** `DatabaseMigrationMain.java`

```java
public final class DatabaseMigrationMain {
    public static void main(String[] args) {
        ApplicationRuntime.migrate(AppConfiguration.fromEnvironment());
    }
}
```

**Source path:** `/sentinel-bootstrap/src/main/java/com/sentinel/enforcement/bootstrap/DatabaseMigrationMain.java`

---

## Liquibase Framework

**Source:** `sentinel-bootstrap/src/main/java/com/sentinel/enforcement/bootstrap/LiquibaseMigrator.java`

```java
public final class LiquibaseMigrator {
    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

    public static void migrate(DataSource dataSource) {
        // Opens a JDBC connection, resolves the correct database implementation,
        // creates a Liquibase instance with ClassLoaderResourceAccessor,
        // and calls liquibase.update(new Contexts(), new LabelExpression()).
    }

    public static void rollbackCount(DataSource dataSource, int rollbackCount) {
        // Opens a JDBC connection and calls liquibase.rollback(rollbackCount, ...).
    }
}
```

**Key details:**

- The changelog path is `db/changelog/db.changelog-master.yaml` (classpath resource).
- Uses `ClassLoaderResourceAccessor` to resolve the changelog and all included release files.
- Sets `liquibase.duplicateFileMode` system property to `"ERROR"` to fail on duplicate file references.
- Both `migrate()` and `rollbackCount()` restore the original `duplicateFileMode` property after execution.

---

## Camunda Schema (Separate Management)

**Source:** `sentinel-bootstrap/src/main/java/com/sentinel/enforcement/bootstrap/CamundaSchemaMigrator.java`

The Camunda schema is **not managed by Liquibase**. Instead, `CamundaSchemaMigrator` runs the official Camunda SQL scripts directly via MyBatis `ScriptRunner`:

```java
private static final List<String> POSTGRES_CREATE_RESOURCES = List.of(
    "org/camunda/bpm/engine/db/create/activiti.postgres.create.engine.sql",
    "org/camunda/bpm/engine/db/create/activiti.postgres.create.history.sql",
    "org/camunda/bpm/engine/db/create/activiti.postgres.create.identity.sql",
    "org/camunda/bpm/engine/db/create/activiti.postgres.create.case.engine.sql",
    "org/camunda/bpm/engine/db/create/activiti.postgres.create.case.history.sql",
    "org/camunda/bpm/engine/db/create/activiti.postgres.create.decision.engine.sql",
    "org/camunda/bpm/engine/db/create/activiti.postgres.create.decision.history.sql"
);
```

**Idempotency check:** Before running any scripts, the migrator checks if the `ACT_GE_PROPERTY` table already exists. If it does, migration is skipped entirely (the schema is already present).

**Transaction management:** Runs all scripts with `autoCommit=false`, commits on success, rolls back on failure.

---

## Rollback Support

### Via Makefile

**Source:** `/Makefile` (lines 132-134)

```makefile
rollback:
    mvn -q -pl sentinel-bootstrap -am -DskipTests install
    $(LOCAL_RUNTIME_ENV) mvn -q -f sentinel-bootstrap/pom.xml exec:java \
        "-Dexec.mainClass=com.sentinel.enforcement.bootstrap.DatabaseRollbackMain" \
        "-Dexec.args=$(ROLLBACK_COUNT)"
```

Usage: `make rollback ROLLBACK_COUNT=3` rolls back 3 Liquibase changesets. Default `ROLLBACK_COUNT` is 1 (set at Makefile line 3).

### DatabaseRollbackMain

**Source:** `sentinel-bootstrap/src/main/java/com/sentinel/enforcement/bootstrap/DatabaseRollbackMain.java`

```java
public final class DatabaseRollbackMain {
    public static void main(String[] args) {
        int rollbackCount = args.length == 0 ? 1 : Integer.parseInt(args[0]);
        ApplicationRuntime.rollback(AppConfiguration.fromEnvironment(), rollbackCount);
    }
}
```

This is a **standalone entrypoint** (executable JAR via Maven `exec:java`) that performs a Liquibase rollback **without** starting the application server.

### `ApplicationRuntime.rollback()`

```java
public static void rollback(AppConfiguration configuration, int rollbackCount) {
    try (HikariDataSource dataSource = createDataSource(configuration)) {
        LiquibaseMigrator.rollbackCount(dataSource, rollbackCount);
    }
}
```

**Important:** Rollback only applies to **Liquibase-managed changesets**. The Camunda schema (`ACT_*` tables) is not rolled back by this process. Manual intervention is required for Camunda schema rollback.

### Rollback Support in Release Files

Each changeset in every release file includes a `rollback` section. For example:

- **Create table:** `dropTable`
- **Add column:** `dropColumn`
- **Add constraint:** `DROP CONSTRAINT IF EXISTS ...`
- **Create function:** `DROP FUNCTION IF EXISTS ...`
- **Create index:** `dropIndex`

---

## Source Files Reference

| File | Path |
|---|---|
| Master changelog | `sentinel-persistence/src/main/resources/db/changelog/db.changelog-master.yaml` |
| Release 0001 | `sentinel-persistence/src/main/resources/db/changelog/releases/0001-foundation.yaml` |
| Release 0002 | `sentinel-persistence/src/main/resources/db/changelog/releases/0002-case-management.yaml` |
| Release 0003 | `sentinel-persistence/src/main/resources/db/changelog/releases/0003-workflow.yaml` |
| Release 0004 | `sentinel-persistence/src/main/resources/db/changelog/releases/0004-evidence.yaml` |
| Release 0005 | `sentinel-persistence/src/main/resources/db/changelog/releases/0005-messaging.yaml` |
| Release 0006 | `sentinel-persistence/src/main/resources/db/changelog/releases/0006-phase7-decision-appeal.yaml` |
| Release 0007 | `sentinel-persistence/src/main/resources/db/changelog/releases/0007-phase8-case-authorization.yaml` |
| Release 0008 | `sentinel-persistence/src/main/resources/db/changelog/releases/0008-phase8-observability-notification.yaml` |
| Release 0009 | `sentinel-persistence/src/main/resources/db/changelog/releases/0009-advanced-persistence-assignment.yaml` |
| Release 0010 | `sentinel-persistence/src/main/resources/db/changelog/releases/0010-advanced-persistence-case-relationships.yaml` |
| Release 0011 | `sentinel-persistence/src/main/resources/db/changelog/releases/0011-advanced-persistence-maintenance-operations.yaml` |
| LiquibaseMigrator.java | `sentinel-bootstrap/src/main/java/com/sentinel/enforcement/bootstrap/LiquibaseMigrator.java` |
| CamundaSchemaMigrator.java | `sentinel-bootstrap/src/main/java/com/sentinel/enforcement/bootstrap/CamundaSchemaMigrator.java` |
| DatabaseMigrationMain.java | `sentinel-bootstrap/src/main/java/com/sentinel/enforcement/bootstrap/DatabaseMigrationMain.java` |
| DatabaseRollbackMain.java | `sentinel-bootstrap/src/main/java/com/sentinel/enforcement/bootstrap/DatabaseRollbackMain.java` |
| ApplicationRuntime.java | `sentinel-bootstrap/src/main/java/com/sentinel/enforcement/bootstrap/ApplicationRuntime.java` |
| Makefile | `/Makefile` |
