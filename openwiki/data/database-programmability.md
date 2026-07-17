---
type: Database Programmability
title: Database Programmability — Migrations, Functions, and Procedures
description: Documentation of the Sentinel database migration framework (Liquibase + CamundaSchemaMigrator), the generate_case_number() function, the recalculate_overdue_sanction_obligations() stored procedure, and rollback support.
tags: [sentinel, database, liquibase, camunda, migration, plpgsql]
---

# Database Programmability

## Migration Framework

```mermaid
flowchart TB
    subgraph Startup[Application Startup / Migration Entry]
        A[ApplicationRuntime.migrate()] --> B[createDataSource<br/>HikariCP]
    end

    subgraph Liquibase[Phase 1: Liquibase]
        B --> C[LiquibaseMigrator.migrate]
        C --> D[db.changelog-master.yaml]
        D --> E[0001-foundation.yaml]
        E --> F[0002-case-management.yaml<br/>+ generate_case_number func]
        F --> G[0003-workflow.yaml]
        G --> H[0004-evidence.yaml]
        H --> I[0005-messaging.yaml]
        I --> J[0006-phase7-decision-appeal.yaml]
        J --> K[0007-phase8-case-authorization.yaml]
        K --> L[0008-phase8-observability-notification.yaml]
        L --> M[0009-advanced-persistence-assignment.yaml]
        M --> N[0010-advanced-persistence-case-relationships.yaml]
        N --> O[0011-advanced-persistence-maintenance<br/>-operations.yaml<br/>+ recalculate_overdue proc]
    end

    subgraph Camunda[Phase 2: Camunda Engine Schema]
        O --> P[CamundaSchemaMigrator.migrate]
        P --> Q{ACT_GE_PROPERTY<br/>exists?}
        Q -->|Yes| R[Skip - schema already present]
        Q -->|No| S[Execute 7 official<br/>Camunda PostgreSQL SQL scripts]
        S --> T[ACT_* engine tables created]
    end

    subgraph Manual[Post-Startup]
        U[DatabaseMigrationMain<br/>standalone entrypoint] --> A
        V[DatabaseRollbackMain<br/>make rollback N] --> W[LiquibaseMigrator<br/>rollbackCount N]
    end

    style A stroke:#333,stroke-width:2px
    style C stroke:#4488ff,stroke-width:2px
    style P stroke:#44aa44,stroke-width:2px
```

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

## Programmable Object Scope

The Sentinel Enforcement Platform defines two PostgreSQL programmable objects and a migration framework. There are no database packages, types, triggers, rules, stored jobs, or dynamic SQL constructs beyond the two documented objects.

| Category | Count | Details |
|---|---|---|
| Functions | 1 | `generate_case_number()` — case number generation |
| Procedures | 1 | `recalculate_overdue_sanction_obligations()` — bulk status transition |
| Packages/Modules | 0 | PostgreSQL does not have packages; no `CREATE SCHEMA` beyond `public` |
| Triggers/Rules | 0 | Not observed |
| Stored Jobs/Schedulers | 0 | Not observed; scheduling is handled by `MaintenanceOperationApplicationService` invoked on-demand |
| Cursors/Dynamic SQL | 0 | Not observed in database objects; application-layer MyBatis uses dynamic SQL via `<script>` tags |

## Functions and Procedures

See detailed documentation above in the "PostgreSQL Functions and Procedures" section.

### `generate_case_number()`

- **Type:** `FUNCTION` (PL/pgSQL)
- **Inputs:** `p_jurisdiction_code VARCHAR`, `p_case_type VARCHAR`, `p_calendar_year INTEGER`
- **Output:** `VARCHAR` (formatted case number)
- **Called by:** `CaseMyBatisMapper.nextCaseNumber()` — application code only
- **Side effects:** UPSERT on `case_number_sequence` table (non-query side effect)
- **Transaction behaviour:** Executes within the calling transaction; the UPSERT is transactional

**Source:** `sentinel-persistence/src/main/resources/db/changelog/releases/0002-case-management.yaml`

### `recalculate_overdue_sanction_obligations()`

- **Type:** `PROCEDURE` (PL/pgSQL)
- **Inputs:** `p_effective_date DATE`, `p_actor VARCHAR`, `p_run_id UUID`
- **Output:** None (side-effect-only)
- **Called by:** `MaintenanceOperationMyBatisMapper.recalculateOverdueSanctionObligations()` — application code only
- **Side effects:** Writes to `maintenance_operation_run`, updates `sanction_obligation`
- **Transaction behaviour:** Full read/write; uses `GET DIAGNOSTICS` for row counting

**Source:** `sentinel-persistence/src/main/resources/db/changelog/releases/0011-advanced-persistence-maintenance-operations.yaml`

## Packages Modules and Types

**Not Observed.** The Sentinel schema uses only PostgreSQL built-in types. No custom composite types, domains, or enums are defined. No `PACKAGE` or module constructs exist.

## Triggers Rules and Notifications

**Not Observed.** No PostgreSQL triggers (row-level or statement-level), rewrite rules, or `NOTIFY`/`LISTEN` channels are defined.

## Stored Jobs and Schedulers

**Not Observed.** No `pg_cron`, `pg_timetable`, or `CREATE EXTENSION` for job scheduling exists within the application schema. Scheduled maintenance operations are driven by the `MaintenanceOperationApplicationService` in application code (`sentinel-application`), not by database schedulers.

## Source References

