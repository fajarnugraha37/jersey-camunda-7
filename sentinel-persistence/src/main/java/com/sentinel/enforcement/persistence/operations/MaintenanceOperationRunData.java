package com.sentinel.enforcement.persistence.operations;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MaintenanceOperationRunData(
    UUID runId,
    String operationName,
    String requestedBy,
    OffsetDateTime requestedAt,
    OffsetDateTime completedAt,
    LocalDate effectiveDate,
    String resultStatus,
    long affectedRows) {}
