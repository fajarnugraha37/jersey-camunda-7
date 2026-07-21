# Database Migrations

Managed via **Liquibase** with 11 release changelogs.

**Master file:** `db.changelog-master.yaml`

---

## Migration Strategy

- **Idempotent**: Each changeset runs exactly once (tracked by Liquibase `DATABASECHANGELOG` table)
- **Fail-fast**: Duplicate changeset IDs will fail (no silent re-runs)
- **Forward-only**: Rollback is explicit via `make rollback` with `ROLLBACK_COUNT` parameter
- **Camunda integration**: Camunda engine schema is migrated alongside application schema

---

## Release History

| Release | Changesets | Purpose |
|---------|-----------|---------|
| **0001-foundation** | 1 | Create `report` table |
| **0002-case-management** | 2 | Create `case_number_sequence`, `case_record`, `case_assignment`, `case_status_history`, `audit_event` |
| **0003-workflow** | 1 | Create `workflow_instance` |
| **0004-evidence** | 2 | Add triage status to report, create `evidence`, `evidence_upload_session`, `evidence_version` |
| **0005-messaging** | 1 | Create `outbox_event`, `inbox_event`, `notification` |
| **0006-decision-appeal** | 2 | Add `workflow_type` to workflow_instance, create `recommendation/review`, `decision/version`, `sanction/obligation`, `appeal/decision` |
| **0007-case-auth** | 1 | Add `classification` to case_record |
| **0008-observability-notif** | 1 | Expand notification statuses to include SENT, FAILED |
| **0009-advanced-assignment** | 1 | Assignment rotation: released_at/by, superseded_by, is_active, active_case_id |
| **0010-advanced-relationships** | 1 | Create `case_relationship` |
| **0011-advanced-maintenance** | 1 | Add OVERDUE status, create `maintenance_operation_run`, add stored procedure `recalculate_overdue_sanction_obligations()` |

---

## Running Migrations

```bash
# Apply all pending migrations
make migrate

# Rollback N changesets
ROLLBACK_COUNT=3 make rollback

# Check migration status
make db-status
```

### Migration Process (`make migrate`)
1. Builds `sentinel-bootstrap` module
2. Runs `DatabaseMigrationMain` with a short-lived DataSource
3. Applies Liquibase changelog
4. Applies Camunda engine schema (if not already present)
5. Starts the application with `docker compose up -d app`

---

## PL/pgSQL Functions

### `generate_case_number(jurisdiction_code, type, year)`
- Atomically increments `case_number_sequence`
- Returns formatted string: `JKT-ENF-2026-000042`

### `recalculate_overdue_sanction_obligations(effectiveDate, requestedBy, runId)`
- Creates `maintenance_operation_run` record (status: RUNNING)
- Updates ACTIVE obligations past due_date to OVERDUE
- Records affected rows and completes the run record
- Wraps in transaction with table-level lock

---

## Index Strategy

| Table | Index Columns | Purpose |
|-------|--------------|---------|
| `report` | `(jurisdiction_code, created_at)` | Report listing by jurisdiction |
| `report` | `(jurisdiction_code, status, created_at)` | Triage queue |
| `case_record` | `(jurisdiction_code, assigned_unit_id, classification, created_at)` | Case listing with auth filters |
| `case_assignment` | `(active_case_id)` | Unique active assignment |
| `workflow_instance` | `(status)` | Active workflow queries |
