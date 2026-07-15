package com.sentinel.enforcement.api.casefile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sentinel.enforcement.application.casefile.CaseListSearchField;
import com.sentinel.enforcement.application.casefile.CaseListSortBy;
import com.sentinel.enforcement.application.casefile.CasePage;
import com.sentinel.enforcement.application.casefile.ListCasesQuery;
import com.sentinel.enforcement.application.casefile.SortDirection;
import com.sentinel.enforcement.domain.casefile.CaseClassification;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import jakarta.ws.rs.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

final class CaseCursorCodec {
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  private CaseCursorCodec() {}

  static ListCasesQuery decode(
      String cursor,
      int limit,
      String quickSearch,
      String searchField,
      String searchValue,
      String status,
      String classification,
      String assignedUnitId,
      String assigneeUserId,
      String createdBy,
      String reportId,
      String sortBy,
      String sortDirection) {
    CaseListSearchField parsedSearchField =
        parseEnum(searchField, CaseListSearchField.class, "searchField");
    CaseStatus parsedStatus = parseEnum(status, CaseStatus.class, "status");
    CaseClassification parsedClassification =
        parseEnum(classification, CaseClassification.class, "classification");
    CaseListSortBy parsedSortBy =
        sortBy == null || sortBy.isBlank()
            ? CaseListSortBy.CREATED_AT
            : parseEnum(sortBy, CaseListSortBy.class, "sortBy");
    SortDirection parsedSortDirection =
        sortDirection == null || sortDirection.isBlank()
            ? SortDirection.DESC
            : parseEnum(sortDirection, SortDirection.class, "sortDirection");
    UUID parsedReportId = parseUuid(reportId, "reportId");

    CursorAwareListCasesQuery query =
        newQuery(
            null,
            null,
            limit,
            quickSearch,
            parsedSearchField,
            searchValue,
            parsedStatus,
            parsedClassification,
            assignedUnitId,
            assigneeUserId,
            createdBy,
            parsedReportId,
            parsedSortBy,
            parsedSortDirection);
    if (cursor == null || cursor.isBlank()) {
      return query.toQuery();
    }
    CursorPayload payload = decodePayload(cursor);
    if (!Objects.equals(payload.sortBy(), query.sortBy().name())
        || !Objects.equals(payload.sortDirection(), query.sortDirection().name())
        || !Objects.equals(payload.scope(), query.cursorScope())) {
      throw new BadRequestException(
          "Cursor does not match the requested search, filter, or sort configuration.");
    }
    validateCursorValue(payload.cursorValue(), query.sortBy());
    return newQuery(
            payload.cursorValue(),
            payload.cursorId(),
            limit,
            quickSearch,
            parsedSearchField,
            searchValue,
            parsedStatus,
            parsedClassification,
            assignedUnitId,
            assigneeUserId,
            createdBy,
            parsedReportId,
            parsedSortBy,
            parsedSortDirection)
        .toQuery();
  }

  static String encode(CasePage casePage, ListCasesQuery query) {
    if (!casePage.hasNextPage()) {
      return null;
    }
    CursorPayload payload =
        new CursorPayload(
            query.sortBy().name(),
            query.sortDirection().name(),
            query.cursorScope(),
            casePage.nextCursorValue(),
            casePage.nextCursorId());
    try {
      String json = OBJECT_MAPPER.writeValueAsString(payload);
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to encode case cursor.", exception);
    }
  }

