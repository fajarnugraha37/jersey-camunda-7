package com.sentinel.enforcement.persistence.casefile;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditEventPageQueryData(
    UUID caseId,
    String quickSearchPattern,
    String searchField,
    String searchPattern,
    String actorId,
    String eventType,
    String action,
    String result,
    String sortBy,
    String sortDirection,
    OffsetDateTime cursorTimestampValue,
    String cursorTextValue,
    UUID cursorId,
    int limitPlusOne) {}
