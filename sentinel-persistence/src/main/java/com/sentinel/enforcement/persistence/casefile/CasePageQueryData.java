package com.sentinel.enforcement.persistence.casefile;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record CasePageQueryData(
    Set<String> jurisdictionCodes,
    String restrictedAssigneeUserId,
    Set<String> restrictedAssignedUnitIds,
    boolean includeUnassignedWhenUnitRestricted,
    Set<String> allowedClassifications,
    Set<String> excludedCreatedByUserIds,
    String requestedAssigneeUserId,
    String quickSearchPattern,
    String searchField,
    String searchPattern,
    String status,
    String classification,
    String assignedUnitId,
    String createdBy,
    UUID reportId,
    String sortBy,
    String sortDirection,
    OffsetDateTime cursorTimestampValue,
    String cursorTextValue,
    UUID cursorId,
    int limitPlusOne) {}
