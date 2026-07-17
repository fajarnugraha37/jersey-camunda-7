package com.sentinel.enforcement.application.casefile;

import com.sentinel.enforcement.domain.casefile.CaseRelationshipType;
import java.util.Objects;

public record ListCaseRelationshipsQuery(
    CaseRelationshipTraversalDirection direction,
    int maxDepth,
    CaseRelationshipType relationshipType) {

  public ListCaseRelationshipsQuery {
    Objects.requireNonNull(direction, "direction must not be null");
    if (maxDepth < 1 || maxDepth > 25) {
      throw new IllegalArgumentException("maxDepth must be between 1 and 25");
    }
  }
}
