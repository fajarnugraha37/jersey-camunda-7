package com.sentinel.enforcement.persistence.casefile;

import java.util.UUID;

public record CaseRelationshipLineageData(
    UUID caseId,
    UUID relatedCaseId,
    String relatedCaseNumber,
    String relatedCaseTitle,
    int depth,
    String direction,
    String relationshipType,
    String relationshipReason,
    String pathCaseIdsCsv) {}