  private static CursorPayload decodePayload(String cursor) {
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      return OBJECT_MAPPER.readValue(decoded, CursorPayload.class);
    } catch (RuntimeException | JsonProcessingException exception) {
      throw new BadRequestException("Cursor is invalid.", exception);
    }
  }

  private static void validateCursorValue(String cursorValue, CaseListSortBy sortBy) {
    try {
      if (sortBy.isTimestampBased()) {
        Instant.parse(cursorValue);
      } else if (cursorValue == null || cursorValue.isBlank()) {
        throw new IllegalArgumentException("Cursor value must not be blank.");
      }
    } catch (RuntimeException exception) {
      throw new BadRequestException("Cursor is invalid.", exception);
    }
  }

  private static <E extends Enum<E>> E parseEnum(
      String value, Class<E> enumType, String fieldName) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException(fieldName + " is invalid.", exception);
    }
  }

  private static UUID parseUuid(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value.trim());
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException(fieldName + " must be a UUID.", exception);
    }
  }

  private static CursorAwareListCasesQuery newQuery(
      String cursorValue,
      UUID cursorId,
      int limit,
      String quickSearch,
      CaseListSearchField searchField,
      String searchValue,
      CaseStatus status,
      CaseClassification classification,
      String assignedUnitId,
      String assigneeUserId,
      String createdBy,
      UUID reportId,
      CaseListSortBy sortBy,
      SortDirection sortDirection) {
    try {
      return new CursorAwareListCasesQuery(
          cursorValue,
          cursorId,
          limit,
          quickSearch,
          searchField,
          searchValue,
          status,
          classification,
          assignedUnitId,
          assigneeUserId,
          createdBy,
          reportId,
          sortBy,
          sortDirection);
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException(exception.getMessage(), exception);
    }
  }

  private record CursorPayload(
      String sortBy, String sortDirection, String scope, String cursorValue, UUID cursorId) {}

  private static final class CursorAwareListCasesQuery {
    private final String cursorValue;
    private final UUID cursorId;
    private final int limit;
    private final String quickSearch;
    private final CaseListSearchField searchField;
    private final String searchValue;
    private final CaseStatus status;
    private final CaseClassification classification;
    private final String assignedUnitId;
    private final String assigneeUserId;
    private final String createdBy;
    private final UUID reportId;
    private final CaseListSortBy sortBy;
    private final SortDirection sortDirection;

    private CursorAwareListCasesQuery(
        String cursorValue,
        UUID cursorId,
        int limit,
        String quickSearch,
        CaseListSearchField searchField,
        String searchValue,
        CaseStatus status,
        CaseClassification classification,
        String assignedUnitId,
        String assigneeUserId,
        String createdBy,
        UUID reportId,
        CaseListSortBy sortBy,
        SortDirection sortDirection) {
      this.cursorValue = cursorValue;
      this.cursorId = cursorId;
      this.limit = limit;
      this.quickSearch = quickSearch;
      this.searchField = searchField;
      this.searchValue = searchValue;
      this.status = status;
      this.classification = classification;
      this.assignedUnitId = assignedUnitId;
      this.assigneeUserId = assigneeUserId;
      this.createdBy = createdBy;
      this.reportId = reportId;
      this.sortBy = sortBy;
      this.sortDirection = sortDirection;
    }

    private CursorAwareListCasesQuery withCursor(String nextCursorValue, UUID nextCursorId) {
      return new CursorAwareListCasesQuery(
          nextCursorValue,
          nextCursorId,
          limit,
          quickSearch,
          searchField,
          searchValue,
          status,
          classification,
          assignedUnitId,
          assigneeUserId,
          createdBy,
          reportId,
          sortBy,
          sortDirection);
    }

    private String cursorScope() {
      return toQuery().cursorScope();
    }

    private ListCasesQuery toQuery() {
      try {
        return new ListCasesQuery(
            cursorValue,
            cursorId,
            limit,
            quickSearch,
            searchField,
            searchValue,
            status,
            classification,
            assignedUnitId,
            assigneeUserId,
            createdBy,
            reportId,
            sortBy,
            sortDirection);
      } catch (IllegalArgumentException exception) {
        throw new BadRequestException(exception.getMessage(), exception);
      }
    }

    private CaseListSortBy sortBy() {
      return sortBy;
    }

    private SortDirection sortDirection() {
      return sortDirection;
    }
  }
}
