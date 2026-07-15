package com.sentinel.enforcement.persistence.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sentinel.enforcement.application.messaging.EventActor;
import com.sentinel.enforcement.application.messaging.EventEnvelope;
import com.sentinel.enforcement.application.messaging.OutboxEvent;
import com.sentinel.enforcement.application.messaging.OutboxRepository;
import com.sentinel.enforcement.persistence.MyBatisRepositorySupport;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;

public final class OutboxRepositoryMyBatisAdapter extends MyBatisRepositorySupport
    implements OutboxRepository {
  private final ObjectMapper objectMapper;

  public OutboxRepositoryMyBatisAdapter(SqlSessionFactory sqlSessionFactory) {
    super(sqlSessionFactory);
    this.objectMapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  @Override
  public void enqueue(OutboxEvent outboxEvent) {
    executeWrite(
        session -> {
          session.getMapper(MessagingMyBatisMapper.class).insertOutboxEvent(toData(outboxEvent));
          return null;
        });
  }

  @Override
  public List<OutboxEvent> claimPending(
      String leaseOwner, Instant now, Duration leaseDuration, int batchSize, String updatedBy) {
    return executeWrite(
        session ->
            session
                .getMapper(MessagingMyBatisMapper.class)
                .claimPending(
                    leaseOwner,
                    now.atOffset(ZoneOffset.UTC),
                    now.plus(leaseDuration).atOffset(ZoneOffset.UTC),
                    batchSize,
                    updatedBy)
                .stream()
                .map(this::toDomain)
                .toList());
  }

  @Override
  public void markPublished(UUID eventId, Instant publishedAt, String updatedBy) {
    executeWrite(
        session -> {
          session
              .getMapper(MessagingMyBatisMapper.class)
              .markPublished(eventId, publishedAt.atOffset(ZoneOffset.UTC), updatedBy);
          return null;
        });
  }

  @Override
  public void releaseForRetry(
      UUID eventId, Instant now, Instant nextAttemptAt, String lastError, String updatedBy) {
    executeWrite(
        session -> {
          session
              .getMapper(MessagingMyBatisMapper.class)
              .releaseForRetry(
                  eventId,
                  now.atOffset(ZoneOffset.UTC),
                  nextAttemptAt.atOffset(ZoneOffset.UTC),
                  lastError,
                  updatedBy);
          return null;
        });
  }

  @Override
  public long countPending() {
    return executeRead(session -> session.getMapper(MessagingMyBatisMapper.class).countPending());
  }

  private OutboxEventData toData(OutboxEvent outboxEvent) {
    return new OutboxEventData(
        outboxEvent.eventId(),
        outboxEvent.topic(),
        outboxEvent.messageKey(),
        outboxEvent.envelope().eventType(),
        outboxEvent.envelope().eventVersion(),
        outboxEvent.envelope().aggregateType(),
        outboxEvent.envelope().aggregateId(),
        outboxEvent.envelope().occurredAt().atOffset(ZoneOffset.UTC),
        outboxEvent.envelope().correlationId(),
        outboxEvent.envelope().causationId(),
        outboxEvent.envelope().actor().type(),
        outboxEvent.envelope().actor().id(),
        serializePayload(outboxEvent.envelope().payload()),
        outboxEvent.status(),
        outboxEvent.availableAt().atOffset(ZoneOffset.UTC),
        outboxEvent.leaseOwner(),
        outboxEvent.leaseExpiresAt() == null
            ? null
            : outboxEvent.leaseExpiresAt().atOffset(ZoneOffset.UTC),
        outboxEvent.publishAttempts(),
        outboxEvent.lastError(),
        outboxEvent.publishedAt() == null
            ? null
            : outboxEvent.publishedAt().atOffset(ZoneOffset.UTC),
        outboxEvent.createdAt().atOffset(ZoneOffset.UTC),
        outboxEvent.createdBy(),
        outboxEvent.updatedAt().atOffset(ZoneOffset.UTC),
        outboxEvent.updatedBy(),
        outboxEvent.version());
  }

  @SuppressWarnings("unchecked")
  private OutboxEvent toDomain(OutboxEventData data) {
    return new OutboxEvent(
        data.eventId(),
        data.topic(),
        data.messageKey(),
        new EventEnvelope(
            data.eventId(),
            data.eventType(),
            data.eventVersion(),
            data.aggregateType(),
            data.aggregateId(),
            data.occurredAt().toInstant(),
            data.correlationId(),
            data.causationId(),
            new EventActor(data.actorType(), data.actorId()),
            deserializePayload(data.payloadJson())),
        data.status(),
        data.availableAt().toInstant(),
        data.leaseOwner(),
        data.leaseExpiresAt() == null ? null : data.leaseExpiresAt().toInstant(),
        data.publishAttempts(),
        data.lastError(),
        data.publishedAt() == null ? null : data.publishedAt().toInstant(),
        data.createdAt().toInstant(),
        data.createdBy(),
        data.updatedAt().toInstant(),
        data.updatedBy(),
        data.version());
  }

  private String serializePayload(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize outbox payload.", exception);
    }
  }

  private Map<String, Object> deserializePayload(String payloadJson) {
    try {
      return objectMapper.readValue(payloadJson, Map.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to deserialize outbox payload.", exception);
    }
  }
}