1. **Liquibase Changelogs** — `sentinel-persistence/src/main/resources/db/changelog/db.changelog-master.yaml`, `releases/0001-foundation.yaml` through `0011-advanced-persistence-maintenance-operations.yaml`
2. **Migration Runtime** — `sentinel-bootstrap/src/main/java/.../bootstrap/ApplicationRuntime.java`, `.../LiquibaseMigrator.java`, `.../CamundaSchemaMigrator.java`
3. **Standalone Tools** — `sentinel-bootstrap/src/main/java/.../bootstrap/DatabaseMigrationMain.java`, `.../DatabaseRollbackMain.java`
4. **Build** — `/Makefile` (migration targets)

## Cursors Bulk Operations and Dynamic SQL

**Not Observed at the database level.** The database objects use simple UPSERT and UPDATE statements. Dynamic SQL generation happens at the MyBatis mapper layer in Java, using MyBatis `<script>` dynamic SQL tags (e.g., `CaseMyBatisMapper.findCasePage()`). These are application-layer dynamic SQL, not PostgreSQL `EXECUTE` statements.

## Reads Writes and Side Effects

| Object | Reads | Writes | Side Effects |
|---|---|---|---|
| `generate_case_number()` | `case_number_sequence` (via UPSERT returning) | `case_number_sequence.next_value` (increment) | None beyond the counter update |
| `recalculate_overdue_sanction_obligations()` | `sanction_obligation` (WHERE filter) | `sanction_obligation.status`, `sanction_obligation.version`, `maintenance_operation_run` (2 rows) | Records operation run in audit table |

## Transactions Exceptions and Error Semantics

| Object | Transaction Behaviour | Error Semantics | Rollback |
|---|---|---|---|
| `generate_case_number()` | Participates in calling transaction | UPSERT `ON CONFLICT` handles the only expected conflict; no explicit exception handling | Atomic with calling transaction |
| `recalculate_overdue_sanction_obligations()` | Own transaction context via `MaintenanceOperationRepositoryMyBatisAdapter` | Fail-fast; any exception causes the entire procedure to roll back | `try-finally` in `MyBatisTransactionManager` handles rollback |

## Execution Rights Performance and Locking

| Object | Execution Rights | Performance Notes | Locking |
|---|---|---|---|
| `generate_case_number()` | `SECURITY INVOKER` (default) | Lightweight UPSERT with index on PK `(jurisdiction_code, calendar_year)` | Row-level lock on `case_number_sequence` row |
| `recalculate_overdue_sanction_obligations()` | `SECURITY INVOKER` (default) | Sequential scan on `sanction_obligation` filtered by `status='ACTIVE' AND due_date < effective_date` | Table-level lock via `LOCK TABLE sanction_obligation IN SHARE ROW EXCLUSIVE MODE NOWAIT` before calling |

**Concurrent call safety:** `MaintenanceOperationApplicationService` acquires a table-level lock (`SHARE ROW EXCLUSIVE MODE`) on `sanction_obligation` via `MaintenanceOperationMyBatisMapper.lockSanctionObligationTable()` before invoking the stored procedure. This prevents concurrent `recalculate` executions. If the lock is not immediately available, the application throws `LockNotAvailableException`.

## Database Programmability Catalog

| Object | Type | Purpose | Inputs and Outputs | Reads | Writes | Called By | Transaction or Security Behaviour | Evidence |
|---|---|---|---|---|---|---|---|---|
| `generate_case_number()` | FUNCTION | Generate formatted case number `{JURISDICTION}-ENF-{YEAR}-{SEQ}` | IN: `VARCHAR, VARCHAR, INTEGER` → OUT: `VARCHAR` | `case_number_sequence` (UPSERT RETURNING) | `case_number_sequence.next_value` (increment) | `CaseMyBatisMapper.nextCaseNumber()` (application layer) | Invoker rights; participates in calling transaction | `0002-case-management.yaml` |
| `recalculate_overdue_sanction_obligations()` | PROCEDURE | Bulk transition ACTIVE→OVERDUE obligations | IN: `DATE, VARCHAR, UUID` → OUT: none | `sanction_obligation` (WHERE filter) | `sanction_obligation` (status+version), `maintenance_operation_run` | `MaintenanceOperationMyBatisMapper.recalculateOverdueSanctionObligations()` (application layer, with table lock) | Invoker rights; guarded by `LOCK TABLE ... NOWAIT` | `0011-advanced-persistence-maintenance-operations.yaml` |

## Knowledge Gaps

1. **No triggers** — The database has no triggers for audit logging, validation, or derived data. All such logic is in application code. Future performance or consistency needs may require trigger-based solutions.
2. **No scheduled jobs** — There is no database scheduler. Maintenance operations (e.g., overdue recalculation) must be triggered externally via the API (`POST /api/v1/operations/recalculate-overdue-obligations`).
3. **No custom types or enums** — Status columns use `CHECK` constraints rather than PostgreSQL `ENUM` types. This is a deliberate pattern (easy to extend via migration) but `ENUM` would provide better type safety at the database level.
4. **No LISTEN/NOTIFY** — The application does not use PostgreSQL's pub/sub channels.
5. **Limited programmability surface** — With only 2 programmable objects, the database layer is thin. Most logic lives in Java application code and MyBatis mappers.

## Source References

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
| CaseMyBatisMapper.java | `sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/casefile/CaseMyBatisMapper.java` |
| MaintenanceOperationMyBatisMapper.java | `sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/operations/MaintenanceOperationMyBatisMapper.java` |
| MyBatisTransactionManager.java | `sentinel-persistence/src/main/java/com/sentinel/enforcement/persistence/MyBatisTransactionManager.java` |

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
