SHELL := pwsh.exe
.SHELLFLAGS := -NoLogo -Command

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
	Write-Host "  verify             Run Maven verify"
	Write-Host "  package            Build distributable artifacts"
	Write-Host "  format             Apply Java and pom formatting"
	Write-Host "  openapi-validate   Validate docs/api/openapi.yaml"
	Write-Host "  up                 Start postgres and app containers"
	Write-Host "  down               Stop compose services"
	Write-Host "  restart            Restart compose services"
	Write-Host "  ps                 Show compose service status"
	Write-Host "  logs               Tail compose logs"
	Write-Host "  app-logs           Tail app logs"
	Write-Host "  migrate            Run Liquibase migration from local Maven runtime"
	Write-Host "  db-status          Show PostgreSQL container status"
	Write-Host "  db-shell           Open psql shell inside postgres container"
	Write-Host "  seed               No-op for current phase"
	Write-Host "  smoke-test         Call health endpoint"

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
	throw "workflow-test is not implemented in the current phase."

messaging-test:
	throw "messaging-test is not implemented in the current phase."

e2e-test:
	throw "e2e-test is not implemented in the current phase."

verify:
	mvn -q verify

package:
	mvn -q -DskipTests package

openapi-generate:
	throw "openapi-generate is not wired yet; the current increment keeps the contract in docs/api/openapi.yaml."

openapi-validate:
	mvn -q org.openapitools:openapi-generator-maven-plugin:$(openapi.generator.version):validate -DinputSpec=docs/api/openapi.yaml

up:
	docker compose up -d --build postgres app

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
	throw "docker-push-local is not implemented in the current phase."

migrate:
	mvn -q -pl sentinel-bootstrap -am exec:java -Dexec.mainClass=com.sentinel.enforcement.bootstrap.DatabaseMigrationMain

rollback:
	throw "rollback is not implemented in the current phase."

db-status:
	docker compose ps postgres

db-shell:
	docker compose exec postgres psql -U sentinel -d sentinel

db-reset:
	docker compose down -v
	docker compose up -d postgres

seed:
	Write-Host "No seed data is defined for the current phase."

smoke-test:
	Invoke-RestMethod -Method Get -Uri http://localhost:8080/health | ConvertTo-Json -Depth 5

kafka-topics:
	throw "kafka-topics is not implemented in the current phase."

kafka-consume:
	throw "kafka-consume is not implemented in the current phase."

kafka-produce:
	throw "kafka-produce is not implemented in the current phase."

minio-init:
	throw "minio-init is not implemented in the current phase."

keycloak-import:
	throw "keycloak-import is not implemented in the current phase."

bpmn-validate:
	throw "bpmn-validate is not implemented in the current phase."

bpmn-deploy:
	throw "bpmn-deploy is not implemented in the current phase."

format:
	mvn -q spotless:apply

lint:
	mvn -q spotless:check

dependency-check:
	mvn -q dependency:analyze
