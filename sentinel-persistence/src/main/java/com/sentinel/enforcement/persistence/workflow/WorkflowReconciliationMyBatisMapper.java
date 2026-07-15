package com.sentinel.enforcement.persistence.workflow;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkflowReconciliationMyBatisMapper {

  @Select(
      """
      SELECT
          c.id AS caseId,
          c.case_number AS caseNumber,
          c.title AS caseTitle,
          c.status AS caseStatus,
          c.jurisdiction_code AS jurisdictionCode,
          c.assignee_user_id AS assigneeUserId,
          c.updated_at AS caseUpdatedAt,
          w.process_instance_id AS correlationProcessInstanceId,
          w.process_definition_id AS correlationProcessDefinitionId,
          w.process_definition_version AS correlationProcessDefinitionVersion,
          w.business_key AS correlationBusinessKey,
          w.status AS correlationStatus
      FROM case_record c
      LEFT JOIN workflow_instance w ON w.case_id = c.id AND w.workflow_type = 'CASE_MAIN'
      """)
  List<WorkflowReconciliationCandidateData> findCandidates();

  @Select(
      """
      SELECT
          c.id AS caseId,
          c.case_number AS caseNumber,
          c.title AS caseTitle,
          c.status AS caseStatus,
          c.jurisdiction_code AS jurisdictionCode,
          c.assignee_user_id AS assigneeUserId,
          c.updated_at AS caseUpdatedAt,
          w.process_instance_id AS correlationProcessInstanceId,
          w.process_definition_id AS correlationProcessDefinitionId,
          w.process_definition_version AS correlationProcessDefinitionVersion,
          w.business_key AS correlationBusinessKey,
          w.status AS correlationStatus
      FROM case_record c
      LEFT JOIN workflow_instance w ON w.case_id = c.id AND w.workflow_type = 'CASE_MAIN'
      WHERE c.id = #{caseId}
      """)
  WorkflowReconciliationCandidateData findCandidateByCaseId(UUID caseId);
}
