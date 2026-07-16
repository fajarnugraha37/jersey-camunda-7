SHELL := pwsh.exe
.SHELLFLAGS := -NoLogo -Command
ROLLBACK_COUNT ?= 1
LOCAL_RUNTIME_ENV := $$env:HTTP_PORT = if ($$env:HTTP_PORT) { $$env:HTTP_PORT } else { '8080' }; $$env:DB_URL = if ($$env:DB_URL) { $$env:DB_URL } else { 'jdbc:postgresql://localhost:5432/sentinel' }; $$env:DB_USERNAME = if ($$env:DB_USERNAME) { $$env:DB_USERNAME } else { 'sentinel' }; $$env:DB_PASSWORD = if ($$env:DB_PASSWORD) { $$env:DB_PASSWORD } else { 'sentinel' }; $$env:KAFKA_BOOTSTRAP_SERVERS = if ($$env:KAFKA_BOOTSTRAP_SERVERS) { $$env:KAFKA_BOOTSTRAP_SERVERS } else { 'localhost:29092' }; $$env:REDIS_HOST = if ($$env:REDIS_HOST) { $$env:REDIS_HOST } else { 'localhost' }; $$env:REDIS_PORT = if ($$env:REDIS_PORT) { $$env:REDIS_PORT } else { '6379' }; $$env:MAILPIT_SMTP_HOST = if ($$env:MAILPIT_SMTP_HOST) { $$env:MAILPIT_SMTP_HOST } else { 'localhost' }; $$env:MAILPIT_SMTP_PORT = if ($$env:MAILPIT_SMTP_PORT) { $$env:MAILPIT_SMTP_PORT } else { '1025' }; $$env:NOTIFICATION_FROM_EMAIL = if ($$env:NOTIFICATION_FROM_EMAIL) { $$env:NOTIFICATION_FROM_EMAIL } else { 'sentinel@local.test' }; $$env:NOTIFICATION_TO_EMAIL = if ($$env:NOTIFICATION_TO_EMAIL) { $$env:NOTIFICATION_TO_EMAIL } else { 'ops@local.test' }; $$env:MINIO_ENDPOINT = if ($$env:MINIO_ENDPOINT) { $$env:MINIO_ENDPOINT } else { 'http://localhost:9000' }; $$env:MINIO_ACCESS_KEY = if ($$env:MINIO_ACCESS_KEY) { $$env:MINIO_ACCESS_KEY } else { 'sentinel' }; $$env:MINIO_SECRET_KEY = if ($$env:MINIO_SECRET_KEY) { $$env:MINIO_SECRET_KEY } else { 'sentinel-secret' }; $$env:MINIO_EVIDENCE_BUCKET = if ($$env:MINIO_EVIDENCE_BUCKET) { $$env:MINIO_EVIDENCE_BUCKET } else { 'sentinel-evidence' }; $$env:EVIDENCE_UPLOAD_URL_TTL = if ($$env:EVIDENCE_UPLOAD_URL_TTL) { $$env:EVIDENCE_UPLOAD_URL_TTL } else { 'PT15M' }; $$env:EVIDENCE_DOWNLOAD_URL_TTL = if ($$env:EVIDENCE_DOWNLOAD_URL_TTL) { $$env:EVIDENCE_DOWNLOAD_URL_TTL } else { 'PT10M' }; $$env:KEYCLOAK_ISSUER = if ($$env:KEYCLOAK_ISSUER) { $$env:KEYCLOAK_ISSUER } else { 'http://localhost:8081/realms/sentinel' }; $$env:KEYCLOAK_AUDIENCE = if ($$env:KEYCLOAK_AUDIENCE) { $$env:KEYCLOAK_AUDIENCE } else { 'sentinel-api' }; $$env:KEYCLOAK_JWKS_URL = if ($$env:KEYCLOAK_JWKS_URL) { $$env:KEYCLOAK_JWKS_URL } else { 'http://localhost:8081/realms/sentinel/protocol/openid-connect/certs' }; $$env:WORKFLOW_INVESTIGATION_ESCALATION_DURATION = if ($$env:WORKFLOW_INVESTIGATION_ESCALATION_DURATION) { $$env:WORKFLOW_INVESTIGATION_ESCALATION_DURATION } else { 'PT30M' };

.PHONY: help bootstrap clean compile test unit-test integration-test workflow-test messaging-test e2e-test verify package \
	openapi-generate openapi-validate up down restart reset ps logs app-logs docker-build docker-push-local \
	migrate rollback db-status db-shell db-reset seed smoke-test kafka-topics kafka-consume kafka-produce \
	minio-init keycloak-import bpmn-validate bpmn-deploy format lint dependency-check

