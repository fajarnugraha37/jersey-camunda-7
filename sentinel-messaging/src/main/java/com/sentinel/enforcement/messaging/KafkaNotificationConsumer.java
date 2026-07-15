package com.sentinel.enforcement.messaging;

import com.sentinel.enforcement.application.messaging.EventEnvelope;
import com.sentinel.enforcement.application.messaging.MessagingTopics;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class KafkaNotificationConsumer {
  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaNotificationConsumer.class);
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
  private static final String RETRY_ATTEMPT_HEADER = "x-retry-attempt";
  private static final String ORIGINAL_TOPIC_HEADER = "x-original-topic";
  private static final String ERROR_HEADER = "x-error";

  private final KafkaConsumer<String, String> consumer;
  private final KafkaProducer<String, String> producer;
  private final EventEnvelopeJsonCodec codec;
  private final NotificationEventHandler notificationEventHandler;
  private final int maxRetries;

  KafkaNotificationConsumer(
      KafkaConsumer<String, String> consumer,
      KafkaProducer<String, String> producer,
      EventEnvelopeJsonCodec codec,
      NotificationEventHandler notificationEventHandler,
      int maxRetries) {
    this.consumer = consumer;
    this.producer = producer;
    this.codec = codec;
    this.notificationEventHandler = notificationEventHandler;
    this.maxRetries = maxRetries;
    this.consumer.subscribe(subscribedTopics());
  }

  void run(AtomicBoolean running) {
    while (running.get()) {
      try {
        ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);
        for (ConsumerRecord<String, String> record : records) {
          processRecord(record);
        }
      } catch (Exception exception) {
        LOGGER.warn("Notification consumer loop failed. Poll will retry.", exception);
      }
    }
  }

  void wakeup() {
    consumer.wakeup();
  }

  private void processRecord(ConsumerRecord<String, String> record) {
    try {
      EventEnvelope eventEnvelope = codec.deserialize(record.value());
      notificationEventHandler.handle(resolveOriginalTopic(record), eventEnvelope);
      commit(record);
    } catch (Exception exception) {
      handleFailure(record, exception);
    }
  }

  private void handleFailure(ConsumerRecord<String, String> record, Exception exception) {
    int currentAttempt = retryAttempt(record);
    String originalTopic = resolveOriginalTopic(record);
    String destinationTopic =
        currentAttempt >= maxRetries ? originalTopic + ".dlq" : originalTopic + ".retry";
    try {
      producer
          .send(
              new ProducerRecord<>(
                  destinationTopic,
                  null,
                  record.key(),
                  record.value(),
                  List.of(
                      new RecordHeader(
                          ORIGINAL_TOPIC_HEADER, originalTopic.getBytes(StandardCharsets.UTF_8)),
                      new RecordHeader(
                          RETRY_ATTEMPT_HEADER,
                          Integer.toString(currentAttempt + 1).getBytes(StandardCharsets.UTF_8)),
                      new RecordHeader(
                          ERROR_HEADER,
                          exception.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8)))))
          .get();
      commit(record);
      LOGGER.warn(
          "Notification consumer moved event on topic {} to {} after failure.",
          record.topic(),
          destinationTopic,
          exception);
    } catch (Exception routingException) {
      LOGGER.error(
          "Notification consumer failed to route event from topic {} to retry/dead-letter topic.",
          record.topic(),
          routingException);
    }
  }

  private void commit(ConsumerRecord<String, String> record) {
    consumer.commitSync(
        Map.of(
            new TopicPartition(record.topic(), record.partition()),
            new OffsetAndMetadata(record.offset() + 1)));
  }

  private int retryAttempt(ConsumerRecord<String, String> record) {
    return header(record, RETRY_ATTEMPT_HEADER).map(Integer::parseInt).orElse(0);
  }

  private String resolveOriginalTopic(ConsumerRecord<String, String> record) {
    return header(record, ORIGINAL_TOPIC_HEADER).orElse(stripRetrySuffix(record.topic()));
  }

  private Optional<String> header(ConsumerRecord<String, String> record, String name) {
    Header header = record.headers().lastHeader(name);
    if (header == null) {
      return Optional.empty();
    }
    return Optional.of(new String(header.value(), StandardCharsets.UTF_8));
  }

  private String stripRetrySuffix(String topic) {
    if (topic.endsWith(".retry")) {
      return topic.substring(0, topic.length() - ".retry".length());
    }
    return topic;
  }

  private List<String> subscribedTopics() {
    return List.of(
        MessagingTopics.CASE_LIFECYCLE,
        MessagingTopics.CASE_LIFECYCLE + ".retry",
        MessagingTopics.CASE_ASSIGNMENT,
        MessagingTopics.CASE_ASSIGNMENT + ".retry",
        MessagingTopics.EVIDENCE_LIFECYCLE,
        MessagingTopics.EVIDENCE_LIFECYCLE + ".retry");
  }
}
