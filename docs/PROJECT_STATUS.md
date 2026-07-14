# PROJECT_STATUS

## Current phase

Phase 4 workflow orchestration implemented and verified.

## Completed capabilities

- Repository assessed against the target architecture.
- Maven multi-module foundation created for `domain`, `application`, `api`, `persistence`, `bootstrap`, and `integration-tests`.
- `sentinel-security` module added for JWT verification and centralized authorization logic.
- `sentinel-workflow` module added for embedded Camunda runtime, BPMN deployment, workflow correlation, task query, and escalation handling.
- Health endpoint implemented.
- PostgreSQL-backed report create/get API implemented.
- Liquibase migration implemented for the initial `report` table.
- Validation, consistent error envelope, JSON configuration, and correlation ID response header implemented.
- OpenAPI Generator is wired into the `sentinel-api` build and now generates request/response models from `docs/api/openapi.yaml`.
- API request validation for non-empty report intake fields is enforced from the OpenAPI contract via `minLength` and generated bean validation annotations.
- Keycloak realm import, JWT bearer authentication filter, and centralized role/jurisdiction authorization are implemented for report endpoints.
- Local dummy users and realm bootstrap are implemented for Keycloak-based development.
- Case lifecycle domain model implemented with strict transition policy, role-aware transition ownership, and optimistic locking checks.
- Case persistence implemented with `case_record`, `case_assignment`, `case_status_history`, `audit_event`, and concurrency-safe `generate_case_number`.
- Case API implemented for create/get/list/assign/transition/audit operations.
- Workflow task API implemented for list, claim, and complete operations with cursor pagination, quick search, targeted search, and sort whitelisting.
- Investigator list visibility now filters to directly assigned cases; direct case read also enforces assignment on investigator-only actors.
- Case creation now starts the `regulatoryEnforcementCase` workflow and persists correlation in `workflow_instance`.
- Investigation task escalation is modeled with a non-interrupting boundary timer and writes deterministic audit evidence through an idempotent delegate path.
- Unit and integration tests implemented and locally verified with Maven and Testcontainers.
- Docker Compose runtime now includes PostgreSQL, Keycloak, and the application container.
- PostgreSQL 18 volume mount aligned with the image's required `/var/lib/postgresql` layout.

## Incomplete capabilities

- Messaging, storage, and dedicated audit modules are not implemented yet.
- Generated API interfaces are not used yet; the current increment generates and uses models only.
- Later-state prerequisites that depend on sanction or appeal aggregates are not yet modeled beyond transition-policy ownership rules.

## Known defects

- Original repository started as a Jersey hello-world template and did not match the required architecture.

## Architecture deviations

- Required module set is still only partially implemented beyond security to keep the current slice vertical and testable.
- OpenAPI code generation is wired for generated models, but resource interfaces are still handwritten.
- Docker Compose currently covers the services needed for the report, case, auth, and embedded workflow slice, not the full target platform stack.
- Audit persistence lives in `sentinel-persistence` as part of the case vertical slice; a dedicated `sentinel-audit` module does not exist yet.

## Test status

- `mvn -q -DskipTests compile` completed successfully.
- `mvn -q test` completed successfully.
- `mvn -q -pl sentinel-integration-tests -am "-Dit.test=WorkflowTaskApiIT" verify` completed successfully.
- `make workflow-test` completed successfully after fixing the multi-module Surefire pattern issue in `bpmn-validate`.
- `make bpmn-validate` completed successfully.
- `make bpmn-deploy` completed successfully and documents the embedded deployment behavior.
- `mvn -q spotless:apply` completed successfully.
- `mvn -q -pl sentinel-integration-tests -am verify` completed successfully.
- `mvn -q verify` completed successfully.
- `mvn -q -pl sentinel-api -am generate-sources` completed successfully and produced compile-consumed generated models.
- Integration verification now covers `GET /health`, report endpoints, case lifecycle happy path, investigator visibility filtering, workflow task list/claim/complete flows, and `409` conflict envelopes for invalid transition and stale version scenarios.
- Liquibase duplicate changelog detection is now fail-fast via `ERROR`, and the integration-test classpath no longer carries the duplicate persistence changelog source.

## Infrastructure status

- PostgreSQL, Keycloak, and application Docker Compose runtime are verified via `docker compose up` plus host-side authenticated smoke requests using `localhost`.
- PostgreSQL and Keycloak integration runtimes are locally verified through Testcontainers.

## Next recommended task

Move into the next vertical slice after workflow: add storage-backed evidence intake with presigned upload/finalize flow while preserving database-owned business state and idempotent external-side-effect handling.
