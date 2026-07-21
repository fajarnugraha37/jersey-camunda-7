# Quickstart

From `git clone` to running application in 5 minutes.

---

## Prerequisites

- Java 21+ (JDK)
- Apache Maven 3.9+
- Docker & Docker Compose
- PowerShell 7+ (Windows) or bash (Linux/macOS)

---

## Setup

```bash
# 1. Clone and enter the repository
git clone <repository-url>
cd sentinel-enforcement

# 2. Download Maven dependencies
make bootstrap

# 3. Start all infrastructure services
make up

# 4. Run database migrations
make migrate

# 5. Seed initial data (MinIO bucket)
make seed

# 6. Verify everything works
make smoke-test
```

---

## What Each Command Does

| Step | Command | What Happens |
|------|---------|--------------|
| Bootstrap | `make bootstrap` | `mvn dependency:go-offline` — caches all Maven dependencies |
| Infrastructure | `make up` | Starts 7 Docker containers: PostgreSQL 18.3, Kafka 7.8.1 (KRaft), Redis 7.2.7, MinIO, Keycloak 26.6, Mailpit, plus MinIO bucket init |
| Migrate | `make migrate` | Runs Liquibase migrations + Camunda engine schema creation |
| Seed | `make seed` | Runs MinIO bucket bootstrap (creates `sentinel-evidence` bucket) |
| Smoke test | `make smoke-test` | Calls `GET /health` — expects `{"status":"UP"}` |

---

## Verify

```bash
# Health endpoint
curl http://localhost:8080/health

# Expected response (200 OK):
# {"status":"UP","dependencies":{"database":"UP","kafka":"UP","redis":"UP","mailpit":"UP","workflow":"UP"},"timestamp":"2026-07-22T00:00:00Z"}
```

---

## Test Users

All 14 users have password `sentinel`.

| Username | Role | Jurisdiction |
|----------|------|-------------|
| `intake-jkt` | CASE_INTAKE_OFFICER | JKT |
| `intake-bdg` | CASE_INTAKE_OFFICER | BDG |
| `triage-jkt` | TRIAGE_OFFICER | JKT |
| `investigator-jkt` | INVESTIGATOR | JKT |
| `reviewer-jkt` | CASE_REVIEWER | JKT |
| `decision-jkt` | DECISION_MAKER | JKT |
| `appeal-jkt` | APPEAL_OFFICER | JKT |
| `supervisor-jkt` | SUPERVISOR | JKT |
| `auditor-jkt` | AUDITOR | JKT |
| `system-admin` | SYSTEM_ADMIN | JKT, BDG |

Full list: [Test Users](reference/test-users.md)

---

## Smoke Test via API

```bash
# Get auth token
TOKEN=$(curl -s -X POST http://localhost:8081/realms/sentinel/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=sentinel-api" \
  -d "username=intake-jkt" \
  -d "password=sentinel" \
  -d "grant_type=password" | jq -r '.access_token')

# Create a report
curl -s -X POST http://localhost:8080/api/v1/reports \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Test Report",
    "description": "Smoke test",
    "jurisdictionCode": "JKT",
    "reporterName": "Tester"
  }'
```

---

## Common Make Commands

| Command | Purpose |
|---------|---------|
| `make compile` | Compile all modules |
| `make test` | Run all tests (unit + integration) |
| `make unit-test` | Run unit tests only |
| `make integration-test` | Run integration tests |
| `make karate-smoke` | Run Karate smoke tests |
| `make reset` | Destroy all Docker volumes (fresh start) |
| `make logs` | Tail all container logs |

Full reference: [Makefile Reference](development/makefile-reference.md)

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| `make up` → port conflict | Port 5432/6379/8080 etc. in use | Stop conflicting service or change port in `.env` |
| `make migrate` → connection refused | PostgreSQL not ready | `make up` again and wait 10s |
| Health endpoint → DB: DOWN | Migrations not run | `make migrate` |
| `401 Unauthorized` | Wrong token or expired | Re-authenticate against Keycloak |
| `403 Forbidden` | Wrong role/jurisdiction | Check [Authorization](security/authorization.md) |
