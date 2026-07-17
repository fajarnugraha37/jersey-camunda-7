---
type: Documentation Index
title: "Runtime"
description: "Files and subdirectories in Runtime."
---

# Files

- [Concurrency and Asynchronous Processing](concurrency-and-asynchronous-processing.md) - Threading and async processing model for the Sentinel Enforcement Platform — Grizzly request threads, MyBatis session management, background messaging threads, Camunda job executor, HikariCP pooling, transaction isolation, and outbox lease locking.
- [Context Propagation](context-propagation.md) - How request-scoped context — correlation IDs, authenticated actors, and database sessions — propagates through the Sentinel Enforcement Platform using SLF4J MDC, ThreadLocal storage, JAX-RS filters, and event causation chains.
- [Traffic and Request Flows](traffic-and-request-flows.md) - Complete lifecycle of an HTTP request through the Sentinel Enforcement Platform — from Grizzly HTTP server, through authentication and authorization filters, into JAX-RS resources, application services, domain logic, persistence, and error handling.
