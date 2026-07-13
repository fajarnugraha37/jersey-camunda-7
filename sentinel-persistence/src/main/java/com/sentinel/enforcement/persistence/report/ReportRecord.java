package com.sentinel.enforcement.persistence.report;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReportRecord(
    UUID id,
    String title,
    String description,
    String jurisdictionCode,
    String reporterName,
    String status,
    OffsetDateTime createdAt,
    String createdBy,
    OffsetDateTime updatedAt,
    String updatedBy,
    long version) {}
