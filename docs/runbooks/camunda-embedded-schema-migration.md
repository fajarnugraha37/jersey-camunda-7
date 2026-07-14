# Runbook: Camunda Embedded Schema Migration

## Purpose

Use this runbook when bringing up a fresh environment or when the application fails to start because Camunda tables are missing.

## Sequence

1. Start infrastructure with `make up`.
2. Run `make migrate`.
3. Wait for the application container or local runtime to start.
4. Verify `GET /health` returns `200`.
5. Verify the expected BPMN definition is deployed by checking application startup logs for `Process Engine sentinel-workflow-engine created`.

## Expected behavior

- Application schema is created by Liquibase first.
- Camunda schema is created from official Camunda SQL resources second.
- Application startup uses `databaseSchemaUpdate=false`.
- If schema is missing, startup fails instead of auto-creating `ACT_*` tables.

## Rollback limitations

- Camunda schema creation is one-way in this increment.
- If migration fails midway, recreate the environment from a clean database backup or disposable local database.
- For future Camunda version upgrades, use official upgrade scripts before deploying the newer application binary.

## Operator notes

- Do not manually edit `ACT_*` tables.
- Do not re-enable `databaseSchemaUpdate=true` in runtime just to recover a broken deployment.
- If startup still fails after `make migrate`, inspect database privileges, schema ownership, and the migration logs from `DatabaseMigrationMain`.
