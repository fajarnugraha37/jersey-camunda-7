package com.sentinel.enforcement.domain.casefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CaseRecordTest {

  @Test
  void transitionHappyPathIncrementsVersionAndUpdatesStatus() {
    CaseRecord caseRecord = createdCase();

    CaseRecord underTriage =
        caseRecord.transitionTo(
            CaseStatus.UNDER_TRIAGE,
            new CaseActionContext(
                "triage-jkt",
                Set.of("TRIAGE_OFFICER"),
                caseRecord.version(),
                "Triage started.",
                Instant.parse("2026-07-14T10:05:00Z")));

    assertEquals(CaseStatus.UNDER_TRIAGE, underTriage.status());
    assertEquals(1L, underTriage.version());
    assertEquals("triage-jkt", underTriage.updatedBy());
  }

  @Test
  void rejectsTransitionWhenRoleDoesNotOwnThatState() {
    CaseRecord caseRecord = createdCase();

    CaseConflictException exception =
        assertThrows(
            CaseConflictException.class,
            () ->
                caseRecord.transitionTo(
                    CaseStatus.UNDER_TRIAGE,
                    new CaseActionContext(
                        "investigator-jkt",
                        Set.of("INVESTIGATOR"),
                        caseRecord.version(),
                        "Skipping triage ownership.",
                        Instant.parse("2026-07-14T10:05:00Z"))));

    assertEquals("CASE_TRANSITION_NOT_ALLOWED", exception.code());
  }

  @Test
  void rejectsStaleVersionBeforeMutatingCase() {
    CaseRecord caseRecord = createdCase();

    CaseConflictException exception =
        assertThrows(
            CaseConflictException.class,
            () ->
                caseRecord.assignTo(
                    "JKT-UNIT-1",
                    "investigator-jkt",
                    new CaseActionContext(
                        "triage-jkt",
                        Set.of("TRIAGE_OFFICER"),
                        99L,
                        "Stale command retry.",
                        Instant.parse("2026-07-14T10:05:00Z"))));

    assertEquals("CONCURRENT_MODIFICATION", exception.code());
  }

  @Test
  void rejectsAssignmentAfterCaseIsClosed() {
    CaseRecord closedCase =
        new CaseRecord(
            UUID.randomUUID(),
            "JKT-ENF-2026-00000010",
            UUID.randomUUID(),
            "Closed case",
            "No more writes allowed.",
            "JKT",
            CaseClassification.CONFIDENTIAL,
            CaseStatus.CLOSED,
            null,
            null,
            Instant.parse("2026-07-14T10:00:00Z"),
            "triage-jkt",
            Instant.parse("2026-07-14T10:00:00Z"),
            "triage-jkt",
            4L);

    CaseConflictException exception =
        assertThrows(
            CaseConflictException.class,
            () ->
                closedCase.assignTo(
                    "JKT-UNIT-1",
                    "investigator-jkt",
                    new CaseActionContext(
                        "supervisor-jkt",
                        Set.of("SUPERVISOR"),
                        closedCase.version(),
                        "Attempted reopen through assignment.",
                        Instant.parse("2026-07-14T10:05:00Z"))));

    assertEquals("CASE_ASSIGNMENT_NOT_ALLOWED", exception.code());
  }

  private static CaseRecord createdCase() {
    return CaseRecord.create(
        UUID.randomUUID(),
        "JKT-ENF-2026-00000001",
        UUID.randomUUID(),
        "Gift disclosure case",
        "Initial case summary.",
        "JKT",
        CaseClassification.CONFIDENTIAL,
        Instant.parse("2026-07-14T10:00:00Z"),
        "triage-jkt");
  }
}
