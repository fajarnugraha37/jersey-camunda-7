package com.sentinel.enforcement.persistence.operations;

import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceOperationCallData(
    UUID runId, LocalDate effectiveDate, String requestedBy) {}
