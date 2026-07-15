package com.sentinel.enforcement.domain.recommendation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Recommendation(
    UUID id,
    UUID caseId,
    String title,
    String summary,
    String proposedDecision,
    String proposedSanction,
    RecommendationStatus status,
    Instant submittedAt,
    String submittedBy,
    UUID approvedReviewId,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    long version) {

  public Recommendation {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(caseId, "caseId must not be null");
    title = requireNonBlank(title, "title");
    summary = requireNonBlank(summary, "summary");
    proposedDecision = requireNonBlank(proposedDecision, "proposedDecision");
    if (proposedSanction != null && proposedSanction.isBlank()) {
      throw new IllegalArgumentException("proposedSanction must not be blank when provided");
    }
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    createdBy = requireNonBlank(createdBy, "createdBy");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    updatedBy = requireNonBlank(updatedBy, "updatedBy");
    if (submittedBy != null && submittedBy.isBlank()) {
      throw new IllegalArgumentException("submittedBy must not be blank when provided");
    }
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
  }

  public static Recommendation create(
      UUID id,
      UUID caseId,
      String title,
      String summary,
      String proposedDecision,
      String proposedSanction,
      Instant now,
      String actorId) {
    return new Recommendation(
        id,
        caseId,
        title,
        summary,
        proposedDecision,
        proposedSanction,
        RecommendationStatus.DRAFT,
        null,
        null,
        null,
        now,
        actorId,
        now,
        actorId,
        0L);
  }

  public Recommendation submit(Instant now, String actorId) {
    if (status != RecommendationStatus.DRAFT) {
      throw new RecommendationConflictException(
          "RECOMMENDATION_SUBMIT_NOT_ALLOWED",
          "Recommendation " + id + " cannot be submitted from status " + status + ".");
    }
    return new Recommendation(
        id,
        caseId,
        title,
        summary,
        proposedDecision,
        proposedSanction,
        RecommendationStatus.SUBMITTED,
        now,
        actorId,
        approvedReviewId,
        createdAt,
        createdBy,
        now,
        actorId,
        version + 1);
  }

  public Recommendation approve(UUID reviewId, Instant now, String actorId) {
    if (status != RecommendationStatus.SUBMITTED) {
      throw new RecommendationConflictException(
          "RECOMMENDATION_REVIEW_NOT_ALLOWED",
          "Recommendation " + id + " cannot be approved from status " + status + ".");
    }
    if (createdBy.equals(actorId)) {
      throw new RecommendationConflictException(
          "MAKER_CHECKER_VIOLATION",
          "Recommendation author cannot approve their own recommendation.");
    }
    return new Recommendation(
        id,
        caseId,
        title,
        summary,
        proposedDecision,
        proposedSanction,
        RecommendationStatus.APPROVED,
        submittedAt,
        submittedBy,
        reviewId,
        createdAt,
        createdBy,
        now,
        actorId,
        version + 1);
  }

  public String auditSummary() {
    return "status="
        + status
        + ";submittedBy="
        + valueOrDash(submittedBy)
        + ";approvedReviewId="
        + valueOrDash(approvedReviewId == null ? null : approvedReviewId.toString())
        + ";version="
        + version;
  }

  private static String requireNonBlank(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }

  private static String valueOrDash(String value) {
    return value == null ? "-" : value;
  }
}
