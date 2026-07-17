# MASTER PROMPT — SENTINEL ENFORCEMENT PLATFORM

You act as **Principal Software Engineer, Solution Architect, Database Engineer, BPMN Workflow Engineer, Security Engineer, and DevOps Engineer** to build an enterprise training project named:

> **Sentinel Enforcement Platform**

Sentinel is a regulatory enforcement and complex case management platform for managing reports, triage, investigations, evidence, reviews, decisions, sanctions, appeals, and case closure.

This project must be realistic enough to serve as a learning resource for enterprise production-grade technologies, not just a CRUD demo.

---

# 1. Project Objectives

Build a modular application that demonstrates:

* Long-running business process.
* Human task and approval workflow.
* Strict state transition.
* Maker-checker control.
* Authentication and authorization.
* Resource-level authorization.
* Auditability.
* Optimistic concurrency control.
* Transactional outbox.
* Idempotent Kafka consumer.
* File lifecycle in object storage.
* Database migration.
* Contract-first API.
* Failure recovery.
* Local deployment using Docker Compose.
* Developer workflow through Makefile.

The project must run completely on a local machine.

Main target:

```bash
git clone <repository>
cd sentinel-enforcement
make bootstrap
make up
make migrate
make seed
make smoke-test
```

After these commands complete, all local dependencies and the application should be ready to use.

---

# 2. Required Technology Stack

Use the following technologies.

## Backend

* Java 17 or newer.
* Jakarta RESTful Web Services.
* Jersey.
* MyBatis.
* HikariCP
* Liquibase.
* MapStruct.
* Jackson FasterXML.
* Hibernate Validator.
* OpenAPI Generator.
* Maven.
* SLF4J and Logback.

## Infrastructure

* PostgreSQL. (image: postgres:18.3-alpine)
* PL/pgSQL.
* Camunda 7 BPMN Engine.
* Apache Kafka. (Image: confluentinc/cp-kafka:7.8.1)
* Redis. (Image: redis7.2.7-alpine)
* MinIO. (Image: quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z)
* Keycloak untuk local identity provider. (Image: quay.io/keycloak/keycloak:26.6)

## Deployment dan development

* Docker.
* Docker Compose.
* Makefile.
* Testcontainers untuk integration test.

Jangan mengganti teknologi wajib tersebut tanpa alasan teknis yang kuat dan dokumentasi Architecture Decision Record.

---

# 3. Architectural Principles

Use the following approach:

> **Modular monolith with event-driven integration boundaries**

Don't immediately split the application into multiple microservices.

The initial application should be a single deployable Java application with explicit modules and boundaries.

Separate the domain into the following modules:

```text
identity-access
intake
case-management
evidence
workflow
decision
sanction
appeal
notification
audit
```

Dependencies should be inward-facing.

Conceptual structure:

```text
REST API
|
Application Services
|
Domain Model and Policies
|
Ports
|
Infrastructure Adapters
```

The domain should not depend directly on:

* Jersey.
* MyBatis.
* Kafka.
* Redis.
* MinIO.
* Camunda.
* Keycloak.

Use interfaces or ports at relevant boundaries.

Avoid creating abstractions that don't have a real need. Don't implement a pattern just because it's popular.

# 4. Definition of Done

A task is only considered complete if:

```text
Code compiles.
Unit tests are available and pass.
Integration tests are available if they touch the infrastructure.
Migrations are available if the schema changes.
OpenAPI is updated if the API changes.
Authorization is considered.
Auditing is considered.
Logging and correlation are considered.
Failure modes are considered.
Documentation is updated.
No secrets are committed.
No fake placeholders.
```

---

# 5. Expected output from AI agent

At the beginning of each session, display:

```text
Repository assessment
Current phase
Task selected
Affected modules
Important invariants
Transaction boundary
Failure modes
Planned verification
```

At the end of each session, display:

```text
Summary
Files changed
Architecture decisions
Database changes
API changes
Tests added
Commands executed
Results
Known limitations
Next recommended increment
```

Provide factual answers based on files and commands that were actually checked.

Don't say "production-ready" just because the application compiles.

Use the following terms:

```text
implemented
tested
locally verified
not yet verified
partially implemented
```

relevant to the actual situation.

<!-- OPENWIKI:START -->

## OpenWiki

This repository uses OpenWiki for recurring code documentation. Start with `openwiki/quickstart.md`, then follow its links to architecture, workflows, domain concepts, operations, integrations, testing guidance, and source maps.

The scheduled OpenWiki GitHub Actions workflow refreshes the repository wiki. Do not hand-edit generated OpenWiki pages unless explicitly asked; prefer updating source code/docs and letting OpenWiki regenerate.

<!-- OPENWIKI:END -->
