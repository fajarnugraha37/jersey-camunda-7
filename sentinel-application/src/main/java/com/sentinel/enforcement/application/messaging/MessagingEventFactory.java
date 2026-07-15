package com.sentinel.enforcement.application.messaging;

import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.domain.appeal.Appeal;
import com.sentinel.enforcement.domain.appeal.AppealDecision;
import com.sentinel.enforcement.domain.casefile.CaseRecord;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import com.sentinel.enforcement.domain.decision.Decision;
import com.sentinel.enforcement.domain.evidence.Evidence;
import com.sentinel.enforcement.domain.evidence.EvidenceVersion;
import com.sentinel.enforcement.domain.sanction.Sanction;
import com.sentinel.enforcement.domain.sanction.SanctionObligation;
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
    payload.put("classification", caseRecord.classification().name());
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
    payload.put("classification", caseRecord.classification().name());
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
    payload.put("classification", caseRecord.classification().name());
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

  public static OutboxEvent decisionPublished(
      ApplicationActor actor, Decision decision, String correlationId, Instant now) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("caseId", decision.caseId().toString());
    payload.put("decisionId", decision.id().toString());
    payload.put("title", decision.title());
    payload.put("violationProven", decision.violationProven());
    payload.put("appealDeadline", decision.appealDeadline().toString());
    return outboxEvent(
        actor,
        MessagingTopics.DECISION_LIFECYCLE,
        "DecisionPublished",
        "Decision",
        decision.id(),
        decision.caseId().toString(),
        correlationId,
        payload,
        now);
  }

  public static OutboxEvent sanctionCreated(
      ApplicationActor actor,
      Sanction sanction,
      SanctionObligation sanctionObligation,
      String correlationId,
      Instant now) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("caseId", sanction.caseId().toString());
    payload.put("sanctionId", sanction.id().toString());
    payload.put("decisionId", sanction.decisionId().toString());
    payload.put("obligationId", sanctionObligation.id().toString());
    payload.put("obligationDueDate", sanctionObligation.dueDate().toString());
    return outboxEvent(
        actor,
        MessagingTopics.SANCTION_LIFECYCLE,
        "SanctionCreated",
        "Sanction",
        sanction.id(),
        sanction.caseId().toString(),
        correlationId,
        payload,
        now);
  }

  public static OutboxEvent sanctionCancelled(
      ApplicationActor actor,
      Sanction sanction,
      SanctionObligation sanctionObligation,
      String correlationId,
      Instant now) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("caseId", sanction.caseId().toString());
    payload.put("sanctionId", sanction.id().toString());
    payload.put("obligationId", sanctionObligation.id().toString());
    return outboxEvent(
        actor,
        MessagingTopics.SANCTION_LIFECYCLE,
        "SanctionCancelled",
        "Sanction",
        sanction.id(),
        sanction.caseId().toString(),
        correlationId,
        payload,
        now);
  }

  public static OutboxEvent appealFiled(
      ApplicationActor actor, Appeal appeal, String correlationId, Instant now) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("caseId", appeal.caseId().toString());
    payload.put("appealId", appeal.id().toString());
    payload.put("decisionId", appeal.decisionId().toString());
    payload.put("supervisorOverride", appeal.supervisorOverride());
    return outboxEvent(
        actor,
        MessagingTopics.APPEAL_LIFECYCLE,
        "AppealFiled",
        "Appeal",
        appeal.id(),
        appeal.caseId().toString(),
        correlationId,
        payload,
        now);
  }

  public static OutboxEvent appealDecided(
      ApplicationActor actor,
      Appeal appeal,
      AppealDecision appealDecision,
      String correlationId,
      Instant now) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("caseId", appeal.caseId().toString());
    payload.put("appealId", appeal.id().toString());
    payload.put("decisionId", appeal.decisionId().toString());
    payload.put("outcome", appealDecision.outcome().name());
    return outboxEvent(
        actor,
        MessagingTopics.APPEAL_LIFECYCLE,
        "AppealDecided",
        "Appeal",
        appeal.id(),
        appeal.caseId().toString(),
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
