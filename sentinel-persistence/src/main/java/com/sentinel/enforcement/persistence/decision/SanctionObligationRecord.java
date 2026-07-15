package com.sentinel.enforcement.persistence.decision;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SanctionObligationRecord(
    UUID id,
    UUID sanctionId,
    String title,
    String details,
    LocalDate dueDate,
    String status,
    OffsetDateTime createdAt,
    String createdBy,
    OffsetDateTime updatedAt,
    String updatedBy,
    long version) {}
