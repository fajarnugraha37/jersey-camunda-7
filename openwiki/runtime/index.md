---
type: Documentation Index
title: "Runtime"
description: "Files and subdirectories in Runtime."
tags: [sentinel, runtime, async, threading, context, request-flows]
---

# Files

- [Asynchronous Processing](asynchronous-processing.md) - Threading and async processing model for the Sentinel Enforcement Platform — Grizzly request threads, MyBatis session management, background messaging threads, Camunda job executor, HikariCP pooling, transaction isolation, and outbox lease locking.
- [Concurrency](concurrency.md) - Threading model, optimistic locking, outbox lease locking, Camunda job executor concurrency, and concurrency safety guarantees.
- [Context Propagation](context-propagation.md) - How request-scoped context — correlation IDs, authenticated actors, and database sessions — propagates through the Sentinel Enforcement Platform using SLF4J MDC, ThreadLocal storage, JAX-RS filters, and event causation chains.
- [Request Flows](request-flows.md) - Request lifecycle through the Sentinel Enforcement Platform — from Grizzly HTTP server through JAX-RS filters, resources, application services, domain logic, persistence, and response.
- [Traffic Flows](traffic-flows.md) - External traffic patterns — HTTP request routing, Kafka event routing, health check polling, and infrastructure service connections.
