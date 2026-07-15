package com.sentinel.enforcement.persistence.recommendation;

import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RecommendationMyBatisMapper {
  @Insert(
      """
      INSERT INTO recommendation (
          id,
          case_id,
          title,
          summary,
          proposed_decision,
          proposed_sanction,
          status,
          submitted_at,
          submitted_by,
          approved_review_id,
          created_at,
          created_by,
          updated_at,
          updated_by,
          version
      ) VALUES (
          #{id},
          #{caseId},
          #{title},
          #{summary},
          #{proposedDecision},
          #{proposedSanction},
          #{status},
          #{submittedAt},
          #{submittedBy},
          #{approvedReviewId},
          #{createdAt},
          #{createdBy},
          #{updatedAt},
          #{updatedBy},
          #{version}
      )
      """)
  int insertRecommendation(RecommendationRecord recommendationRecord);

  @Select(
      """
      SELECT
          id,
          case_id AS caseId,
          title,
          summary,
          proposed_decision AS proposedDecision,
          proposed_sanction AS proposedSanction,
          status,
          submitted_at AS submittedAt,
          submitted_by AS submittedBy,
          approved_review_id AS approvedReviewId,
          created_at AS createdAt,
          created_by AS createdBy,
          updated_at AS updatedAt,
          updated_by AS updatedBy,
          version
      FROM recommendation
      WHERE id = #{id}
      """)
  RecommendationRecord findById(UUID id);

  @Select(
      """
      SELECT
          id,
          case_id AS caseId,
          title,
          summary,
          proposed_decision AS proposedDecision,
          proposed_sanction AS proposedSanction,
          status,
          submitted_at AS submittedAt,
          submitted_by AS submittedBy,
          approved_review_id AS approvedReviewId,
          created_at AS createdAt,
          created_by AS createdBy,
          updated_at AS updatedAt,
          updated_by AS updatedBy,
          version
      FROM recommendation
      WHERE case_id = #{caseId}
      """)
  RecommendationRecord findByCaseId(UUID caseId);

  @Update(
      """
      UPDATE recommendation
      SET status = #{recommendation.status},
          submitted_at = #{recommendation.submittedAt},
          submitted_by = #{recommendation.submittedBy},
          updated_at = #{recommendation.updatedAt},
          updated_by = #{recommendation.updatedBy},
          version = #{recommendation.version}
      WHERE id = #{recommendation.id}
        AND version = #{expectedVersion}
      """)
  int submit(
      @Param("recommendation") RecommendationRecord recommendation,
      @Param("expectedVersion") long expectedVersion);

  @Update(
      """
      UPDATE recommendation
      SET status = #{recommendation.status},
          approved_review_id = #{recommendation.approvedReviewId},
          updated_at = #{recommendation.updatedAt},
          updated_by = #{recommendation.updatedBy},
          version = #{recommendation.version}
      WHERE id = #{recommendation.id}
        AND version = #{expectedVersion}
      """)
  int approve(
      @Param("recommendation") RecommendationRecord recommendation,
      @Param("expectedVersion") long expectedVersion);

  @Insert(
      """
      INSERT INTO recommendation_review (
          id,
          recommendation_id,
          outcome,
          review_summary,
          reviewed_at,
          reviewed_by,
          created_at,
          created_by,
          version
      ) VALUES (
          #{id},
          #{recommendationId},
          #{outcome},
          #{reviewSummary},
          #{reviewedAt},
          #{reviewedBy},
          #{createdAt},
          #{createdBy},
          #{version}
      )
      """)
  int insertReview(RecommendationReviewRecord recommendationReviewRecord);

  @Select("SELECT COUNT(*) FROM recommendation WHERE case_id = #{caseId} AND status = 'APPROVED'")
  boolean existsApprovedForCase(UUID caseId);

  @Select("SELECT COUNT(*) FROM recommendation WHERE case_id = #{caseId} AND status = 'SUBMITTED'")
  boolean existsSubmittedForCase(UUID caseId);
}
