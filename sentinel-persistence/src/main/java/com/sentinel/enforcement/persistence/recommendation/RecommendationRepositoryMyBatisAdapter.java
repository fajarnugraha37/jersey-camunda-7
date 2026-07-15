package com.sentinel.enforcement.persistence.recommendation;

import com.sentinel.enforcement.application.recommendation.RecommendationRepository;
import com.sentinel.enforcement.domain.recommendation.Recommendation;
import com.sentinel.enforcement.domain.recommendation.RecommendationConflictException;
import com.sentinel.enforcement.domain.recommendation.RecommendationReview;
import com.sentinel.enforcement.domain.recommendation.RecommendationStatus;
import com.sentinel.enforcement.persistence.MyBatisRepositorySupport;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;

public final class RecommendationRepositoryMyBatisAdapter extends MyBatisRepositorySupport
    implements RecommendationRepository {
  public RecommendationRepositoryMyBatisAdapter(SqlSessionFactory sqlSessionFactory) {
    super(sqlSessionFactory);
  }

  @Override
  public void save(Recommendation recommendation) {
    executeWrite(
        session -> {
          session
              .getMapper(RecommendationMyBatisMapper.class)
              .insertRecommendation(toRecord(recommendation));
          return null;
        });
  }

  @Override
  public Optional<Recommendation> findById(UUID recommendationId) {
    return executeRead(
        session ->
            Optional.ofNullable(
                    session.getMapper(RecommendationMyBatisMapper.class).findById(recommendationId))
                .map(this::toDomain));
  }

  @Override
  public Optional<Recommendation> findByCaseId(UUID caseId) {
    return executeRead(
        session ->
            Optional.ofNullable(
                    session.getMapper(RecommendationMyBatisMapper.class).findByCaseId(caseId))
                .map(this::toDomain));
  }

  @Override
  public void submit(Recommendation recommendation) {
    executeWrite(
        session -> {
          int updated =
              session
                  .getMapper(RecommendationMyBatisMapper.class)
                  .submit(toRecord(recommendation), recommendation.version() - 1);
          if (updated != 1) {
            throw new RecommendationConflictException(
                "CONCURRENT_MODIFICATION",
                "Recommendation " + recommendation.id() + " was modified concurrently.");
          }
          return null;
        });
  }

  @Override
  public void approve(Recommendation recommendation, RecommendationReview recommendationReview) {
    executeWrite(
        session -> {
          RecommendationMyBatisMapper mapper = session.getMapper(RecommendationMyBatisMapper.class);
          int updated = mapper.approve(toRecord(recommendation), recommendation.version() - 1);
          if (updated != 1) {
            throw new RecommendationConflictException(
                "CONCURRENT_MODIFICATION",
                "Recommendation " + recommendation.id() + " was modified concurrently.");
          }
          mapper.insertReview(toRecord(recommendationReview));
          return null;
        });
  }

  @Override
  public boolean existsApprovedForCase(UUID caseId) {
    return executeRead(
        session ->
            session.getMapper(RecommendationMyBatisMapper.class).existsApprovedForCase(caseId));
  }

  @Override
  public boolean existsSubmittedForCase(UUID caseId) {
    return executeRead(
        session ->
            session.getMapper(RecommendationMyBatisMapper.class).existsSubmittedForCase(caseId));
  }

  private RecommendationRecord toRecord(Recommendation recommendation) {
    return new RecommendationRecord(
        recommendation.id(),
        recommendation.caseId(),
        recommendation.title(),
        recommendation.summary(),
        recommendation.proposedDecision(),
        recommendation.proposedSanction(),
        recommendation.status().name(),
        recommendation.submittedAt() == null
            ? null
            : recommendation.submittedAt().atOffset(ZoneOffset.UTC),
        recommendation.submittedBy(),
        recommendation.approvedReviewId(),
        recommendation.createdAt().atOffset(ZoneOffset.UTC),
        recommendation.createdBy(),
        recommendation.updatedAt().atOffset(ZoneOffset.UTC),
        recommendation.updatedBy(),
        recommendation.version());
  }

  private RecommendationReviewRecord toRecord(RecommendationReview recommendationReview) {
    return new RecommendationReviewRecord(
        recommendationReview.id(),
        recommendationReview.recommendationId(),
        recommendationReview.outcome().name(),
        recommendationReview.reviewSummary(),
        recommendationReview.reviewedAt().atOffset(ZoneOffset.UTC),
        recommendationReview.reviewedBy(),
        recommendationReview.createdAt().atOffset(ZoneOffset.UTC),
        recommendationReview.createdBy(),
        recommendationReview.version());
  }

  private Recommendation toDomain(RecommendationRecord recommendationRecord) {
    return new Recommendation(
        recommendationRecord.id(),
        recommendationRecord.caseId(),
        recommendationRecord.title(),
        recommendationRecord.summary(),
        recommendationRecord.proposedDecision(),
        recommendationRecord.proposedSanction(),
        RecommendationStatus.valueOf(recommendationRecord.status()),
        recommendationRecord.submittedAt() == null
            ? null
            : recommendationRecord.submittedAt().toInstant(),
        recommendationRecord.submittedBy(),
        recommendationRecord.approvedReviewId(),
        recommendationRecord.createdAt().toInstant(),
        recommendationRecord.createdBy(),
        recommendationRecord.updatedAt().toInstant(),
        recommendationRecord.updatedBy(),
        recommendationRecord.version());
  }
}
