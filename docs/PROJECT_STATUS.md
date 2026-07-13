# PROJECT_STATUS

## Current phase

Phase 2 authentication and authorization implemented and verified.

## Completed capabilities

- Repository assessed against the target architecture.
- Maven multi-module foundation created for `domain`, `application`, `api`, `persistence`, `bootstrap`, and `integration-tests`.
- `sentinel-security` module added for JWT verification and centralized authorization logic.
- Health endpoint implemented.
- PostgreSQL-backed report create/get API implemented.
- Liquibase migration implemented for the initial `report` table.
- Validation, consistent error envelope, JSON configuration, and correlation ID response header implemented.
- OpenAPI Generator is wired into the `sentinel-api` build and now generates request/response models from `docs/api/openapi.yaml`.
- API request validation for non-empty report intake fields is enforced from the OpenAPI contract via `minLength` and generated bean validation annotations.
- Keycloak realm import, JWT bearer authentication filter, and centralized role/jurisdiction authorization are implemented for report endpoints.
- Local dummy users and realm bootstrap are implemented for Keycloak-based development.
- Unit and integration tests implemented and locally verified with Maven and Testcontainers.
- Docker Compose runtime now includes PostgreSQL, Keycloak, and the application container.
- PostgreSQL 18 volume mount aligned with the image's required `/var/lib/postgresql` layout.

## Incomplete capabilities

- Workflow, messaging, storage, and audit modules are not implemented yet.
- Generated API interfaces are not used yet; the current increment generates and uses models only.

## Known defects

- Original repository started as a Jersey hello-world template and did not match the required architecture.
- Liquibase emits duplicate changelog warnings during multi-module test classpath execution because both persistence and shaded bootstrap artifacts expose the same changelog resources.
- Jersey still emits a non-blocking WADL/JAXB warning at startup because WADL support is present without a JAXB implementation.

## Architecture deviations

- Required module set is still only partially implemented beyond security to keep the current slice vertical and testable.
- OpenAPI code generation is wired for generated models, but resource interfaces are still handwritten.
- Docker Compose currently covers the services needed for the report + auth slice, not the full target platform stack.

## Test status

- `mvn -q -DskipTests compile` completed successfully.
- `mvn -q test` completed successfully.
- `mvn -q -pl sentinel-integration-tests -am verify` completed successfully.
- `mvn -q spotless:apply` completed successfully.
- `mvn -q verify` completed successfully.
- `mvn -q -pl sentinel-api -am generate-sources` completed successfully and produced compile-consumed generated models.
- Integration verification now covers `GET /health`, `POST /api/v1/reports`, and `GET /api/v1/reports/{reportId}` under authenticated and unauthorized scenarios.

## Infrastructure status

- PostgreSQL, Keycloak, and application Docker Compose runtime are verified via `docker compose up` plus host-side authenticated smoke requests using `localhost`.
- PostgreSQL and Keycloak integration runtimes are locally verified through Testcontainers.

## Next recommended task

Move into Phase 3 case lifecycle: introduce `CaseRecord`, optimistic locking, state transition policy, and status history.
