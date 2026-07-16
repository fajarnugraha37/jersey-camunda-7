@full
@workflow-reconciliation-advanced
Feature: Workflow reconciliation full coverage works against the running application

  Background:
    * def intakeAuth = callonce read('classpath:karate/common/auth.feature') { username: 'intake-jkt' }
    * def triageAuth = callonce read('classpath:karate/common/auth.feature') { username: 'triage-jkt' }
    * def investigatorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'investigator-jkt' }
    * def reviewerAuth = callonce read('classpath:karate/common/auth.feature') { username: 'reviewer-jkt' }
    * def decisionAuth = callonce read('classpath:karate/common/auth.feature') { username: 'decision-jkt' }
    * def supervisorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'supervisor-jkt' }
    * def DbSupport = Java.type('com.sentinel.enforcement.integration.karate.support.LiveDbSupport')

  Scenario: supervisor can auto repair a terminal case from workflow history
    * def token = 'wf-reconcile-history-' + karate.uuid().substring(0, 8)
    * def bootstrap = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: '#(token + " history mismatch")',
      caseSummary: 'History mismatch.'
    }
    """
    * def caseId = bootstrap.createdCase.id

    * def triageTaskLookup = call read('classpath:karate/common/list-single-task.feature') { accessToken: '#(triageAuth.accessToken)', caseId: '#(caseId)' }
    Given url baseUrl
    And path 'api', 'v1', 'tasks', triageTaskLookup.task.taskId, 'claim'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'tasks', triageTaskLookup.task.taskId, 'complete'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 204

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    When method get
    Then status 200
    * def afterTriageCase = response

    * call read('classpath:karate/common/assign-case.feature')
    """
    {
      accessToken: '#(triageAuth.accessToken)',
      caseId: '#(caseId)',
      expectedVersion: '#(afterTriageCase.version)',
      assignedUnitId: 'JKT-UNIT-1',
      assigneeUserId: 'investigator-jkt',
      reason: 'Assign investigator.'
    }
    """

    * def investigationTaskLookup = call read('classpath:karate/common/list-single-task.feature') { accessToken: '#(investigatorAuth.accessToken)', caseId: '#(caseId)' }
    Given url baseUrl
    And path 'api', 'v1', 'tasks', investigationTaskLookup.task.taskId, 'claim'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'recommendations'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And request
    """
    {
      title: 'Workflow reconciliation recommendation',
      summary: 'Recommendation created for reconciliation flow.',
      proposedDecision: 'Proceed to decision.',
      proposedSanction: null
    }
    """
    When method post
    Then status 201
    * def recommendation = response

    Given url baseUrl
    And path 'api', 'v1', 'recommendations', recommendation.id, 'submit'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'tasks', investigationTaskLookup.task.taskId, 'complete'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 204

    * def reviewTaskLookup = call read('classpath:karate/common/list-single-task.feature') { accessToken: '#(reviewerAuth.accessToken)', caseId: '#(caseId)' }
    Given url baseUrl
    And path 'api', 'v1', 'tasks', reviewTaskLookup.task.taskId, 'claim'
    And header Authorization = 'Bearer ' + reviewerAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'recommendations', recommendation.id, 'reviews'
    And header Authorization = 'Bearer ' + reviewerAuth.accessToken
    And request { reviewSummary: 'Workflow reconciliation review approved.' }
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'tasks', reviewTaskLookup.task.taskId, 'complete'
    And header Authorization = 'Bearer ' + reviewerAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 204

    * def decisionTaskLookup = call read('classpath:karate/common/list-single-task.feature') { accessToken: '#(decisionAuth.accessToken)', caseId: '#(caseId)' }
    Given url baseUrl
    And path 'api', 'v1', 'tasks', decisionTaskLookup.task.taskId, 'claim'
    And header Authorization = 'Bearer ' + decisionAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'decisions'
    And header Authorization = 'Bearer ' + decisionAuth.accessToken
    And request
    """
    {
      title: 'Workflow reconciliation decision',
      summary: 'Decision created for reconciliation flow.',
      violationProven: false,
      sanctionSummary: null,
      obligationTitle: null,
      obligationDetails: null,
      obligationDueDate: null,
      appealDeadline: '2026-08-01'
    }
    """
    When method post
    Then status 201
    * def decision = response

    Given url baseUrl
    And path 'api', 'v1', 'decisions', decision.id, 'approve'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'decisions', decision.id, 'publish'
    And header Authorization = 'Bearer ' + decisionAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'tasks', decisionTaskLookup.task.taskId, 'complete'
    And header Authorization = 'Bearer ' + decisionAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 204

    * match DbSupport.workflowStatus(caseId) == 'COMPLETED'
    * match DbSupport.deleteWorkflowInstance(caseId) == 1

    Given url baseUrl
    And path 'api', 'v1', 'workflow-reconciliation'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And param q = token
    And param limit = 10
    When method get
    Then status 200
    And match response.items == '#[1]'
    And match response.items[0].issueType == 'TERMINAL_CASE_MISSING_CORRELATION'
    And match response.items[0].runtimeProcessInstanceId == null
    And match response.items[0].availableActions contains 'AUTO_REPAIR'

    Given url baseUrl
    And path 'api', 'v1', 'workflow-reconciliation', caseId, 'actions'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And request { action: 'AUTO_REPAIR', reason: 'Restore terminal workflow correlation from history.' }
    When method post
    Then status 200
    And match response.result == 'REPAIRED'
    And match response.workflowCorrelationStatus == 'COMPLETED'
    And match response.processInstanceId == '#string'

    * match DbSupport.workflowStatus(caseId) == 'COMPLETED'
    * match DbSupport.countAuditEventsByType(caseId, 'WorkflowReconciliationPerformed') == 1

    Given url baseUrl
    And path 'api', 'v1', 'workflow-reconciliation'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And param q = token
    And param limit = 10
    When method get
    Then status 200
    And match response.items == '#[0]'

  Scenario: list/search/cursor are available only to supervisors
    * def token = 'wf-reconcile-list-' + karate.uuid().substring(0, 8)
    * def alpha = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: '#(token + " Alpha")',
      caseSummary: 'List mismatch.'
    }
    """
    * def bravo = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: '#(token + " Bravo")',
      caseSummary: 'List mismatch.'
    }
    """
    * def charlie = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: '#(token + " Charlie")',
      caseSummary: 'List mismatch.'
    }
    """
    * match DbSupport.deleteWorkflowInstance(alpha.createdCase.id) == 1
    * match DbSupport.deleteWorkflowInstance(bravo.createdCase.id) == 1
    * match DbSupport.deleteWorkflowInstance(charlie.createdCase.id) == 1

    Given url baseUrl
    And path 'api', 'v1', 'workflow-reconciliation'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And param q = token
    And param sortBy = 'CASE_NUMBER'
    And param sortDirection = 'ASC'
    And param limit = 2
    When method get
    Then status 200
    And match response.items == '#[2]'
    And match response.nextCursor == '#string'
    * def firstPage = response

    Given url baseUrl
    And path 'api', 'v1', 'workflow-reconciliation'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And param q = token
    And param sortBy = 'CASE_NUMBER'
    And param sortDirection = 'ASC'
    And param limit = 2
    And param cursor = firstPage.nextCursor
    When method get
    Then status 200
    And match response.items == '#[1]'

    * def caseNumber = firstPage.items[0].caseNumber
    Given url baseUrl
    And path 'api', 'v1', 'workflow-reconciliation'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And param searchField = 'CASE_NUMBER'
    And param searchValue = caseNumber
    And param limit = 10
    When method get
    Then status 200
    And match response.items == '#[1]'
    And match response.items[0].caseNumber == caseNumber

    Given url baseUrl
    And path 'api', 'v1', 'workflow-reconciliation'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And param limit = 10
    When method get
    Then status 403
    And match response.code == 'FORBIDDEN'
