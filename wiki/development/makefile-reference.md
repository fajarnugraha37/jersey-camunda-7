# Makefile Reference

## Setup & Build

| Target | Command | Description |
|--------|---------|-------------|
| `help` | — | Print all targets with descriptions |
| `bootstrap` | `mvn dependency:go-offline` | Download all Maven dependencies |
| `clean` | `mvn clean` | Clean build artifacts |
| `compile` | `mvn compile -DskipTests` | Compile all modules |
| `package` | `mvn package -DskipTests` | Package as JAR |
| `format` | `mvn spotless:apply` | Apply code formatting |
| `lint` | `mvn spotless:check` | Check code formatting |

## Testing

| Target | Command | Description |
|--------|---------|-------------|
| `test` | `mvn verify` | Run all tests (unit + integration) |
| `unit-test` | `mvn test` | Run unit tests only |
| `integration-test` | `mvn verify -pl sentinel-integration-tests -am` | Run integration tests (Testcontainers) |
| `e2e-test` | Full integration tests module | Same as integration-test |
| `verify` | `mvn verify` | Full verification |
| `workflow-test` | Workflow unit tests + WorkflowTaskApiIT | Workflow-specific tests |
| `messaging-test` | MessagingReliabilityIT | Messaging reliability tests |
| `bpmn-validate` | `BpmnModelValidationTest` | Validate BPMN models |

## Karate Acceptance Tests

| Target | Command | Description |
|--------|---------|-------------|
| `karate-smoke` | `KarateSmokeIT` | Smoke test suite |
| `karate-regression` | `KarateRegressionIT` | Regression test suite |
| `karate-full` | `KarateFullIT` | Full acceptance suite |

## Docker Compose

| Target | Command | Description |
|--------|---------|-------------|
| `up` | `docker compose up -d` → `minio-init` | Start all infrastructure services |
| `down` | `docker compose down` | Stop all services |
| `restart` | `docker compose restart` | Restart all services |
| `reset` | `docker compose down -v` | Stop and destroy volumes (fresh start) |
| `ps` | `docker compose ps` | List service status |
| `logs` | `docker compose logs -f` | Tail all logs |
| `app-logs` | `docker compose logs -f app` | Tail application logs |
| `docker-build` | `docker compose build app` | Build application container |

## Database

| Target | Command | Description |
|--------|---------|-------------|
| `migrate` | Build + run migrations → start app | Apply all pending migrations |
| `rollback` | Build + rollback N changesets | Rollback (uses `ROLLBACK_COUNT`) |
| `db-status` | `docker compose ps postgres` | Check PostgreSQL status |
| `db-shell` | `psql` inside container | Open PostgreSQL shell |
| `db-reset` | `down -v` → `up -d postgres` | Reset and restart PostgreSQL |

## Infrastructure Management

| Target | Description |
|--------|-------------|
| `seed` | Run MinIO bucket init |
| `minio-init` | Create MinIO bucket |
| `smoke-test` | `curl http://localhost:8080/health` |
| `kafka-topics` | List Kafka topics |
| `kafka-consume` | Tail `case.lifecycle.v1` from beginning |
| `kafka-produce` | Produce sample `NotificationDispatchRequested` |
| `openapi-validate` | Validate OpenAPI generation |
| `dependency-check` | `mvn dependency:analyze` |

---

## Typical Workflow

```bash
# First time
make bootstrap
make up
make migrate
make seed
make smoke-test

# Development loop
make compile
make unit-test
make integration-test
make docker-build
make restart
make smoke-test

# Reset everything
make reset
make up
make migrate
```
