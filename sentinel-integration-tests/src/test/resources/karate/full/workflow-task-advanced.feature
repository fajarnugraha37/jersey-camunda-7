@full
@workflow-advanced
Feature: Advanced workflow task behaviors are safe and observable against the running application

  Background:
    * def intakeAuth = callonce read('classpath:karate/common/auth.feature') { username: 'intake-jkt' }
    * def triageAuth = callonce read('classpath:karate/common/auth.feature') { username: 'triage-jkt' }
    * def investigatorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'investigator-jkt' }
    * def reviewerAuth = callonce read('classpath:karate/common/auth.feature') { username: 'reviewer-jkt' }
    * def decisionAuth = callonce read('classpath:karate/common/auth.feature') { username: 'decision-jkt' }
    * def appealAuth = callonce read('classpath:karate/common/auth.feature') { username: 'appeal-jkt' }
    * def supervisorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'supervisor-jkt' }
    * def DbSupport = Java.type('com.sentinel.enforcement.integration.karate.support.LiveDbSupport')

  Scenario: denied appeal moves the main workflow into enforcement monitoring tasks
    * def suffix = karate.uuid()
    * def bootstrap = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      suffix: '#(suffix)',
      reportTitle: 'Workflow appeal enforcement report ' + suffix,
      caseTitle: 'Workflow appeal enforcement case ' + suffix,
      caseSummary: 'Exercise sanction publication and appeal resolution.'
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
      reason: 'Assign investigator after triage.'
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
      title: 'Workflow sanction recommendation ' + suffix,
      summary: 'Recommendation prepared for sanction publication flow.',
      proposedDecision: 'Proceed to sanction.',
      proposedSanction: 'Apply financial and reporting sanctions.'
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
    And request { reviewSummary: 'Workflow sanction review approved.' }
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
      title: 'Workflow sanction decision ' + suffix,
      summary: 'Decision published into sanction branch.',
      violationProven: true,
      sanctionSummary: 'Administrative fine and quarterly reporting.',
      obligationTitle: 'Pay administrative fine',
      obligationDetails: 'Transfer the assessed fine within the prescribed window.',
      obligationDueDate: '2026-08-20',
      appealDeadline: '2026-08-15'
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

    Given url baseUrl
    And path 'api', 'v1', 'decisions', decision.id, 'appeals'
    And header Authorization = 'Bearer ' + appealAuth.accessToken
    And request
    """
    {
      rationale: 'The sanction calculation is disputed.',
      submittedAt: '2026-07-20T10:00:00Z',
      supervisorOverride: false
    }
    """
    When method post
    Then status 201
    * def appeal = response

    Given url baseUrl
    And path 'api', 'v1', 'appeals', appeal.id, 'decisions'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And request { outcome: 'DENIED', summary: 'Denied' }
    When method post
    Then status 200

    * def appealReviewTaskLookup = call read('classpath:karate/common/list-single-task.feature') { accessToken: '#(appealAuth.accessToken)', caseId: '#(caseId)' }
    * match appealReviewTaskLookup.task.taskDefinitionKey == 'appealReviewTask'

    Given url baseUrl
    And path 'api', 'v1', 'tasks', appealReviewTaskLookup.task.taskId, 'claim'
    And header Authorization = 'Bearer ' + appealAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'tasks', appealReviewTaskLookup.task.taskId, 'complete'
    And header Authorization = 'Bearer ' + appealAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 204

    Given url baseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = 'Bearer ' + reviewerAuth.accessToken
    And param caseId = caseId
    And param limit = 10
    When method get
    Then status 200
    And match response.items == '#[3]'
    And match response.items[*].taskDefinitionKey contains 'monitorPaymentObligationTask'
    And match response.items[*].taskDefinitionKey contains 'monitorCorrectiveActionTask'
    And match response.items[*].taskDefinitionKey contains 'monitorReportingObligationTask'

    * match DbSupport.workflowStatus(caseId) == 'ACTIVE'

  Scenario: duplicate task completion is safe and does not advance the case twice
    * def bootstrap = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: 'Duplicate completion case',
      caseSummary: 'Ensure duplicate task completion is idempotent.'
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
    And match response.status == 'UNDER_INVESTIGATION'
    And match response.version == 2

  Scenario: task list supports quick search, field search, sort, and cursor pagination
    * def token = 'wf-list-' + karate.uuid().substring(0, 8)
    * def alpha = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: '#(token + " Alpha")',
      caseSummary: 'Workflow list.'
    }
    """
    * def bravo = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: '#(token + " Bravo")',
      caseSummary: 'Workflow list.'
    }
    """
    * def zulu = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: 'Zulu unrelated',
      caseSummary: '#(token + " summary")'
    }
    """

    Given url baseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And param q = token
    And param sortBy = 'CASE_NUMBER'
    And param sortDirection = 'ASC'
    And param limit = 10
    When method get
    Then status 200
    And match response.items == '#[3]'
    And match response.items[0].caseId == alpha.createdCase.id
    And match response.items[1].caseId == bravo.createdCase.id
    And match response.items[2].caseId == zulu.createdCase.id

    Given url baseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And param searchField = 'CASE_TITLE'
    And param searchValue = token
    And param sortBy = 'CASE_NUMBER'
    And param sortDirection = 'ASC'
    And param limit = 10
    When method get
    Then status 200
    And match response.items == '#[2]'
    And match response.items[0].caseId == alpha.createdCase.id
    And match response.items[1].caseId == bravo.createdCase.id

    Given url baseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
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
    And path 'api', 'v1', 'tasks'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And param q = token
    And param sortBy = 'CASE_NUMBER'
    And param sortDirection = 'ASC'
    And param limit = 2
    And param cursor = firstPage.nextCursor
    When method get
    Then status 200
    And match response.items == '#[1]'
    And match response.items[0].caseId == zulu.createdCase.id
