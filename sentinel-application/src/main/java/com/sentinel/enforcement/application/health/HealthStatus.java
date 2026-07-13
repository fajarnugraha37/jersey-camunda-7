package com.sentinel.enforcement.application.health;

import java.time.Instant;

public record HealthStatus(boolean healthy, String database, Instant timestamp) {}
