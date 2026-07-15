package com.sentinel.enforcement.messaging;

import java.time.Duration;

public record MessagingRuntimeConfiguration(
    String kafkaBootstrapServers,
    String appInstanceId,
    Duration outboxPollInterval,
    Duration outboxLeaseDuration,
    int outboxBatchSize,
    String notificationConsumerGroupId,
    int notificationMaxRetries) {}
