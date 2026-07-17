package com.sentinel.enforcement.application.casefile;

import com.sentinel.enforcement.domain.casefile.CaseRelationshipType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CaseRelationshipView(
    UUID caseId,
    UUID relatedCaseId,
    String relatedCaseNumber,
    String relatedCaseTitle,
    int depth,
    CaseRelationshipViewDirection direction,
    CaseRelationshipType relationshipType,
    String relationshipReason,
    List<UUID> pathCaseIds) {

  public CaseRelationshipView {
    Objects.requireNonNull(caseId, "caseId must not be null");
    Objects.requireNonNull(relatedCaseId, "relatedCaseId must not be null");
    relatedCaseNumber = requireNonBlank(relatedCaseNumber, "relatedCaseNumber");
    relatedCaseTitle = requireNonBlank(relatedCaseTitle, "relatedCaseTitle");
    if (depth < 1) {
      throw new IllegalArgumentException("depth must be at least 1");
    }
    Objects.requireNonNull(direction, "direction must not be null");
    Objects.requireNonNull(relationshipType, "relationshipType must not be null");
    relationshipReason = requireNonBlank(relationshipReason, "relationshipReason");
    pathCaseIds = List.copyOf(pathCaseIds);
    if (pathCaseIds.size() < 2) {
      throw new IllegalArgumentException("pathCaseIds must contain at least two nodes");
    }
  }

  private static String requireNonBlank(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }
}
