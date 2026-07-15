package com.sentinel.enforcement.domain.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DecisionTest {

  @Test
  void authorCannotApproveTheirOwnDraftDecision() {
    Decision draft = draftDecision();

    DecisionConflictException exception =
        assertThrows(
            DecisionConflictException.class,
            () -> draft.approve(Instant.parse("2026-07-14T11:00:00Z"), "decision-jkt"));

    assertEquals("MAKER_CHECKER_VIOLATION", exception.code());
  }

  @Test
  void publishedDecisionIsImmutable() {
    Decision published =
        draftDecision()
            .approve(Instant.parse("2026-07-14T11:00:00Z"), "supervisor-jkt")
            .publish(Instant.parse("2026-07-14T11:15:00Z"), "decision-jkt");

    DecisionConflictException exception =
        assertThrows(
            DecisionConflictException.class,
            () -> published.publish(Instant.parse("2026-07-14T11:30:00Z"), "decision-jkt"));

    assertEquals("DECISION_PUBLICATION_NOT_ALLOWED", exception.code());
  }

  private static Decision draftDecision() {
    return Decision.create(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Decision title",
        "Decision summary",
        false,
        null,
        null,
        null,
        null,
        LocalDate.parse("2026-08-01"),
        Instant.parse("2026-07-14T10:00:00Z"),
        "decision-jkt");
  }
}
