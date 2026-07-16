package com.sentinel.enforcement.messaging;

import com.sentinel.enforcement.application.messaging.ApplicationTransactionManager;
import com.sentinel.enforcement.application.messaging.InboxRepository;
import com.sentinel.enforcement.application.messaging.MessagingTopics;
import com.sentinel.enforcement.application.messaging.NotificationRepository;
import com.sentinel.enforcement.application.messaging.OutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MessagingRuntime implements AutoCloseable {
  private static final Duration ADMIN_TIMEOUT = Duration.ofSeconds(15);
  private static final Logger LOGGER = LoggerFactory.getLogger(MessagingRuntime.class);
  private final AtomicBoolean running;
  private final KafkaProducer<String, String> producer;
  private final KafkaConsumer<String, String> consumer;
  private final Thread outboxThread;
  private final Thread consumerThread;

  private MessagingRuntime(
      AtomicBoolean running,
      KafkaProducer<String, String> producer,
      KafkaConsumer<String, String> consumer,
      Thread outboxThread,
      Thread consumerThread) {
    this.running = running;
    this.producer = producer;
    this.consumer = consumer;
    this.outboxThread = outboxThread;
    this.consumerThread = consumerThread;
  }

  public static MessagingRuntime start(
      MessagingRuntimeConfiguration configuration,
      ApplicationTransactionManager transactionManager,
      OutboxRepository outboxRepository,
      InboxRepository inboxRepository,
      NotificationRepository notificationRepository,
      Clock clock) {
    AtomicBoolean running = new AtomicBoolean(true);
    Runnable topicProvisioner = () -> ensureTopicsExist(configuration);
    topicProvisioner.run();
    EventEnvelopeJsonCodec codec = new EventEnvelopeJsonCodec();
    KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties(configuration));
    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties(configuration));

    KafkaOutboxPublisher outboxPublisher =
        new KafkaOutboxPublisher(
            outboxRepository,
            producer,
            codec,
            clock,
            configuration.appInstanceId(),
            configuration.outboxLeaseDuration(),
            configuration.outboxBatchSize(),
            topicProvisioner);
    NotificationEventHandler notificationEventHandler =
        new NotificationEventHandler(
            transactionManager,
            inboxRepository,
            notificationRepository,
            outboxRepository,
            configuration.notificationToEmail(),
            configuration.notificationFromEmail(),
            clock);
    NotificationCommandHandler notificationCommandHandler =
        new NotificationCommandHandler(
            transactionManager,
            inboxRepository,
            notificationRepository,
            outboxRepository,
            new NotificationEmailSender(
                configuration.mailpitSmtpHost(), configuration.mailpitSmtpPort()),
            clock);
    KafkaNotificationConsumer notificationConsumer =
        new KafkaNotificationConsumer(
            consumer,
            producer,
            codec,
            notificationEventHandler,
            notificationCommandHandler,
            configuration.notificationMaxRetries());

    Thread outboxThread =
        Thread.ofPlatform()
            .name("sentinel-outbox-publisher-" + suffix(configuration.appInstanceId()))
            .daemon(true)
            .start(
                () -> {
                  while (running.get()) {
                    try {
                      outboxPublisher.publishPendingBatch();
                    } catch (Exception exception) {
                      LOGGER.warn(
                          "Outbox publisher loop failed. Poll will retry after {}.",
                          configuration.outboxPollInterval(),
                          exception);
                    }
                    sleep(configuration.outboxPollInterval());
                  }
                });
    Thread consumerThread =
        Thread.ofPlatform()
            .name("sentinel-notification-consumer-" + suffix(configuration.appInstanceId()))
            .daemon(true)
            .start(() -> notificationConsumer.run(running));

    return new MessagingRuntime(running, producer, consumer, outboxThread, consumerThread);
  }

  @Override
  public void close() {
    running.set(false);
    consumer.wakeup();
    join(outboxThread);
    join(consumerThread);
    consumer.close();
    producer.close();
  }

  private static Properties producerProperties(MessagingRuntimeConfiguration configuration) {
    Properties properties = new Properties();
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.kafkaBootstrapServers());
    properties.put(ProducerConfig.CLIENT_ID_CONFIG, configuration.appInstanceId() + "-producer");
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    properties.put(ProducerConfig.ACKS_CONFIG, "all");
    properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    properties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");
    properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "5000");
    properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");
    properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "10000");
    return properties;
  }

  private static Properties consumerProperties(MessagingRuntimeConfiguration configuration) {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.kafkaBootstrapServers());
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, configuration.notificationConsumerGroupId());
    properties.put(ConsumerConfig.CLIENT_ID_CONFIG, configuration.appInstanceId() + "-consumer");
    properties.put(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    properties.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
    return properties;
  }

  private static void ensureTopicsExist(MessagingRuntimeConfiguration configuration) {
    Properties properties = new Properties();
    properties.put(
        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.kafkaBootstrapServers());
    try (AdminClient adminClient = AdminClient.create(properties)) {
      List<String> existingTopics =
          new ArrayList<>(
              adminClient.listTopics().names().get(ADMIN_TIMEOUT.toSeconds(), TimeUnit.SECONDS));
      List<NewTopic> missingTopics =
          provisionedTopics().stream()
              .filter(topic -> !existingTopics.contains(topic))
              .map(topic -> new NewTopic(topic, 1, (short) 1))
              .toList();
      if (!missingTopics.isEmpty()) {
        adminClient
            .createTopics(missingTopics)
            .all()
            .get(ADMIN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
      }
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Failed to provision Kafka topics for messaging runtime.", exception);
    }
  }

  private static List<String> provisionedTopics() {
    List<String> topics = new ArrayList<>();
    for (String topic : MessagingTopics.domainLifecycleTopics()) {
      topics.add(topic);
      topics.add(topic + ".retry");
      topics.add(topic + ".dlq");
    }
    for (String topic : MessagingTopics.integrationTopics()) {
      topics.add(topic);
      topics.add(topic + ".retry");
      topics.add(topic + ".dlq");
    }
    return List.copyOf(topics);
  }

  private static void sleep(java.time.Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }

  private static void join(Thread thread) {
    try {
      thread.join(5000);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }

  private static String suffix(String instanceId) {
    return instanceId == null || instanceId.isBlank()
        ? UUID.randomUUID().toString()
        : instanceId.substring(0, Math.min(instanceId.length(), 12));
  }
}
