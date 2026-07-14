package com.sentinel.enforcement.api.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sentinel.enforcement.application.casefile.SortDirection;
import com.sentinel.enforcement.application.workflow.ListWorkflowReconciliationIssuesQuery;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationIssueType;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationPage;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationSearchField;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationSortBy;
import com.sentinel.enforcement.domain.casefile.CaseStatus;
import jakarta.ws.rs.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

final class WorkflowReconciliationCursorCodec {
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  private WorkflowReconciliationCursorCodec() {}

  static ListWorkflowReconciliationIssuesQuery decode(
      String cursor,
      int limit,
      String quickSearch,
      String searchField,
      String searchValue,
      String issueType,
      String caseStatus,
      String workflowCorrelationStatus,
      String sortBy,
      String sortDirection) {
    WorkflowReconciliationSearchField parsedSearchField =
        parseEnum(searchField, WorkflowReconciliationSearchField.class, "searchField");
    WorkflowReconciliationIssueType parsedIssueType =
        parseEnum(issueType, WorkflowReconciliationIssueType.class, "issueType");
    CaseStatus parsedCaseStatus = parseEnum(caseStatus, CaseStatus.class, "caseStatus");
    WorkflowReconciliationSortBy parsedSortBy =
        sortBy == null || sortBy.isBlank()
            ? WorkflowReconciliationSortBy.CASE_UPDATED_AT
            : parseEnum(sortBy, WorkflowReconciliationSortBy.class, "sortBy");
    SortDirection parsedSortDirection =
        sortDirection == null || sortDirection.isBlank()
            ? SortDirection.DESC
            : parseEnum(sortDirection, SortDirection.class, "sortDirection");

    ListWorkflowReconciliationIssuesQuery query =
        newQuery(
            null,
            null,
            limit,
            quickSearch,
            parsedSearchField,
            searchValue,
            parsedIssueType,
            parsedCaseStatus,
            workflowCorrelationStatus,
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
        payload.cursorCaseId(),
        limit,
        quickSearch,
        parsedSearchField,
        searchValue,
        parsedIssueType,
        parsedCaseStatus,
        workflowCorrelationStatus,
        parsedSortBy,
        parsedSortDirection);
  }

  static String encode(
      WorkflowReconciliationPage page, ListWorkflowReconciliationIssuesQuery query) {
    if (!page.hasNextPage()) {
      return null;
    }
    CursorPayload payload =
        new CursorPayload(
            query.sortBy().name(),
            query.sortDirection().name(),
            query.cursorScope(),
            page.nextCursorValue(),
            page.nextCursorCaseId());
    try {
      String json = OBJECT_MAPPER.writeValueAsString(payload);
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Failed to encode workflow reconciliation cursor.", exception);
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

  private static void validateCursorValue(String cursorValue, WorkflowReconciliationSortBy sortBy) {
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

  private static ListWorkflowReconciliationIssuesQuery newQuery(
      String cursorValue,
      String cursorCaseId,
      int limit,
      String quickSearch,
      WorkflowReconciliationSearchField searchField,
      String searchValue,
      WorkflowReconciliationIssueType issueType,
      CaseStatus caseStatus,
      String workflowCorrelationStatus,
      WorkflowReconciliationSortBy sortBy,
      SortDirection sortDirection) {
    try {
      return new ListWorkflowReconciliationIssuesQuery(
          cursorValue,
          cursorCaseId,
          limit,
          quickSearch,
          searchField,
          searchValue,
          issueType,
          caseStatus,
          workflowCorrelationStatus,
          sortBy,
          sortDirection);
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException(exception.getMessage(), exception);
    }
  }

  private record CursorPayload(
      String sortBy, String sortDirection, String scope, String cursorValue, String cursorCaseId) {}
}
