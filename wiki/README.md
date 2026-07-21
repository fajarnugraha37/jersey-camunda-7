# Sentinel Enforcement Platform — Wiki

> **Version:** 0.1.0-SNAPSHOT &nbsp;|&nbsp; **Java:** 21 &nbsp;|&nbsp; **Build:** Maven 3.9+ &nbsp;|&nbsp; **Infra:** Docker Compose

Regulatory enforcement and complex case management platform. Modular hexagonal monolith with event-driven integration boundaries.

---

## Quick Start

| Page | Description |
|------|-------------|
| [Quickstart](quickstart.md) | From `git clone` to running in 5 minutes |

---

## Architecture

| Page | Description |
|------|-------------|
| [Overview](architecture/overview.md) | Hexagonal modular monolith — layers, dependency rules, principles |
| [Module Dependencies](architecture/module-dependencies.md) | Maven module graph, dependency direction, boundary rules |
| [Architecture Decision Records](architecture/decision-records.md) | ADR-001 through ADR-010 — rationale behind every key decision |

---

## Domain

| Page | Description |
|------|-------------|
| [Aggregates](domain/aggregates.md) | 7 aggregates — Report, CaseRecord, Evidence, Recommendation, Decision, Sanction, Appeal |
| [State Machine](domain/state-machine.md) | 10-state case lifecycle — transitions, roles, guards |
| [Permissions](domain/permissions.md) | 30 Permission enum values, role mapping, 7-axis authorization model |

---

## API

| Page | Description |
|------|-------------|
| [Endpoints](api/endpoints.md) | All 30 REST endpoints — paths, methods, auth, query params |
| [Error Handling](api/error-handling.md) | 26 exception mappers, RFC 7807 envelope, correlation IDs |
| [Auth Flow](api/auth-flow.md) | Bearer token → JWT verification → 7-axis authorization |
| [Endpoint Call Traces](api/endpoint-call-traces.md) | HTTP resource → application service → repository/port → side effects |

---

## Persistence

| Page | Description |
|------|-------------|
| [Database Schema](persistence/schema.md) | All 23 tables, relationships, constraints, indexes |
| [Transactions](persistence/transactions.md) | MyBatis TransactionManager, OCC, session propagation |
| [Migrations](persistence/migrations.md) | Liquibase changelog — 11 releases, 20+ changesets |

---

## Messaging

| Page | Description |
|------|-------------|
| [Outbox & Inbox](messaging/outbox-inbox.md) | Transactional outbox pattern, inbox idempotency, leases |
| [Event Catalog](messaging/event-catalog.md) | All 11 event types across 9 topics |
| [Consumer Reliability](messaging/consumer-reliability.md) | Retry queues, dead-letter topics, at-least-once delivery |
| [Message Call Traces](messaging/message-call-traces.md) | Outbox publisher, Kafka consumer, notification handlers, workflow messages |

---

## Storage

| Page | Description |
|------|-------------|
| [Evidence Lifecycle](storage/evidence-lifecycle.md) | Upload session → presigned URL → finalize → version → download |

---

## Workflow

| Page | Description |
|------|-------------|
| [BPMN Models](workflow/bpmn-models.md) | 2 Camunda BPMN processes — structure, subprocesses, events |
| [Task Reference](workflow/task-reference.md) | 19 user task keys, candidate groups, escalation timers |
| [Reconciliation](workflow/reconciliation.md) | 6 issue types, auto-repair, terminate runtime |

---

## Security

| Page | Description |
|------|-------------|
| [Authentication](security/authentication.md) | Keycloak JWT, Nimbus verification, JWKS, claims extraction |
| [Authorization](security/authorization.md) | 7-axis evaluation order, permission-to-role mapping, scoping |

---

## Operations

| Page | Description |
|------|-------------|
| [Configuration](operations/configuration.md) | All 30+ environment variables with defaults |
| [Docker Compose](operations/docker-compose.md) | 7 services — PostgreSQL, Kafka, Redis, MinIO, Keycloak, Mailpit, app |
| [Runbooks](operations/runbooks.md) | Operational runbooks for recovery and maintenance |

---

## Development

| Page | Description |
|------|-------------|
| [Setup](development/setup.md) | Local development environment prerequisites |
| [Testing](development/testing.md) | Test strategy — unit, integration (Testcontainers), Karate acceptance |
| [Makefile Reference](development/makefile-reference.md) | All 40+ Makefile targets |

---

## Reference

| Page | Description |
|------|-------------|
| [Test Users](reference/test-users.md) | 14 Keycloak users, roles, jurisdictions, classifications |
| [Technology Stack](reference/tech-stack.md) | Complete dependency versions |
