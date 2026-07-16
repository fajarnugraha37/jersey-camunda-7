package com.sentinel.enforcement.integration.karate.support;

import java.util.Map;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

public final class LiveMessagingSupport {
  private static final String DEFAULT_KAFKA_BOOTSTRAP_SERVERS = "localhost:29092";

  private LiveMessagingSupport() {}

  public static void produceRawEvent(String topic, String key, String payload) {
    try (KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProducerProperties())) {
      producer.send(new ProducerRecord<>(topic, key, payload)).get();
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to produce Kafka test event.", exception);
    }
  }

  private static Map<String, Object> kafkaProducerProperties() {
    return Map.of(
        "bootstrap.servers", kafkaBootstrapServers(),
        "key.serializer", StringSerializer.class.getName(),
        "value.serializer", StringSerializer.class.getName(),
        "acks", "all");
  }

  private static String kafkaBootstrapServers() {
    return propertyOrDefault(
        "sentinel.kafkaBootstrapServers",
        "KAFKA_BOOTSTRAP_SERVERS",
        DEFAULT_KAFKA_BOOTSTRAP_SERVERS);
  }

  private static String propertyOrDefault(
      String propertyName, String environmentName, String defaultValue) {
    String propertyValue = System.getProperty(propertyName);
    if (propertyValue != null && !propertyValue.isBlank()) {
      return propertyValue;
    }
    String environmentValue = System.getenv(environmentName);
    if (environmentValue != null && !environmentValue.isBlank()) {
      return environmentValue;
    }
    return defaultValue;
  }
}
