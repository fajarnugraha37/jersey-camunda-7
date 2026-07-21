# Technology Stack

## Backend

| Technology | Version | Purpose |
|-----------|---------|---------|
| Java | 21 | Runtime |
| Jakarta RESTful Web Services | 3.1.0 | JAX-RS API |
| Jersey | 3.1.9 | JAX-RS implementation |
| Grizzly | 3.0.2 | HTTP server |
| HK2 | 3.0.6 | Dependency injection |

## Persistence

| Technology | Version | Purpose |
|-----------|---------|---------|
| MyBatis | 3.5.19 | SQL mapper |
| MyBatis Guice | 3.5.19 | MyBatis integration |
| HikariCP | 5.1.0 | Connection pool |
| Liquibase | 4.29.2 | Database migrations |
| PostgreSQL JDBC | 42.7.4 | Database driver |

## Integration

| Technology | Version | Purpose |
|-----------|---------|---------|
| Kafka Client | 3.8.1 | Event messaging |
| MinIO SDK | 8.5.17 | Object storage |
| Camunda BPMN | 7.24.0 | Workflow engine |
| Camunda Spin | 7.24.0 | JSON/data handling |
| Nimbus JOSE+JWT | 9.47 | JWT verification |
| Keycloak | 26.6 | Identity provider |

## JSON & Validation

| Technology | Version | Purpose |
|-----------|---------|---------|
| Jackson FasterXML | 2.18.2 | JSON serialization |
| Jackson JSR310 | 2.18.2 | Java time module |
| Jackson JsonNullable | 0.2.1 | Nullable support |
| Hibernate Validator | 8.0.2.Final | Bean Validation |
| MapStruct | 1.6.3 | Object mapping |

## Testing

| Technology | Version | Purpose |
|-----------|---------|---------|
| JUnit 5 | 5.11.4 | Test framework |
| Testcontainers | 1.20.4 | Infrastructure-in-test |
| Testcontainers PostgreSQL | 1.20.4 | PostgreSQL module |
| Testcontainers Kafka | 1.20.4 | Kafka module |
| Testcontainers Keycloak | 3.6.0 | Keycloak module |
| Karate | 1.5.1 | Acceptance testing |
| Mockito | 5.14.2 | Mocking |

## Monitoring

| Technology | Version | Purpose |
|-----------|---------|---------|
| Micrometer | 1.14.4 | Metrics |
| SLF4J | 2.0.16 | Logging facade |
| Logback | 1.5.16 | Logging implementation |

## Build & Code Quality

| Technology | Version | Purpose |
|-----------|---------|---------|
| Maven | 3.9+ | Build tool |
| OpenAPI Generator | 7.10.0 | API code generation |
| Spotless | 2.43.0 | Code formatting |
| JaCoCo | 0.8.12 | Code coverage |

## Infrastructure (Docker)

| Service | Image | Version |
|---------|-------|---------|
| PostgreSQL | `postgres:18.3-alpine` | 18.3 |
| Kafka | `confluentinc/cp-kafka:7.8.1` | 7.8.1 |
| Redis | `redis:7.2.7-alpine` | 7.2.7 |
| MinIO | `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z` | 2025-09-07 |
| Keycloak | `quay.io/keycloak/keycloak:26.6` | 26.6 |
| Mailpit | `axllent/mailpit:latest` | latest |
