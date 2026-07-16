package com.sentinel.enforcement.messaging;

import com.sentinel.enforcement.application.messaging.OutboxEvent;
import com.sentinel.enforcement.application.messaging.OutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class KafkaOutboxPublisher {
  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaOutboxPublisher.class);
  private static final String SYSTEM_ACTOR = "outbox-publisher";

  private final OutboxRepository outboxRepository;
  private final KafkaProducer<String, String> producer;
  private final EventEnvelopeJsonCodec codec;
  private final Clock clock;
  private final String leaseOwner;
  private final Duration leaseDuration;
  private final int batchSize;
  private final Runnable topicProvisioner;

  KafkaOutboxPublisher(
      OutboxRepository outboxRepository,
      KafkaProducer<String, String> producer,
      EventEnvelopeJsonCodec codec,
      Clock clock,
      String leaseOwner,
      Duration leaseDuration,
      int batchSize,
      Runnable topicProvisioner) {
    this.outboxRepository = outboxRepository;
    this.producer = producer;
    this.codec = codec;
    this.clock = clock;
    this.leaseOwner = leaseOwner;
    this.leaseDuration = leaseDuration;
    this.batchSize = batchSize;
    this.topicProvisioner = topicProvisioner;
  }

  int publishPendingBatch() {
    Instant now = clock.instant();
    List<OutboxEvent> claimed =
        outboxRepository.claimPending(leaseOwner, now, leaseDuration, batchSize, SYSTEM_ACTOR);
    for (OutboxEvent outboxEvent : claimed) {
      try {
        producer
            .send(
                new ProducerRecord<>(
                    outboxEvent.topic(),
                    outboxEvent.messageKey(),
                    codec.serialize(outboxEvent.envelope())))
            .get();
        outboxRepository.markPublished(outboxEvent.eventId(), clock.instant(), SYSTEM_ACTOR);
      } catch (Exception exception) {
        Instant retryAt = clock.instant().plus(retryDelay(outboxEvent.publishAttempts() + 1));
        outboxRepository.releaseForRetry(
            outboxEvent.eventId(),
            clock.instant(),
            retryAt,
            abbreviatedMessage(exception),
            SYSTEM_ACTOR);
        ensureTopicsExistForRetry();
        LOGGER.warn(
            "Failed to publish outbox event {} to topic {}. Will retry at {}.",
            outboxEvent.eventId(),
            outboxEvent.topic(),
            retryAt,
            exception);
      }
    }
    return claimed.size();
  }

  private Duration retryDelay(int attemptNumber) {
    long seconds = Math.min(60L, 1L << Math.min(attemptNumber, 6));
    return Duration.ofSeconds(seconds);
  }

  private void ensureTopicsExistForRetry() {
    try {
      topicProvisioner.run();
    } catch (RuntimeException ignored) {
      // Topic recovery is best-effort here; the original publish failure is already recorded.
    }
  }

  private String abbreviatedMessage(Exception exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return exception.getClass().getSimpleName();
    }
    return message.length() > 500 ? message.substring(0, 500) : message;
  }
}
