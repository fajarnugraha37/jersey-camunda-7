package com.sentinel.enforcement.persistence.decision;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DecisionVersionRecord(
    UUID id,
    UUID decisionId,
    int versionNumber,
    String title,
    String summary,
    boolean violationProven,
    String sanctionSummary,
    String obligationTitle,
    String obligationDetails,
    LocalDate obligationDueDate,
    LocalDate appealDeadline,
    OffsetDateTime publishedAt,
    String publishedBy,
    OffsetDateTime createdAt,
    String createdBy) {}
