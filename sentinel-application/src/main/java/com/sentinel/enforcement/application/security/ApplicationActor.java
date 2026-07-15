package com.sentinel.enforcement.application.security;

import com.sentinel.enforcement.domain.casefile.CaseClassification;
import java.util.EnumSet;
import java.util.Set;

public record ApplicationActor(
    String subject,
    String username,
    Set<String> roles,
    Set<String> jurisdictions,
    Set<String> assignedUnits,
    Set<CaseClassification> caseClassifications,
    Set<String> conflictedActorIds) {

  public ApplicationActor(
      String subject, String username, Set<String> roles, Set<String> jurisdictions) {
    this(
        subject,
        username,
        roles,
        jurisdictions,
        Set.of(),
        EnumSet.allOf(CaseClassification.class),
        Set.of());
  }

  public ApplicationActor {
    roles = Set.copyOf(roles);
    jurisdictions = Set.copyOf(jurisdictions);
    assignedUnits = Set.copyOf(assignedUnits);
    caseClassifications = Set.copyOf(caseClassifications);
    conflictedActorIds = Set.copyOf(conflictedActorIds);
  }

  public boolean hasRole(String role) {
    return roles.contains(role);
  }

  public boolean hasJurisdiction(String jurisdictionCode) {
    return jurisdictions.contains(jurisdictionCode);
  }

  public boolean hasAssignedUnit(String assignedUnitId) {
    return assignedUnits.contains(assignedUnitId);
  }

  public boolean hasCaseClassification(CaseClassification classification) {
    return caseClassifications.contains(classification);
  }

  public boolean isConflictedWith(String actorId) {
    return actorId != null && conflictedActorIds.contains(actorId);
  }
}
