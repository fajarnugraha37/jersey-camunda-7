package com.sentinel.enforcement.persistence.decision;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DecisionRecord(
    UUID id,
    UUID caseId,
    UUID recommendationId,
    String title,
    String summary,
    boolean violationProven,
    String sanctionSummary,
    String obligationTitle,
    String obligationDetails,
    LocalDate obligationDueDate,
    LocalDate appealDeadline,
    String status,
    OffsetDateTime approvedAt,
    String approvedBy,
    OffsetDateTime publishedAt,
    String publishedBy,
    OffsetDateTime createdAt,
    String createdBy,
    OffsetDateTime updatedAt,
    String updatedBy,
    long version) {}
