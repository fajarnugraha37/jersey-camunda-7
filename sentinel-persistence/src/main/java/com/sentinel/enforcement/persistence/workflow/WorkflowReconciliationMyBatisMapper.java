package com.sentinel.enforcement.persistence.workflow;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkflowReconciliationMyBatisMapper {

  @Select(
      """
      <script>
      WITH correlation AS (
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
            w.status AS workflowCorrelationStatus
        FROM case_record c
        LEFT JOIN workflow_instance w ON w.case_id = c.id AND w.workflow_type = 'CASE_MAIN'
      ),
      runtime_root AS (
        SELECT
            CAST(execution.business_key_ AS UUID) AS caseId,
            execution.proc_inst_id_ AS runtimeProcessInstanceId
        FROM act_ru_execution execution
        WHERE execution.business_key_ IS NOT NULL
          AND execution.business_key_ ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
          AND execution.parent_id_ IS NULL
          AND execution.proc_inst_id_ = execution.id_
      ),
      issue_projection AS (
        SELECT
            correlation.caseId,
            correlation.caseNumber,
            correlation.caseTitle,
            correlation.caseStatus,
            correlation.jurisdictionCode,
            correlation.assigneeUserId,
            correlation.caseUpdatedAt,
            correlation.workflowCorrelationStatus,
            correlation.correlationProcessInstanceId,
            runtime_root.runtimeProcessInstanceId,
            CASE
              WHEN runtime_root.runtimeProcessInstanceId IS NOT NULL
                   AND correlation.caseStatus IN ('DECIDED', 'CLOSED', 'CANCELLED')
                THEN 'TERMINAL_CASE_RUNTIME_ACTIVE'
              WHEN runtime_root.runtimeProcessInstanceId IS NOT NULL
                   AND correlation.correlationProcessInstanceId IS NULL
                THEN 'ACTIVE_RUNTIME_MISSING_CORRELATION'
              WHEN runtime_root.runtimeProcessInstanceId IS NOT NULL
                   AND (
                     correlation.workflowCorrelationStatus IS DISTINCT FROM 'ACTIVE'
                     OR correlation.correlationProcessInstanceId IS DISTINCT FROM runtime_root.runtimeProcessInstanceId
                   )
                THEN 'ACTIVE_RUNTIME_CORRELATION_MISMATCH'
              WHEN runtime_root.runtimeProcessInstanceId IS NULL
                   AND correlation.caseStatus NOT IN ('DECIDED', 'CLOSED', 'CANCELLED')
                   AND correlation.correlationProcessInstanceId IS NULL
                THEN 'ACTIVE_CASE_WORKFLOW_NOT_RUNNING'
              WHEN runtime_root.runtimeProcessInstanceId IS NULL
                   AND correlation.caseStatus NOT IN ('DECIDED', 'CLOSED', 'CANCELLED')
                   AND correlation.workflowCorrelationStatus IS DISTINCT FROM 'ACTIVE'
                THEN 'ACTIVE_CASE_CORRELATION_TERMINAL'
              WHEN runtime_root.runtimeProcessInstanceId IS NULL
                   AND correlation.caseStatus NOT IN ('DECIDED', 'CLOSED', 'CANCELLED')
                   AND correlation.workflowCorrelationStatus = 'ACTIVE'
                THEN 'ACTIVE_CASE_WORKFLOW_NOT_RUNNING'
              WHEN runtime_root.runtimeProcessInstanceId IS NULL
                   AND correlation.caseStatus IN ('DECIDED', 'CLOSED', 'CANCELLED')
                   AND correlation.correlationProcessInstanceId IS NULL
                THEN 'TERMINAL_CASE_MISSING_CORRELATION'
              WHEN runtime_root.runtimeProcessInstanceId IS NULL
                   AND correlation.caseStatus IN ('DECIDED', 'CLOSED', 'CANCELLED')
                   AND correlation.workflowCorrelationStatus = 'ACTIVE'
                THEN 'TERMINAL_CASE_CORRELATION_ACTIVE'
              ELSE NULL
            END AS issueType,
            CASE
              WHEN runtime_root.runtimeProcessInstanceId IS NOT NULL
                   AND correlation.caseStatus IN ('DECIDED', 'CLOSED', 'CANCELLED')
                THEN 'Case reached a post-decision state but still has an active workflow runtime instance.'
              WHEN runtime_root.runtimeProcessInstanceId IS NOT NULL
                   AND correlation.correlationProcessInstanceId IS NULL
                THEN 'Active workflow runtime exists but workflow correlation row is missing.'
              WHEN runtime_root.runtimeProcessInstanceId IS NOT NULL
                   AND (
                     correlation.workflowCorrelationStatus IS DISTINCT FROM 'ACTIVE'
                     OR correlation.correlationProcessInstanceId IS DISTINCT FROM runtime_root.runtimeProcessInstanceId
                   )
                THEN 'Active workflow runtime and stored workflow correlation disagree on status or process instance.'
              WHEN runtime_root.runtimeProcessInstanceId IS NULL
                   AND correlation.caseStatus NOT IN ('DECIDED', 'CLOSED', 'CANCELLED')
                   AND correlation.correlationProcessInstanceId IS NULL
                THEN 'Case is still in an active workflow state but no runtime instance or workflow correlation is present.'
              WHEN runtime_root.runtimeProcessInstanceId IS NULL
                   AND correlation.caseStatus NOT IN ('DECIDED', 'CLOSED', 'CANCELLED')
                   AND correlation.workflowCorrelationStatus IS DISTINCT FROM 'ACTIVE'
                THEN 'Case is still in an active workflow state but the stored workflow correlation is terminal.'
              WHEN runtime_root.runtimeProcessInstanceId IS NULL
                   AND correlation.caseStatus NOT IN ('DECIDED', 'CLOSED', 'CANCELLED')
                   AND correlation.workflowCorrelationStatus = 'ACTIVE'
                THEN 'Case is still in an active workflow state but its active runtime instance is missing.'
              WHEN runtime_root.runtimeProcessInstanceId IS NULL
                   AND correlation.caseStatus IN ('DECIDED', 'CLOSED', 'CANCELLED')
                   AND correlation.correlationProcessInstanceId IS NULL
                THEN 'Case reached a post-decision state but the workflow correlation row is missing.'
              WHEN runtime_root.runtimeProcessInstanceId IS NULL
                   AND correlation.caseStatus IN ('DECIDED', 'CLOSED', 'CANCELLED')
                   AND correlation.workflowCorrelationStatus = 'ACTIVE'
                THEN 'Case reached a post-decision state but the workflow correlation still reports ACTIVE.'
              ELSE NULL
            END AS detail,
            CASE
              WHEN runtime_root.runtimeProcessInstanceId IS NOT NULL
                   AND correlation.caseStatus IN ('DECIDED', 'CLOSED', 'CANCELLED')
                THEN 'TERMINATE_RUNTIME'
              WHEN runtime_root.runtimeProcessInstanceId IS NOT NULL
                   AND correlation.correlationProcessInstanceId IS NULL
                THEN 'AUTO_REPAIR'
              WHEN runtime_root.runtimeProcessInstanceId IS NOT NULL
                   AND (
                     correlation.workflowCorrelationStatus IS DISTINCT FROM 'ACTIVE'
                     OR correlation.correlationProcessInstanceId IS DISTINCT FROM runtime_root.runtimeProcessInstanceId
                   )
                THEN 'AUTO_REPAIR'
              WHEN runtime_root.runtimeProcessInstanceId IS NULL
                   AND correlation.caseStatus IN ('DECIDED', 'CLOSED', 'CANCELLED')
                   AND correlation.correlationProcessInstanceId IS NULL
                THEN 'AUTO_REPAIR'
              WHEN runtime_root.runtimeProcessInstanceId IS NULL
                   AND correlation.caseStatus IN ('DECIDED', 'CLOSED', 'CANCELLED')
                   AND correlation.workflowCorrelationStatus = 'ACTIVE'
                THEN 'AUTO_REPAIR'
              ELSE ''
            END AS availableActionsCsv
        FROM correlation
        LEFT JOIN runtime_root ON runtime_root.caseId = correlation.caseId
      ),
      filtered AS (
        SELECT
            caseId,
            caseNumber,
            caseTitle,
            caseStatus,
            jurisdictionCode,
            assigneeUserId,
            caseUpdatedAt,
            issueType,
            detail,
            workflowCorrelationStatus,
            correlationProcessInstanceId,
            runtimeProcessInstanceId,
            availableActionsCsv,
            LOWER(caseNumber) AS sortCaseNumber,
            LOWER(caseStatus) AS sortCaseStatus,
            LOWER(issueType) AS sortIssueType,
            LOWER(COALESCE(workflowCorrelationStatus, '')) AS sortCorrelationStatus
        FROM issue_projection
        WHERE issueType IS NOT NULL
          <if test="caseStatus != null">
            AND caseStatus = #{caseStatus}
          </if>
          <if test="workflowCorrelationStatus != null">
            AND LOWER(COALESCE(workflowCorrelationStatus, '')) = #{workflowCorrelationStatus}
          </if>
          <if test="issueType != null">
            AND issueType = #{issueType}
          </if>
          <if test="quickSearchPattern != null">
            AND (
              LOWER(caseNumber) LIKE #{quickSearchPattern}
              OR LOWER(caseTitle) LIKE #{quickSearchPattern}
              OR LOWER(jurisdictionCode) LIKE #{quickSearchPattern}
              OR LOWER(issueType) LIKE #{quickSearchPattern}
              OR LOWER(COALESCE(correlationProcessInstanceId, '')) LIKE #{quickSearchPattern}
              OR LOWER(COALESCE(runtimeProcessInstanceId, '')) LIKE #{quickSearchPattern}
            )
          </if>
          <if test="searchPattern != null">
            AND
            <choose>
              <when test='searchField == "CASE_NUMBER"'>LOWER(caseNumber) LIKE #{searchPattern}</when>
              <when test='searchField == "CASE_TITLE"'>LOWER(caseTitle) LIKE #{searchPattern}</when>
              <when test='searchField == "ISSUE_TYPE"'>LOWER(issueType) LIKE #{searchPattern}</when>
              <when test='searchField == "PROCESS_INSTANCE_ID"'>
                (
                  LOWER(COALESCE(correlationProcessInstanceId, '')) LIKE #{searchPattern}
                  OR LOWER(COALESCE(runtimeProcessInstanceId, '')) LIKE #{searchPattern}
                )
              </when>
              <when test='searchField == "JURISDICTION_CODE"'>LOWER(jurisdictionCode) LIKE #{searchPattern}</when>
              <otherwise>1 = 0</otherwise>
            </choose>
          </if>
      )
      SELECT
          caseId,
          caseNumber,
          caseTitle,
          caseStatus,
          jurisdictionCode,
          assigneeUserId,
          caseUpdatedAt,
          issueType,
          detail,
          workflowCorrelationStatus,
          correlationProcessInstanceId,
          runtimeProcessInstanceId,
          availableActionsCsv
      FROM filtered
      <where>
        <if test="cursorCaseId != null">
          <choose>
            <when test='sortBy == "CASE_UPDATED_AT" and sortDirection == "DESC"'>(caseUpdatedAt &lt; #{cursorTimestampValue} OR (caseUpdatedAt = #{cursorTimestampValue} AND caseId::text &lt; #{cursorCaseId}))</when>
            <when test='sortBy == "CASE_UPDATED_AT" and sortDirection == "ASC"'>(caseUpdatedAt &gt; #{cursorTimestampValue} OR (caseUpdatedAt = #{cursorTimestampValue} AND caseId::text &gt; #{cursorCaseId}))</when>
            <when test='sortBy == "CASE_NUMBER" and sortDirection == "DESC"'>(sortCaseNumber &lt; #{cursorTextValue} OR (sortCaseNumber = #{cursorTextValue} AND caseId::text &lt; #{cursorCaseId}))</when>
            <when test='sortBy == "CASE_NUMBER" and sortDirection == "ASC"'>(sortCaseNumber &gt; #{cursorTextValue} OR (sortCaseNumber = #{cursorTextValue} AND caseId::text &gt; #{cursorCaseId}))</when>
            <when test='sortBy == "CASE_STATUS" and sortDirection == "DESC"'>(sortCaseStatus &lt; #{cursorTextValue} OR (sortCaseStatus = #{cursorTextValue} AND caseId::text &lt; #{cursorCaseId}))</when>
            <when test='sortBy == "CASE_STATUS" and sortDirection == "ASC"'>(sortCaseStatus &gt; #{cursorTextValue} OR (sortCaseStatus = #{cursorTextValue} AND caseId::text &gt; #{cursorCaseId}))</when>
            <when test='sortBy == "ISSUE_TYPE" and sortDirection == "DESC"'>(sortIssueType &lt; #{cursorTextValue} OR (sortIssueType = #{cursorTextValue} AND caseId::text &lt; #{cursorCaseId}))</when>
            <when test='sortBy == "ISSUE_TYPE" and sortDirection == "ASC"'>(sortIssueType &gt; #{cursorTextValue} OR (sortIssueType = #{cursorTextValue} AND caseId::text &gt; #{cursorCaseId}))</when>
            <when test='sortBy == "CORRELATION_STATUS" and sortDirection == "DESC"'>(sortCorrelationStatus &lt; #{cursorTextValue} OR (sortCorrelationStatus = #{cursorTextValue} AND caseId::text &lt; #{cursorCaseId}))</when>
            <when test='sortBy == "CORRELATION_STATUS" and sortDirection == "ASC"'>(sortCorrelationStatus &gt; #{cursorTextValue} OR (sortCorrelationStatus = #{cursorTextValue} AND caseId::text &gt; #{cursorCaseId}))</when>
            <otherwise>1 = 0</otherwise>
          </choose>
        </if>
      </where>
      ORDER BY
      <choose>
        <when test='sortBy == "CASE_UPDATED_AT"'>caseUpdatedAt</when>
        <when test='sortBy == "CASE_NUMBER"'>sortCaseNumber</when>
        <when test='sortBy == "CASE_STATUS"'>sortCaseStatus</when>
        <when test='sortBy == "ISSUE_TYPE"'>sortIssueType</when>
        <when test='sortBy == "CORRELATION_STATUS"'>sortCorrelationStatus</when>
        <otherwise>caseUpdatedAt</otherwise>
      </choose>
      <choose>
        <when test='sortDirection == "ASC"'>ASC</when>
        <otherwise>DESC</otherwise>
      </choose>,
      caseId::text
      <choose>
        <when test='sortDirection == "ASC"'>ASC</when>
        <otherwise>DESC</otherwise>
      </choose>
      LIMIT #{limitPlusOne}
      </script>
      """)
  List<WorkflowReconciliationIssueData> findIssuePage(
      WorkflowReconciliationIssueQueryData queryData);

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
