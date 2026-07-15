package com.sentinel.enforcement.persistence.appeal;

import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppealMyBatisMapper {
  @Insert(
      """
      INSERT INTO appeal (
          id, case_id, decision_id, rationale, supervisor_override, supervisor_override_reason, status,
          submitted_at, submitted_by, decided_by_appeal_decision_id, created_at, created_by, updated_at, updated_by, version
      ) VALUES (
          #{id}, #{caseId}, #{decisionId}, #{rationale}, #{supervisorOverride}, #{supervisorOverrideReason}, #{status},
          #{submittedAt}, #{submittedBy}, #{decidedByAppealDecisionId}, #{createdAt}, #{createdBy}, #{updatedAt}, #{updatedBy}, #{version}
      )
      """)
  int insertAppeal(AppealRecord appealRecord);

  @Select(
      """
      SELECT
          id, case_id AS caseId, decision_id AS decisionId, rationale, supervisor_override AS supervisorOverride,
          supervisor_override_reason AS supervisorOverrideReason, status, submitted_at AS submittedAt,
          submitted_by AS submittedBy, decided_by_appeal_decision_id AS decidedByAppealDecisionId,
          created_at AS createdAt, created_by AS createdBy, updated_at AS updatedAt, updated_by AS updatedBy, version
      FROM appeal
      WHERE id = #{id}
      """)
  AppealRecord findById(UUID id);

  @Select(
      """
      SELECT
          id, case_id AS caseId, decision_id AS decisionId, rationale, supervisor_override AS supervisorOverride,
          supervisor_override_reason AS supervisorOverrideReason, status, submitted_at AS submittedAt,
          submitted_by AS submittedBy, decided_by_appeal_decision_id AS decidedByAppealDecisionId,
          created_at AS createdAt, created_by AS createdBy, updated_at AS updatedAt, updated_by AS updatedBy, version
      FROM appeal
      WHERE case_id = #{caseId}
      ORDER BY created_at DESC
      LIMIT 1
      """)
  AppealRecord findLatestByCaseId(UUID caseId);

  @Select(
      """
      SELECT
          id, case_id AS caseId, decision_id AS decisionId, rationale, supervisor_override AS supervisorOverride,
          supervisor_override_reason AS supervisorOverrideReason, status, submitted_at AS submittedAt,
          submitted_by AS submittedBy, decided_by_appeal_decision_id AS decidedByAppealDecisionId,
          created_at AS createdAt, created_by AS createdBy, updated_at AS updatedAt, updated_by AS updatedBy, version
      FROM appeal
      WHERE decision_id = #{decisionId}
        AND status = 'ACTIVE'
      """)
  AppealRecord findActiveByDecisionId(UUID decisionId);

  @Select(
      """
      SELECT
          id, case_id AS caseId, decision_id AS decisionId, rationale, supervisor_override AS supervisorOverride,
          supervisor_override_reason AS supervisorOverrideReason, status, submitted_at AS submittedAt,
          submitted_by AS submittedBy, decided_by_appeal_decision_id AS decidedByAppealDecisionId,
          created_at AS createdAt, created_by AS createdBy, updated_at AS updatedAt, updated_by AS updatedBy, version
      FROM appeal
      WHERE case_id = #{caseId}
        AND status = 'ACTIVE'
      """)
  AppealRecord findActiveByCaseId(UUID caseId);

  @Update(
      """
      UPDATE appeal
      SET status = #{appeal.status},
          decided_by_appeal_decision_id = #{appeal.decidedByAppealDecisionId},
          updated_at = #{appeal.updatedAt},
          updated_by = #{appeal.updatedBy},
          version = #{appeal.version}
      WHERE id = #{appeal.id}
        AND version = #{expectedVersion}
      """)
  int decide(@Param("appeal") AppealRecord appeal, @Param("expectedVersion") long expectedVersion);

  @Insert(
      """
      INSERT INTO appeal_decision (
          id, appeal_id, outcome, summary, decided_at, decided_by, created_at, created_by, version
      ) VALUES (
          #{id}, #{appealId}, #{outcome}, #{summary}, #{decidedAt}, #{decidedBy}, #{createdAt}, #{createdBy}, #{version}
      )
      """)
  int insertAppealDecision(AppealDecisionRecord appealDecisionRecord);

  @Select(
      """
      SELECT
          id, appeal_id AS appealId, outcome, summary, decided_at AS decidedAt, decided_by AS decidedBy,
          created_at AS createdAt, created_by AS createdBy, version
      FROM appeal_decision
      WHERE appeal_id = #{appealId}
      """)
  AppealDecisionRecord findDecisionByAppealId(UUID appealId);

  @Select("SELECT COUNT(*) FROM appeal WHERE case_id = #{caseId} AND status = 'ACTIVE'")
  boolean existsActiveByCaseId(UUID caseId);
}
