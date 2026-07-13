package com.sentinel.enforcement.api.report;

import java.time.Instant;
import java.util.UUID;

public record ReportResponse(
    UUID id,
    String title,
    String description,
    String jurisdictionCode,
    String reporterName,
    String status,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version) {}
