---
type: Reference
title: Sentinel Enforcement Platform Quickstart
description: Entry point for the Sentinel Enforcement Platform code wiki. Covers project overview, local setup, architecture summary, and navigation to all major areas.
tags: [getting-started, architecture, sentinel, enforcement]
---

# Sentinel Enforcement Platform Quickstart

Sentinel Enforcement Platform is an enterprise-grade training project for **regulatory enforcement and complex case management**. It spans phases 0-8 including foundation, intake, authentication/authorization, case lifecycle, embedded Camunda workflow, evidence intake (MinIO), Kafka reliability, aggregate decision/appeal/sanction, case-level authorization, and observability.

Built as a **modular monolith** with hexagonal architecture and event-driven integration boundaries.

## Quick Setup

**Prerequisites:** Java 21+, Maven 3.9+, Docker Desktop, GNU Make.

```bash
# 1. Environment (optional -- copy to override defaults)
cp .env.example .env

# 2. Restore Maven dependencies
make bootstrap

# 3. Start infrastructure: PostgreSQL, Kafka, Redis, MinIO, Keycloak, Mailpit
make up

# 4. Run DB migration + Camunda schema, then start the application
make migrate

# 5. (Optional) Run idempotent seed helpers
make seed

# 6. Verify the app is running
make smoke-test
```

Full command reference: `make help`.

## Architecture Summary

The application is a **modular monolith** with strict dependency direction: **API &rarr; Application &rarr; Domain &larr; Persistence, Workflow, Messaging, Storage, Security**.

| Module | Responsibility | Key Technologies |
|---|---|---|
| `sentinel-domain` | Aggregates, entities, value objects, state machines, business invariants | Pure Java |
| `sentinel-application` | Use cases, command/query objects, ports, authorization orchestration | Jakarta |
| `sentinel-api` | Jersey REST resources, bean validation, error envelopes, OpenAPI-generated DTOs | Jersey, Jackson, MapStruct, Hibernate Validator |
| `sentinel-persistence` | MyBatis mappers, repository adapters, Liquibase changelogs | MyBatis, HikariCP, Liquibase, PostgreSQL |
| `sentinel-workflow` | Embedded Camunda engine, BPMN deployment, task/administration adapters | Camunda 7 BPMN |
| `sentinel-messaging` | Kafka outbox publisher, notification consumer, retry/DLQ routing | Apache Kafka |
| `sentinel-storage` | MinIO adapter for evidence object storage | MinIO SDK |
| `sentinel-security` | Keycloak JWT verification, role-/scope-based authorization | Nimbus JOSE, Keycloak |
| `sentinel-observability` | Composite health checks, request metrics, correlation context | JDBC, Socket |
| `sentinel-bootstrap` | Dependency assembly (HK2), server startup, migration entrypoints | Grizzly, HK2 |
| `sentinel-integration-tests` | Testcontainers integration tests, Karate BDD suites | Testcontainers, Karate |

## Documentation Map

| Page | Description |
|---|---|
| [Architecture Overview](/openwiki/architecture/overview.md) | Module boundaries, hexagonal layering, CDI wiring, cross-cutting concerns |
| [Domain Concepts](/openwiki/domain/concepts.md) | Core entities, state machines, domain invariants, business rules |
| [API Overview](/openwiki/api/overview.md) | Full REST surface, list query pattern, error handling, security flow |
| [BPMN Workflows](/openwiki/workflows/bpmn.md) | Embedded Camunda integration, process definitions, reconciliation tooling |
| [Messaging &amp; Storage](/openwiki/integrations/messaging-storage.md) | Transactional outbox, Kafka reliability, MinIO evidence lifecycle, observability |
| [Operations &amp; Runbooks](/openwiki/operations/runbooks.md) | Schema migration, troubleshooting guides, configuration reference, default users |
| [Testing Strategy](/openwiki/testing/strategy.md) | Unit tests, integration tests, Karate coverage, key test classes |

## Key Technology Stack

- Java 21 &middot; Jersey 3.1.9 &middot; Grizzly &middot; HK2 (DI)
- MyBatis 3.5.19 &middot; HikariCP 6.3 &middot; Liquibase 4.31.1 &middot; PostgreSQL 18.3
- Embedded Camunda 7.24.0 &middot; Apache Kafka 3.8.1
- MinIO (object storage) &middot; Keycloak 26.6 (identity)
- Redis 7.2.7 &middot; Mailpit (SMTP testing)
- MapStruct 1.6.3 &middot; Jackson 2.18.2 &middot; Hibernate Validator 9
- Testcontainers 1.20.5 &middot; Karate 2.1.1 &middot; JUnit 5.11.4

## Current Phase

Phase 8 is fully implemented: case-level authorization (jurisdiction, assigned unit, classification clearance, conflict-of-interest), observability/metrics, notification command/result flow, and hardened messaging reliability. See [Project Status](/docs/PROJECT_STATUS.md) for details.

## Backlog

The following areas are not yet fully documented in this wiki:

| Area | Source Anchor | Reason Deferred |
|---|---|---|
| DB schema evolution details | `/sentinel-persistence/src/main/resources/db/changelog/` | Well-documented via Liquibase changelog files themselves; tracking 11 releases from `0001-foundation` to `0011-maintenance-operations` |
| Postman collection | `/docs/api/postman/sentinel-enforcement-platform.postman_collection.json` | Supplementary tooling, not core architecture |
| BPMN diagram details | `/sentinel-workflow/src/main/resources/bpmn/` | Detailed BPMN modeling and element-level documentation lives in `docs/plan/enhance-bpmn/` |
| Advanced persistence design | `/docs/plan/advanced-persistence/` | Architectural decision records for schema additions, not runtime behaviour |
| Karate flow coverage plan | `/docs/plan/karate-centric/` | Planning documents, superseded by actual Karate features
