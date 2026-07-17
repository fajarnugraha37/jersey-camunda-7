---
type: Documentation Index
title: "Integrations"
description: "Files and subdirectories in Integrations."
---

# Files

- [Cloud Services Strategy](cloud-services.md) - The Sentinel Enforcement Platform has zero cloud-specific dependencies. All infrastructure runs locally via Docker Compose. This page documents the local equivalents for every service.
- [Module and Infrastructure Dependency Matrix](dependency-matrix.md) - Complete inter-module and module-to-infrastructure dependency map for the Sentinel Enforcement Platform, including a Mermaid matrix and per-module breakdown.
- [External Infrastructure Services](external-services.md) - Comprehensive catalog of all infrastructure services the Sentinel Enforcement Platform depends on, including PostgreSQL, Apache Kafka, Keycloak, MinIO, Redis, Mailpit, and Embedded Camunda 7.
- [Inter-Module Communication Patterns](service-to-service.md) - Documents the hexagonal architecture port/adapter pattern, catalog of all ports and their infrastructure adapter implementations, request flow patterns, and async messaging pipelines.