help:
	Write-Host "Available targets:"
	Write-Host "  bootstrap          Restore Maven dependencies for local development"
	Write-Host "  clean              Remove compiled artifacts"
	Write-Host "  compile            Compile all modules"
	Write-Host "  test               Run unit and integration tests"
	Write-Host "  unit-test          Run unit tests"
	Write-Host "  integration-test   Run integration tests with Testcontainers"
	Write-Host "  workflow-test      Run workflow-focused unit and integration tests"
	Write-Host "  messaging-test     Run messaging-focused integration tests"
	Write-Host "  e2e-test           Run the phase 6 end-to-end integration slice"
	Write-Host "  verify             Run Maven verify"
	Write-Host "  package            Build distributable artifacts"
	Write-Host "  format             Apply Java and pom formatting"
	Write-Host "  openapi-validate   Validate docs/api/openapi.yaml"
	Write-Host "  up                 Start postgres, kafka, redis, keycloak, mailpit, and minio containers for schema migration"
	Write-Host "  down               Stop compose services"
	Write-Host "  restart            Restart compose services"
	Write-Host "  ps                 Show compose service status"
	Write-Host "  logs               Tail compose logs"
	Write-Host "  app-logs           Tail app logs"
	Write-Host "  docker-build       Build the application image through Docker Compose"
	Write-Host "  docker-push-local  Build and tag the app image in the local Docker daemon"
	Write-Host "  migrate            Run application plus Camunda schema migration, then start app"
	Write-Host "  rollback           Roll back the latest Liquibase changesets (override with ROLLBACK_COUNT=n)"
	Write-Host "  db-status          Show PostgreSQL container status"
	Write-Host "  db-shell           Open psql shell inside postgres container"
	Write-Host "  seed               Re-run idempotent local bootstrap helpers such as MinIO bucket init"
	Write-Host "  smoke-test         Call health endpoint"
	Write-Host "  kafka-topics       List Kafka topics inside the local broker"
	Write-Host "  kafka-consume      Tail the case lifecycle topic from the beginning"
	Write-Host "  kafka-produce      Produce a sample notification command message"
	Write-Host "  minio-init         Ensure the MinIO evidence bucket exists"
	Write-Host "  bpmn-validate      Validate the embedded Camunda BPMN model"
	Write-Host "  bpmn-deploy        Explain embedded BPMN deployment behavior"

bootstrap:
	mvn -q -DskipTests dependency:go-offline

clean:
	mvn -q clean

compile:
	mvn -q -DskipTests compile

test:
	mvn -q verify

unit-test:
	mvn -q test

integration-test:
	mvn -q -pl sentinel-integration-tests -am verify

workflow-test:
	mvn -q -pl sentinel-workflow -am test
	mvn -q -pl sentinel-integration-tests -am "-Dit.test=WorkflowTaskApiIT" verify

messaging-test:
	mvn -q -pl sentinel-integration-tests -am "-Dit.test=MessagingReliabilityIT" verify

e2e-test:
	mvn -q -pl sentinel-integration-tests -am verify

verify:
	mvn -q verify

package:
	mvn -q -DskipTests package

openapi-generate:
	mvn -q -pl sentinel-api -am generate-sources

openapi-validate:
	mvn -q -pl sentinel-api -am generate-sources

up:
	docker compose up -d --build postgres kafka redis minio keycloak mailpit
	docker compose up minio-init

down:
	docker compose down

restart:
	docker compose restart

reset:
	docker compose down -v

ps:
	docker compose ps

logs:
	docker compose logs -f

app-logs:
	docker compose logs -f app

docker-build:
	docker compose build app

docker-push-local:
	docker build -t sentinel-app:local -f Dockerfile .

migrate:
	mvn -q -pl sentinel-bootstrap -am -DskipTests install
	$(LOCAL_RUNTIME_ENV) mvn -q -f sentinel-bootstrap/pom.xml exec:java "-Dexec.mainClass=com.sentinel.enforcement.bootstrap.DatabaseMigrationMain"
	docker compose up -d --build app

rollback:
	mvn -q -pl sentinel-bootstrap -am -DskipTests install
	$(LOCAL_RUNTIME_ENV) mvn -q -f sentinel-bootstrap/pom.xml exec:java "-Dexec.mainClass=com.sentinel.enforcement.bootstrap.DatabaseRollbackMain" "-Dexec.args=$(ROLLBACK_COUNT)"

db-status:
	docker compose ps postgres

db-shell:
	docker compose exec postgres psql -U sentinel -d sentinel

db-reset:
	docker compose down -v
	docker compose up -d postgres

seed:
	$(MAKE) minio-init

smoke-test:
	Invoke-RestMethod -Method Get -Uri http://localhost:8080/health | ConvertTo-Json -Depth 5

kafka-topics:
	docker compose exec kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --list"

kafka-consume:
	docker compose exec kafka bash -lc "kafka-console-consumer --bootstrap-server kafka:9092 --topic case.lifecycle.v1 --from-beginning"

kafka-produce:
	docker compose exec kafka bash -lc "printf '{\"eventId\":\"00000000-0000-0000-0000-000000000001\",\"eventType\":\"NotificationDispatchRequested\",\"eventVersion\":1,\"aggregateType\":\"Notification\",\"aggregateId\":\"00000000-0000-0000-0000-000000000002\",\"occurredAt\":\"2026-07-16T00:00:00Z\",\"correlationId\":\"sample-correlation-id\",\"causationId\":null,\"actor\":{\"type\":\"SYSTEM\",\"id\":\"make-kafka-produce\"},\"payload\":{\"notificationId\":\"00000000-0000-0000-0000-000000000002\",\"caseId\":\"00000000-0000-0000-0000-000000000003\",\"notificationType\":\"ManualSmoke\",\"title\":\"Sample notification\",\"body\":\"Generated by make kafka-produce\",\"toEmail\":\"ops@local.test\",\"fromEmail\":\"sentinel@local.test\",\"channel\":\"EMAIL\"}}\n' | kafka-console-producer --bootstrap-server kafka:9092 --topic notification.command.v1"

minio-init:
	docker compose up minio-init

keycloak-import:
	docker compose up -d keycloak

bpmn-validate:
	mvn -q -pl sentinel-workflow -am "-Dtest=BpmnModelValidationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test

bpmn-deploy:
	Write-Host "BPMN deployment is automatic on application startup through the embedded Camunda engine."

format:
	mvn -q spotless:apply

lint:
	mvn -q spotless:check

dependency-check:
	mvn -q dependency:analyze
