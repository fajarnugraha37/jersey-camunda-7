package com.sentinel.enforcement.application.workflow;

import java.util.List;

public record WorkflowTaskPage(
    List<WorkflowTaskView> items,
    String nextCursorValue,
    String nextCursorTaskId,
    boolean hasNextPage) {

  public WorkflowTaskPage {
    items = List.copyOf(items);
    if (hasNextPage && (nextCursorValue == null || nextCursorTaskId == null)) {
      throw new IllegalArgumentException(
          "next cursor fields must be present when hasNextPage is true");
    }
    if (!hasNextPage && (nextCursorValue != null || nextCursorTaskId != null)) {
      throw new IllegalArgumentException(
          "next cursor fields must be absent when hasNextPage is false");
    }
  }
}
