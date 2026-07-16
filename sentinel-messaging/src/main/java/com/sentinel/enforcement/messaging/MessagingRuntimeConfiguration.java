package com.sentinel.enforcement.messaging;

import java.time.Duration;

public record MessagingRuntimeConfiguration(
    String kafkaBootstrapServers,
    String mailpitSmtpHost,
    int mailpitSmtpPort,
    String notificationFromEmail,
    String notificationToEmail,
    String appInstanceId,
    Duration outboxPollInterval,
    Duration outboxLeaseDuration,
    int outboxBatchSize,
    String notificationConsumerGroupId,
    int notificationMaxRetries) {}
