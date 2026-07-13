package com.sentinel.enforcement.api.health;

import java.time.Instant;

public record HealthResponse(String status, String database, Instant timestamp) {}
