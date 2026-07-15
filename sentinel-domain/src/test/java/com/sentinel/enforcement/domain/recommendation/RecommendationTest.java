package com.sentinel.enforcement.domain.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationTest {

  @Test
  void authorCannotApproveTheirOwnSubmittedRecommendation() {
    Recommendation submitted =
        Recommendation.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Gift disclosure recommendation",
                "Escalate to formal decision.",
                "Proceed to decision.",
                null,
                Instant.parse("2026-07-14T10:00:00Z"),
                "investigator-jkt")
            .submit(Instant.parse("2026-07-14T10:15:00Z"), "investigator-jkt");

    RecommendationConflictException exception =
        assertThrows(
            RecommendationConflictException.class,
            () ->
                submitted.approve(
                    UUID.randomUUID(),
                    Instant.parse("2026-07-14T10:30:00Z"),
                    "investigator-jkt"));

    assertEquals("MAKER_CHECKER_VIOLATION", exception.code());
  }
}
