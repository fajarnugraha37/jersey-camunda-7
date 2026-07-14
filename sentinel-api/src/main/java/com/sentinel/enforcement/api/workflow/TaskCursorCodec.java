package com.sentinel.enforcement.api.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sentinel.enforcement.application.casefile.SortDirection;
import com.sentinel.enforcement.application.workflow.ListWorkflowTasksQuery;
import com.sentinel.enforcement.application.workflow.WorkflowTaskPage;
import com.sentinel.enforcement.application.workflow.WorkflowTaskSearchField;
import com.sentinel.enforcement.application.workflow.WorkflowTaskSortBy;
import com.sentinel.enforcement.application.workflow.WorkflowTaskState;
import jakarta.ws.rs.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

final class TaskCursorCodec {
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  private TaskCursorCodec() {}

  static ListWorkflowTasksQuery decode(
      String cursor,
      int limit,
      String quickSearch,
      String searchField,
      String searchValue,
      String caseId,
      String assigneeUserId,
      String state,
      String sortBy,
      String sortDirection) {
    WorkflowTaskSearchField parsedSearchField =
        parseEnum(searchField, WorkflowTaskSearchField.class, "searchField");
    WorkflowTaskState parsedState = parseEnum(state, WorkflowTaskState.class, "state");
    WorkflowTaskSortBy parsedSortBy =
        sortBy == null || sortBy.isBlank()
            ? WorkflowTaskSortBy.CREATED_AT
            : parseEnum(sortBy, WorkflowTaskSortBy.class, "sortBy");
    SortDirection parsedSortDirection =
        sortDirection == null || sortDirection.isBlank()
            ? SortDirection.DESC
            : parseEnum(sortDirection, SortDirection.class, "sortDirection");
    UUID parsedCaseId = parseUuid(caseId, "caseId");

    ListWorkflowTasksQuery query =
        newQuery(
            null,
            null,
            limit,
            quickSearch,
            parsedSearchField,
            searchValue,
            parsedCaseId,
            assigneeUserId,
            parsedState,
            parsedSortBy,
            parsedSortDirection);
    if (cursor == null || cursor.isBlank()) {
      return query;
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
        payload.cursorTaskId(),
        limit,
        quickSearch,
        parsedSearchField,
        searchValue,
        parsedCaseId,
        assigneeUserId,
        parsedState,
        parsedSortBy,
        parsedSortDirection);
  }

  static String encode(WorkflowTaskPage page, ListWorkflowTasksQuery query) {
    if (!page.hasNextPage()) {
      return null;
    }
    CursorPayload payload =
        new CursorPayload(
            query.sortBy().name(),
            query.sortDirection().name(),
            query.cursorScope(),
            page.nextCursorValue(),
            page.nextCursorTaskId());
    try {
      String json = OBJECT_MAPPER.writeValueAsString(payload);
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to encode workflow task cursor.", exception);
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

  private static void validateCursorValue(String cursorValue, WorkflowTaskSortBy sortBy) {
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

  private static ListWorkflowTasksQuery newQuery(
      String cursorValue,
      String cursorTaskId,
      int limit,
      String quickSearch,
      WorkflowTaskSearchField searchField,
      String searchValue,
      UUID caseId,
      String assigneeUserId,
      WorkflowTaskState state,
      WorkflowTaskSortBy sortBy,
      SortDirection sortDirection) {
    try {
      return new ListWorkflowTasksQuery(
          cursorValue,
          cursorTaskId,
          limit,
          quickSearch,
          searchField,
          searchValue,
          caseId,
          assigneeUserId,
          state,
          sortBy,
          sortDirection);
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException(exception.getMessage(), exception);
    }
  }

  private record CursorPayload(
      String sortBy, String sortDirection, String scope, String cursorValue, String cursorTaskId) {}
}
