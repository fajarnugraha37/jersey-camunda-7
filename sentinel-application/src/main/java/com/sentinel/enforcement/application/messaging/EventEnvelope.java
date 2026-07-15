package com.sentinel.enforcement.application.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventEnvelope(
    UUID eventId,
    String eventType,
    int eventVersion,
    String aggregateType,
    UUID aggregateId,
    Instant occurredAt,
    String correlationId,
    String causationId,
    EventActor actor,
    Map<String, Object> payload) {}
