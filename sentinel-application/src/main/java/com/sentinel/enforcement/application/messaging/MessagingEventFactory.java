package com.sentinel.enforcement.application.messaging;

import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import com.sentinel.enforcement.domain.evidence.Evidence;
import com.sentinel.enforcement.domain.evidence.EvidenceVersion;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class MessagingEventFactory {
  private MessagingEventFactory() {}

  public static OutboxEvent caseCreated(
      ApplicationActor actor, CaseRecord caseRecord, String correlationId, Instant now) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("caseId", caseRecord.id().toString());
    payload.put("caseNumber", caseRecord.caseNumber());
    payload.put("reportId", caseRecord.reportId().toString());
    payload.put("title", caseRecord.title());
    payload.put("jurisdictionCode", caseRecord.jurisdictionCode());
    payload.put("status", caseRecord.status().name());
    return outboxEvent(
        actor,
        MessagingTopics.CASE_LIFECYCLE,
        "CaseCreated",
        "Case",
        caseRecord.id(),
        caseRecord.id().toString(),
        correlationId,
        payload,
        now);
  }

  public static OutboxEvent caseAssigned(
      ApplicationActor actor,
      CaseRecord caseRecord,
      String reason,
      String correlationId,
      Instant now) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("caseId", caseRecord.id().toString());
    payload.put("caseNumber", caseRecord.caseNumber());
    payload.put("assignedUnitId", caseRecord.assignedUnitId());
    payload.put("assigneeUserId", caseRecord.assigneeUserId());
    payload.put("reason", reason);
    return outboxEvent(
        actor,
        MessagingTopics.CASE_ASSIGNMENT,
        "CaseAssigned",
        "Case",
        caseRecord.id(),
        caseRecord.id().toString(),
        correlationId,
        payload,
        now);
  }

  public static OutboxEvent caseTransitioned(
      ApplicationActor actor,
      CaseRecord caseRecord,
      CaseStatus fromStatus,
      String reason,
      String correlationId,
      Instant now) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("caseId", caseRecord.id().toString());
    payload.put("caseNumber", caseRecord.caseNumber());
    payload.put("fromStatus", fromStatus.name());
    payload.put("toStatus", caseRecord.status().name());
    payload.put("reason", reason);
    return outboxEvent(
        actor,
        MessagingTopics.CASE_LIFECYCLE,
        "CaseTransitioned",
        "Case",
        caseRecord.id(),
        caseRecord.id().toString(),
        correlationId,
        payload,
        now);
  }

  public static OutboxEvent evidenceVersionFinalized(
      ApplicationActor actor,
      Evidence evidence,
      EvidenceVersion evidenceVersion,
      String correlationId,
      Instant now) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("caseId", evidence.caseId().toString());
    payload.put("evidenceId", evidence.id().toString());
    payload.put("title", evidence.title());
    payload.put("classification", evidence.classification().name());
    payload.put("versionNumber", evidenceVersion.versionNumber());
    return outboxEvent(
        actor,
        MessagingTopics.EVIDENCE_LIFECYCLE,
        "EvidenceVersionFinalized",
        "Evidence",
        evidence.id(),
        evidence.caseId().toString(),
        correlationId,
        payload,
        now);
  }

  private static OutboxEvent outboxEvent(
      ApplicationActor actor,
      String topic,
      String eventType,
      String aggregateType,
      UUID aggregateId,
      String messageKey,
      String correlationId,
      Map<String, Object> payload,
      Instant now) {
    UUID eventId = UUID.randomUUID();
    EventEnvelope envelope =
        new EventEnvelope(
            eventId,
            eventType,
            1,
            aggregateType,
            aggregateId,
            now,
            correlationId,
            null,
            new EventActor("USER", actor.username()),
            Map.copyOf(payload));
    return new OutboxEvent(
        eventId,
        topic,
        messageKey,
        envelope,
        "PENDING",
        now,
        null,
        null,
        0,
        null,
        null,
        now,
        actor.username(),
        now,
        actor.username(),
        0L);
  }
}
