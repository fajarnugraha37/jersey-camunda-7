# PROJECT_STATUS

## Current phase

Phase 1 foundation vertical slice implemented.

## Completed capabilities

- Repository assessed against the target architecture.
- Maven multi-module foundation created for `domain`, `application`, `api`, `persistence`, `bootstrap`, and `integration-tests`.
- Health endpoint implemented.
- PostgreSQL-backed report create/get API implemented.
- Liquibase migration implemented for the initial `report` table.
- Validation, consistent error envelope, JSON configuration, and correlation ID response header implemented.
- Unit and integration tests implemented and locally verified with Maven and Testcontainers.
- Docker Compose runtime verified end-to-end for PostgreSQL and the application container.
- PostgreSQL 18 volume mount aligned with the image's required `/var/lib/postgresql` layout.

## Incomplete capabilities

- Authentication, authorization, workflow, messaging, storage, and audit modules are not implemented yet.
- OpenAPI generator integration is not wired into generated interfaces/models yet.

## Known defects

- Original repository started as a Jersey hello-world template and did not match the required architecture.
- Liquibase emits duplicate changelog warnings during multi-module test classpath execution because both persistence and shaded bootstrap artifacts expose the same changelog resources.
- Jersey still emits a non-blocking WADL/JAXB warning at startup because WADL support is present without a JAXB implementation.

## Architecture deviations

- Required module set is only partially implemented in the current increment to keep the first slice vertical and testable.
- OpenAPI code generation is not yet wired into compile-time generated interfaces.
- Docker Compose currently covers only the foundation services needed for this slice, not the full target platform stack.

## Test status

- `mvn -q -DskipTests compile` completed successfully.
- `mvn -q test` completed successfully.
- `mvn -q -pl sentinel-integration-tests -am verify` completed successfully.
- `mvn -q spotless:apply` completed successfully.
- `mvn -q verify` completed successfully.
- Containerized smoke checks against `http://localhost:8080` completed successfully for `GET /health`, `POST /api/v1/reports`, and `GET /api/v1/reports/{reportId}`.

## Infrastructure status

- PostgreSQL Docker Compose service is verified via `docker compose up` and live smoke requests.
- PostgreSQL integration runtime is locally verified through Testcontainers.

## Next recommended task

Wire OpenAPI Generator into the build so the contract-first requirement is enforced automatically, then move into Phase 2 authentication and authorization.
