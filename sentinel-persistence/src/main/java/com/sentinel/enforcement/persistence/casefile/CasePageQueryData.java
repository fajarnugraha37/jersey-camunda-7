package com.sentinel.enforcement.persistence.casefile;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record CasePageQueryData(
    Set<String> jurisdictionCodes,
    String assigneeUserId,
    OffsetDateTime cursorCreatedAt,
    UUID cursorId,
    int limitPlusOne) {}
