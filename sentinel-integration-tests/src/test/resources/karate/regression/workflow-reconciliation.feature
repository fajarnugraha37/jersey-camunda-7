@regression
@full
@workflow-reconciliation
Feature: Workflow reconciliation APIs repair live domain-workflow mismatches

  Background:
    * def intakeAuth = callonce read('classpath:karate/common/auth.feature') { username: 'intake-jkt' }
    * def triageAuth = callonce read('classpath:karate/common/auth.feature') { username: 'triage-jkt' }
    * def supervisorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'supervisor-jkt' }
    * def DbSupport = Java.type('com.sentinel.enforcement.integration.karate.support.LiveDbSupport')

  Scenario: active runtime missing correlation can be auto repaired
    * def suffix = karate.uuid()
    * def token = 'wf-karate-active-' + suffix.substring(0, 8)
    * def caseTitle = token + ' active mismatch'
    * def bootstrap = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      suffix: '#(suffix)',
      reportTitle: 'Karate workflow reconciliation report ' + suffix,
      caseTitle: '#(caseTitle)',
      caseSummary: 'Workflow reconciliation active mismatch regression.'
    }
    """
    * def caseId = bootstrap.createdCase.id
    * match DbSupport.deleteWorkflowInstance(caseId) == 1

    Given url baseUrl
    And path 'api', 'v1', 'workflow-reconciliation'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And param q = token
    And param limit = 10
    When method get
    Then status 200
    And match response.items == '#[1]'
    And match response.items[0].caseId == caseId
    And match response.items[0].issueType == 'ACTIVE_RUNTIME_MISSING_CORRELATION'
    And match response.items[0].workflowCorrelationStatus == null
    And match response.items[0].runtimeProcessInstanceId == '#string'
    And match response.items[0].availableActions contains 'AUTO_REPAIR'

    Given url baseUrl
    And path 'api', 'v1', 'workflow-reconciliation', caseId, 'actions'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And request
    """
    {
      action: 'AUTO_REPAIR',
      reason: 'Restore missing workflow correlation row.'
    }
    """
    When method post
    Then status 200
    And match response.caseId == caseId
    And match response.action == 'AUTO_REPAIR'
    And match response.result == 'REPAIRED'
    And match response.issueType == 'ACTIVE_RUNTIME_MISSING_CORRELATION'
    And match response.workflowCorrelationStatus == 'ACTIVE'
    And match response.processInstanceId == '#string'

    * match DbSupport.workflowStatus(caseId) == 'ACTIVE'
    * match DbSupport.countAuditEventsByType(caseId, 'WorkflowReconciliationPerformed') == 1

    Given url baseUrl
    And path 'api', 'v1', 'workflow-reconciliation'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And param q = token
    And param limit = 10
    When method get
    Then status 200
    And match response.items == '#[0]'

  Scenario: terminal case with active runtime can be terminated through reconciliation
    * def suffix = karate.uuid()
    * def token = 'wf-karate-terminate-' + suffix.substring(0, 8)
    * def caseTitle = token + ' terminate mismatch'
    * def bootstrap = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      suffix: '#(suffix)',
      reportTitle: 'Karate workflow terminate report ' + suffix,
      caseTitle: '#(caseTitle)',
      caseSummary: 'Workflow reconciliation terminate mismatch regression.'
    }
    """
    * def caseId = bootstrap.createdCase.id

    * def triageTaskLookup = call read('classpath:karate/common/list-single-task.feature') { accessToken: '#(triageAuth.accessToken)', caseId: '#(caseId)' }
    * match triageTaskLookup.task.taskDefinitionKey == 'triageTask'
    * match DbSupport.forceCaseStatus(caseId, 'DECIDED') == 1

    Given url baseUrl
    And path 'api', 'v1', 'workflow-reconciliation'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And param q = token
    And param limit = 10
    When method get
    Then status 200
    And match response.items == '#[1]'
    And match response.items[0].caseId == caseId
    And match response.items[0].issueType == 'TERMINAL_CASE_RUNTIME_ACTIVE'
    And match response.items[0].workflowCorrelationStatus == 'ACTIVE'
    And match response.items[0].availableActions contains 'TERMINATE_RUNTIME'

    Given url baseUrl
    And path 'api', 'v1', 'workflow-reconciliation', caseId, 'actions'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And request
    """
    {
      action: 'TERMINATE_RUNTIME',
      reason: 'Terminate runtime for terminal case mismatch.'
    }
    """
    When method post
    Then status 200
    And match response.caseId == caseId
    And match response.action == 'TERMINATE_RUNTIME'
    And match response.result == 'REPAIRED'
    And match response.issueType == 'TERMINAL_CASE_RUNTIME_ACTIVE'
    And match response.workflowCorrelationStatus == 'COMPLETED'

    * match DbSupport.workflowStatus(caseId) == 'COMPLETED'
    * match DbSupport.countAuditEventsByType(caseId, 'WorkflowReconciliationPerformed') == 1

    Given url baseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And param caseId = caseId
    And param limit = 10
    When method get
    Then status 200
    And match response.items == '#[0]'
