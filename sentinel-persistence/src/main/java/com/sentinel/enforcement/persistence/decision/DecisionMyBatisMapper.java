package com.sentinel.enforcement.persistence.decision;

import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DecisionMyBatisMapper {
  @Insert(
      """
      INSERT INTO decision (
          id, case_id, recommendation_id, title, summary, violation_proven, sanction_summary,
          obligation_title, obligation_details, obligation_due_date, appeal_deadline, status,
          approved_at, approved_by, published_at, published_by, created_at, created_by, updated_at, updated_by, version
      ) VALUES (
          #{id}, #{caseId}, #{recommendationId}, #{title}, #{summary}, #{violationProven}, #{sanctionSummary},
          #{obligationTitle}, #{obligationDetails}, #{obligationDueDate}, #{appealDeadline}, #{status},
          #{approvedAt}, #{approvedBy}, #{publishedAt}, #{publishedBy}, #{createdAt}, #{createdBy}, #{updatedAt}, #{updatedBy}, #{version}
      )
      """)
  int insertDecision(DecisionRecord decisionRecord);

  @Select(
      """
      SELECT
          id, case_id AS caseId, recommendation_id AS recommendationId, title, summary,
          violation_proven AS violationProven, sanction_summary AS sanctionSummary,
          obligation_title AS obligationTitle, obligation_details AS obligationDetails,
          obligation_due_date AS obligationDueDate, appeal_deadline AS appealDeadline,
          status, approved_at AS approvedAt, approved_by AS approvedBy,
          published_at AS publishedAt, published_by AS publishedBy, created_at AS createdAt,
          created_by AS createdBy, updated_at AS updatedAt, updated_by AS updatedBy, version
      FROM decision
      WHERE id = #{id}
      """)
  DecisionRecord findById(UUID id);

  @Select(
      """
      SELECT
          id, case_id AS caseId, recommendation_id AS recommendationId, title, summary,
          violation_proven AS violationProven, sanction_summary AS sanctionSummary,
          obligation_title AS obligationTitle, obligation_details AS obligationDetails,
          obligation_due_date AS obligationDueDate, appeal_deadline AS appealDeadline,
          status, approved_at AS approvedAt, approved_by AS approvedBy,
          published_at AS publishedAt, published_by AS publishedBy, created_at AS createdAt,
          created_by AS createdBy, updated_at AS updatedAt, updated_by AS updatedBy, version
      FROM decision
      WHERE case_id = #{caseId}
      """)
  DecisionRecord findByCaseId(UUID caseId);

  @Update(
      """
      UPDATE decision
      SET status = #{decision.status},
          approved_at = #{decision.approvedAt},
          approved_by = #{decision.approvedBy},
          updated_at = #{decision.updatedAt},
          updated_by = #{decision.updatedBy},
          version = #{decision.version}
      WHERE id = #{decision.id}
        AND version = #{expectedVersion}
      """)
  int approve(@Param("decision") DecisionRecord decision, @Param("expectedVersion") long expectedVersion);

  @Update(
      """
      UPDATE decision
      SET status = #{decision.status},
          published_at = #{decision.publishedAt},
          published_by = #{decision.publishedBy},
          updated_at = #{decision.updatedAt},
          updated_by = #{decision.updatedBy},
          version = #{decision.version}
      WHERE id = #{decision.id}
        AND version = #{expectedVersion}
      """)
  int publish(@Param("decision") DecisionRecord decision, @Param("expectedVersion") long expectedVersion);

  @Insert(
      """
      INSERT INTO decision_version (
          id, decision_id, version_number, title, summary, violation_proven, sanction_summary,
          obligation_title, obligation_details, obligation_due_date, appeal_deadline,
          published_at, published_by, created_at, created_by
      ) VALUES (
          #{id}, #{decisionId}, #{versionNumber}, #{title}, #{summary}, #{violationProven}, #{sanctionSummary},
          #{obligationTitle}, #{obligationDetails}, #{obligationDueDate}, #{appealDeadline},
          #{publishedAt}, #{publishedBy}, #{createdAt}, #{createdBy}
      )
      """)
  int insertDecisionVersion(DecisionVersionRecord decisionVersionRecord);

  @Insert(
      """
      INSERT INTO sanction (
          id, case_id, decision_id, summary, status, created_at, created_by, updated_at, updated_by, version
      ) VALUES (
          #{id}, #{caseId}, #{decisionId}, #{summary}, #{status}, #{createdAt}, #{createdBy}, #{updatedAt}, #{updatedBy}, #{version}
      )
      """)
  int insertSanction(SanctionRecord sanctionRecord);

  @Insert(
      """
      INSERT INTO sanction_obligation (
          id, sanction_id, title, details, due_date, status, created_at, created_by, updated_at, updated_by, version
      ) VALUES (
          #{id}, #{sanctionId}, #{title}, #{details}, #{dueDate}, #{status}, #{createdAt}, #{createdBy}, #{updatedAt}, #{updatedBy}, #{version}
      )
      """)
  int insertSanctionObligation(SanctionObligationRecord sanctionObligationRecord);

  @Select("SELECT COUNT(*) FROM decision WHERE case_id = #{caseId} AND status = 'PUBLISHED'")
  boolean existsPublishedForCase(UUID caseId);

  @Select(
      """
      SELECT
          id, case_id AS caseId, decision_id AS decisionId, summary, status,
          created_at AS createdAt, created_by AS createdBy, updated_at AS updatedAt, updated_by AS updatedBy, version
      FROM sanction
      WHERE decision_id = #{decisionId}
      """)
  SanctionRecord findSanctionByDecisionId(UUID decisionId);

  @Select(
      """
      SELECT
          id, case_id AS caseId, decision_id AS decisionId, summary, status,
          created_at AS createdAt, created_by AS createdBy, updated_at AS updatedAt, updated_by AS updatedBy, version
      FROM sanction
      WHERE case_id = #{caseId}
      """)
  SanctionRecord findSanctionByCaseId(UUID caseId);

  @Select("SELECT COUNT(*) FROM sanction_obligation so JOIN sanction s ON s.id = so.sanction_id WHERE s.case_id = #{caseId} AND so.status = 'ACTIVE'")
  long countActiveObligationsForCase(UUID caseId);

  @Select(
      """
      SELECT
          id, sanction_id AS sanctionId, title, details, due_date AS dueDate, status,
          created_at AS createdAt, created_by AS createdBy, updated_at AS updatedAt, updated_by AS updatedBy, version
      FROM sanction_obligation
      WHERE sanction_id = #{sanctionId}
        AND status = 'ACTIVE'
      """)
  SanctionObligationRecord findActiveObligationBySanctionId(UUID sanctionId);

  @Update(
      """
      UPDATE sanction
      SET status = #{sanction.status},
          updated_at = #{sanction.updatedAt},
          updated_by = #{sanction.updatedBy},
          version = #{sanction.version}
      WHERE id = #{sanction.id}
        AND version = #{expectedVersion}
      """)
  int cancelSanction(@Param("sanction") SanctionRecord sanction, @Param("expectedVersion") long expectedVersion);

  @Update(
      """
      UPDATE sanction_obligation
      SET status = #{obligation.status},
          updated_at = #{obligation.updatedAt},
          updated_by = #{obligation.updatedBy},
          version = #{obligation.version}
      WHERE id = #{obligation.id}
        AND version = #{expectedVersion}
      """)
  int cancelSanctionObligation(
      @Param("obligation") SanctionObligationRecord obligation,
      @Param("expectedVersion") long expectedVersion);
}
