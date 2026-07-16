package com.sentinel.enforcement.application.casefile;

import com.sentinel.enforcement.domain.casefile.CaseRelationshipType;
import java.util.Objects;
import java.util.UUID;

public record CreateCaseRelationshipCommand(
    UUID relatedCaseId,
    CaseRelationshipType relationshipType,
    CaseRelationshipReferenceDirection direction,
    String relationshipReason,
    String correlationId,
    String sourceIp) {

  public CreateCaseRelationshipCommand {
    Objects.requireNonNull(relatedCaseId, "relatedCaseId must not be null");
    Objects.requireNonNull(relationshipType, "relationshipType must not be null");
    Objects.requireNonNull(direction, "direction must not be null");
    relationshipReason = normalizeRequired(relationshipReason, "relationshipReason");
    correlationId = normalizeRequired(correlationId, "correlationId");
    sourceIp = normalizeOptional(sourceIp);
  }

  private static String normalizeRequired(String value, String fieldName) {
    String normalized = normalizeOptional(value);
    if (normalized == null) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return normalized;
  }

  private static String normalizeOptional(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
