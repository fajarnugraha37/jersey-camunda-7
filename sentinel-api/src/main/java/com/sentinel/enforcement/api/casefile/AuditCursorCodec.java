package com.sentinel.enforcement.api.casefile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sentinel.enforcement.application.casefile.AuditEventListSearchField;
import com.sentinel.enforcement.application.casefile.AuditEventListSortBy;
import com.sentinel.enforcement.application.casefile.AuditEventPage;
import com.sentinel.enforcement.application.casefile.ListCaseAuditEventsQuery;
import com.sentinel.enforcement.application.casefile.SortDirection;
import jakarta.ws.rs.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

final class AuditCursorCodec {
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  private AuditCursorCodec() {}

  static ListCaseAuditEventsQuery decode(
      String cursor,
      int limit,
      String quickSearch,
      String searchField,
      String searchValue,
      String actorId,
      String eventType,
      String action,
      String result,
      String sortBy,
      String sortDirection) {
    AuditEventListSearchField parsedSearchField =
        parseEnum(searchField, AuditEventListSearchField.class, "searchField");
    AuditEventListSortBy parsedSortBy =
        sortBy == null || sortBy.isBlank()
            ? AuditEventListSortBy.TIMESTAMP
            : parseEnum(sortBy, AuditEventListSortBy.class, "sortBy");
    SortDirection parsedSortDirection =
        sortDirection == null || sortDirection.isBlank()
            ? SortDirection.DESC
            : parseEnum(sortDirection, SortDirection.class, "sortDirection");

    CursorAwareAuditQuery query =
        newQuery(
            null,
            null,
            limit,
            quickSearch,
            parsedSearchField,
            searchValue,
            actorId,
            eventType,
            action,
            result,
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
            actorId,
            eventType,
            action,
            result,
            parsedSortBy,
            parsedSortDirection)
        .toQuery();
  }

  static String encode(AuditEventPage page, ListCaseAuditEventsQuery query) {
    if (!page.hasNextPage()) {
      return null;
    }
    CursorPayload payload =
        new CursorPayload(
            query.sortBy().name(),
            query.sortDirection().name(),
            query.cursorScope(),
            page.nextCursorValue(),
            page.nextCursorId());
    try {
      String json = OBJECT_MAPPER.writeValueAsString(payload);
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to encode audit cursor.", exception);
    }
  }

  private static void validateCursorValue(String cursorValue, AuditEventListSortBy sortBy) {
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

  private static CursorPayload decodePayload(String cursor) {
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      return OBJECT_MAPPER.readValue(decoded, CursorPayload.class);
    } catch (RuntimeException | JsonProcessingException exception) {
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

  private static CursorAwareAuditQuery newQuery(
      String cursorValue,
      UUID cursorId,
      int limit,
      String quickSearch,
      AuditEventListSearchField searchField,
      String searchValue,
      String actorId,
      String eventType,
      String action,
      String result,
      AuditEventListSortBy sortBy,
      SortDirection sortDirection) {
    try {
      return new CursorAwareAuditQuery(
          cursorValue,
          cursorId,
          limit,
          quickSearch,
          searchField,
          searchValue,
          actorId,
          eventType,
          action,
          result,
          sortBy,
          sortDirection);
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException(exception.getMessage(), exception);
    }
  }

  private record CursorPayload(
      String sortBy, String sortDirection, String scope, String cursorValue, UUID cursorId) {}

  private static final class CursorAwareAuditQuery {
    private final String cursorValue;
    private final UUID cursorId;
    private final int limit;
    private final String quickSearch;
    private final AuditEventListSearchField searchField;
    private final String searchValue;
    private final String actorId;
    private final String eventType;
    private final String action;
    private final String result;
    private final AuditEventListSortBy sortBy;
    private final SortDirection sortDirection;

    private CursorAwareAuditQuery(
        String cursorValue,
        UUID cursorId,
        int limit,
        String quickSearch,
        AuditEventListSearchField searchField,
        String searchValue,
        String actorId,
        String eventType,
        String action,
        String result,
        AuditEventListSortBy sortBy,
        SortDirection sortDirection) {
      this.cursorValue = cursorValue;
      this.cursorId = cursorId;
      this.limit = limit;
      this.quickSearch = quickSearch;
      this.searchField = searchField;
      this.searchValue = searchValue;
      this.actorId = actorId;
      this.eventType = eventType;
      this.action = action;
      this.result = result;
      this.sortBy = sortBy;
      this.sortDirection = sortDirection;
    }

    private CursorAwareAuditQuery withCursor(String nextCursorValue, UUID nextCursorId) {
      return new CursorAwareAuditQuery(
          nextCursorValue,
          nextCursorId,
          limit,
          quickSearch,
          searchField,
          searchValue,
          actorId,
          eventType,
          action,
          result,
          sortBy,
          sortDirection);
    }

    private String cursorScope() {
      return toQuery().cursorScope();
    }

    private ListCaseAuditEventsQuery toQuery() {
      try {
        return new ListCaseAuditEventsQuery(
            cursorValue,
            cursorId,
            limit,
            quickSearch,
            searchField,
            searchValue,
            actorId,
            eventType,
            action,
            result,
            sortBy,
            sortDirection);
      } catch (IllegalArgumentException exception) {
        throw new BadRequestException(exception.getMessage(), exception);
      }
    }

    private AuditEventListSortBy sortBy() {
      return sortBy;
    }

    private SortDirection sortDirection() {
      return sortDirection;
    }
  }
}
