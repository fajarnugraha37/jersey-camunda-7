# Development Setup

## Prerequisites

- **Java 21+** (JDK, not JRE)
- **Apache Maven 3.9+**
- **Docker Desktop** (with Docker Compose V2)
- **PowerShell 7+** (Windows) or **bash** (Linux/macOS)

---

## Quick Start

```bash
# 1. Clone the repository
git clone <repository-url>
cd sentinel-enforcement

# 2. Download Maven dependencies
make bootstrap

# 3. Start infrastructure (PostgreSQL, Kafka, Redis, MinIO, Keycloak, Mailpit)
make up

# 4. Run database migrations
make migrate

# 5. Seed initial data (MinIO bucket)
make seed

# 6. Verify
make smoke-test
```

---

## IDE Setup

### IntelliJ IDEA
1. Open project root (`pom.xml` as project)
2. Enable annotation processing (MapStruct)
3. Import Maven projects automatically
4. Set Java SDK to 21+
5. Install Lombok plugin (if needed — though project uses records)

### VS Code
1. Install "Extension Pack for Java"
2. Open project root
3. VS Code detects Maven project automatically

---

## Running Without Docker

For local development without Docker, you need:
- PostgreSQL 18 running locally on port 5432
- Kafka 3.8 running locally on port 29092
- Redis 7.2 running locally on port 6379
- MinIO running locally on port 9000
- Keycloak 26.6 running locally on port 8081
- Mailpit running locally on port 1025

**Recommended:** Use Docker Compose for all infrastructure.

---

## Environment Variables

Copy `.env.example` to `.env` and adjust as needed:

```bash
cp .env.example .env
```

All configuration is via environment variables with sensible defaults.
[Full configuration reference](../operations/configuration.md)

---

## Common Development Commands

```bash
# Compile only
make compile

# Run unit tests
make unit-test

# Run integration tests (requires Docker)
make integration-test

# Run Karate smoke tests (requires running app)
make karate-smoke

# Format code
make format

# Run full verification
make verify
```

### Running a Single Test

```bash
mvn test -pl sentinel-domain -Dtest=CaseRecordTest

mvn verify -pl sentinel-integration-tests -am \
  -Dit.test=CaseApiIT
```
