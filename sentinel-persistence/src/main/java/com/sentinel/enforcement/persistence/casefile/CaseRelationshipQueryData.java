package com.sentinel.enforcement.persistence.casefile;

import java.util.UUID;

public record CaseRelationshipQueryData(UUID caseId, int maxDepth, String relationshipType) {}
