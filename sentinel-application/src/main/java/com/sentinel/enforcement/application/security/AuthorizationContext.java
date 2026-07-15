package com.sentinel.enforcement.application.security;

import com.sentinel.enforcement.domain.casefile.CaseClassification;
import java.util.UUID;

public record AuthorizationContext(
    String jurisdictionCode,
    String resourceType,
    String resourceId,
    UUID caseId,
    String assigneeUserId,
    String assignedUnitId,
    CaseClassification caseClassification,
    String resourceOwnerId,
    CaseAuthorizationScope authorizationScope) {

  public AuthorizationContext(
      String jurisdictionCode, String resourceType, String resourceId, String assigneeUserId) {
    this(
        jurisdictionCode,
        resourceType,
        resourceId,
        null,
        assigneeUserId,
        null,
        null,
        null,
        CaseAuthorizationScope.NONE);
  }
}
