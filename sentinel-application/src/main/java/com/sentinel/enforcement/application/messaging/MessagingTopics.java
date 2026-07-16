package com.sentinel.enforcement.application.messaging;

import java.util.List;

public final class MessagingTopics {
  public static final String CASE_LIFECYCLE = "case.lifecycle.v1";
  public static final String CASE_ASSIGNMENT = "case.assignment.v1";
  public static final String EVIDENCE_LIFECYCLE = "evidence.lifecycle.v1";
  public static final String DECISION_LIFECYCLE = "decision.lifecycle.v1";
  public static final String SANCTION_LIFECYCLE = "sanction.lifecycle.v1";
  public static final String APPEAL_LIFECYCLE = "appeal.lifecycle.v1";
  public static final String NOTIFICATION_COMMAND = "notification.command.v1";
  public static final String NOTIFICATION_RESULT = "notification.result.v1";
  public static final String AUDIT_INTEGRATION = "audit.integration.v1";

  private MessagingTopics() {}

  public static List<String> domainLifecycleTopics() {
    return List.of(
        CASE_LIFECYCLE,
        CASE_ASSIGNMENT,
        EVIDENCE_LIFECYCLE,
        DECISION_LIFECYCLE,
        SANCTION_LIFECYCLE,
        APPEAL_LIFECYCLE);
  }

  public static List<String> notificationProjectionTopics() {
    return List.of(
        CASE_LIFECYCLE,
        CASE_ASSIGNMENT,
        EVIDENCE_LIFECYCLE,
        DECISION_LIFECYCLE,
        SANCTION_LIFECYCLE,
        APPEAL_LIFECYCLE);
  }

  public static List<String> integrationTopics() {
    return List.of(NOTIFICATION_COMMAND, NOTIFICATION_RESULT, AUDIT_INTEGRATION);
  }
}
