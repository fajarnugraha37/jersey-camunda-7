# Docker Compose Infrastructure

## Services

| Service | Image | Container | Depends On |
|---------|-------|-----------|------------|
| **postgres** | `postgres:18.3-alpine` | `sentinel-postgres` | — |
| **kafka** | `confluentinc/cp-kafka:7.8.1` | `sentinel-kafka` | — |
| **redis** | `redis:7.2.7-alpine` | `sentinel-redis` | — |
| **minio** | `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z` | `sentinel-minio` | — |
| **minio-init** | `quay.io/minio/mc:latest` | `sentinel-minio-init` | minio (healthy) |
| **keycloak** | `quay.io/keycloak/keycloak:26.6` | `sentinel-keycloak` | — |
| **mailpit** | `axllent/mailpit:latest` | `sentinel-mailpit` | — |
| **app** | Dockerfile (local build) | `sentinel-app` | All infra services |

---

## Port Mappings

| Service | Internal Port | Host Port |
|---------|--------------|-----------|
| postgres | 5432 | `5432` |
| kafka (PLAINTEXT_HOST) | 29092 | `29092` |
| redis | 6379 | `6379` |
| minio (API) | 9000 | `9000` |
| minio (Console) | 9001 | `9001` |
| keycloak | 8080 | `8081` |
| mailpit (SMTP) | 1025 | `1025` |
| mailpit (Web UI) | 8025 | `8025` |
| app | 8080 | `8080` |

---

## Service Details

### PostgreSQL
- Health check: `pg_isready -U sentinel`
- Volume: `sentinel-postgres-data` (persistent)
- Database: `sentinel`

### Kafka (KRaft mode)
- Single-node, no ZooKeeper
- Auto-creates topics with `auto.create.topics.enable=true`
- Controller + broker in one container

### Redis
- Health check: `redis-cli ping`
- Cache session store

### MinIO
- API endpoint on port 9000
- Console on port 9001
- Volume: `sentinel-minio-data` (persistent)
- Bucket created by `minio-init` one-shot container

### Keycloak
- Development mode (`--start-dev`)
- Imports `deployment/keycloak/realm/sentinel-realm.json` at startup
- Health check: `curl -f http://localhost:8080/realms/sentinel`

### Mailpit
- SMTP server for testing email
- Web UI at port 8025 for viewing captured emails

### Application Container
- Built from Dockerfile using `mvn package`
- Depends on all 6 infra services (healthy)
- Environment from `.env`

---

## Volumes

| Volume | Purpose |
|--------|---------|
| `sentinel-postgres-data` | Persistent PostgreSQL data |
| `sentinel-minio-data` | Persistent MinIO objects |
