@regression
@workflow
Feature: Workflow task APIs can drive a case from creation to a decided state

  Background:
    * def intakeAuth = callonce read('classpath:karate/common/auth.feature') { username: 'intake-jkt' }
    * def triageAuth = callonce read('classpath:karate/common/auth.feature') { username: 'triage-jkt' }
    * def investigatorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'investigator-jkt' }
    * def reviewerAuth = callonce read('classpath:karate/common/auth.feature') { username: 'reviewer-jkt' }
    * def decisionAuth = callonce read('classpath:karate/common/auth.feature') { username: 'decision-jkt' }
    * def supervisorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'supervisor-jkt' }

  Scenario: main workflow progresses from triage task to decision completion
    * def suffix = karate.uuid()
    * def bootstrap = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      suffix: '#(suffix)',
      reportTitle: 'Karate workflow report ' + suffix,
      caseTitle: 'Karate workflow case ' + suffix,
      caseSummary: 'Workflow lifecycle regression driven by Karate.'
    }
    """
    * def caseId = bootstrap.createdCase.id

    * def triageTaskLookup = call read('classpath:karate/common/list-single-task.feature') { accessToken: '#(triageAuth.accessToken)', caseId: '#(caseId)' }
    * match triageTaskLookup.task.taskDefinitionKey == 'triageTask'

    Given url baseUrl
    And path 'api', 'v1', 'tasks', triageTaskLookup.task.taskId, 'claim'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200
    And match response.assigneeUserId == 'triage-jkt'

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
    * def underInvestigationCase = response

    * def assignment = call read('classpath:karate/common/assign-case.feature')
    """
    {
      accessToken: '#(triageAuth.accessToken)',
      caseId: '#(caseId)',
      expectedVersion: '#(underInvestigationCase.version)',
      assignedUnitId: 'JKT-UNIT-1',
      assigneeUserId: 'investigator-jkt',
      reason: 'Assign investigator after Karate triage completion.'
    }
    """

    * def investigationTaskLookup = call read('classpath:karate/common/list-single-task.feature') { accessToken: '#(investigatorAuth.accessToken)', caseId: '#(caseId)' }
    * match investigationTaskLookup.task.taskDefinitionKey == 'investigationTask'

    Given url baseUrl
    And path 'api', 'v1', 'tasks', investigationTaskLookup.task.taskId, 'claim'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200
    And match response.assigneeUserId == 'investigator-jkt'

    * def recommendationPayload =
    """
    {
      title: 'Karate workflow recommendation ' + suffix,
      summary: 'Recommendation prepared during Karate workflow regression.',
      proposedDecision: 'Proceed to formal decision.',
      proposedSanction: null
    }
    """
    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'recommendations'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And request recommendationPayload
    When method post
    Then status 201
    And match response.id == '#uuid'
    * def recommendationId = response.id

    Given url baseUrl
    And path 'api', 'v1', 'recommendations', recommendationId, 'submit'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200
    And match response.id == recommendationId

    Given url baseUrl
    And path 'api', 'v1', 'tasks', investigationTaskLookup.task.taskId, 'complete'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 204

    * def reviewTaskLookup = call read('classpath:karate/common/list-single-task.feature') { accessToken: '#(reviewerAuth.accessToken)', caseId: '#(caseId)' }
    * match reviewTaskLookup.task.taskDefinitionKey == 'reviewTask'

    Given url baseUrl
    And path 'api', 'v1', 'tasks', reviewTaskLookup.task.taskId, 'claim'
    And header Authorization = 'Bearer ' + reviewerAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200
    And match response.assigneeUserId == 'reviewer-jkt'

    Given url baseUrl
    And path 'api', 'v1', 'recommendations', recommendationId, 'reviews'
    And header Authorization = 'Bearer ' + reviewerAuth.accessToken
    And request { reviewSummary: 'Karate workflow review approved.' }
    When method post
    Then status 200
    And match response.id == recommendationId

    Given url baseUrl
    And path 'api', 'v1', 'tasks', reviewTaskLookup.task.taskId, 'complete'
    And header Authorization = 'Bearer ' + reviewerAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 204

    * def decisionTaskLookup = call read('classpath:karate/common/list-single-task.feature') { accessToken: '#(decisionAuth.accessToken)', caseId: '#(caseId)' }
    * match decisionTaskLookup.task.taskDefinitionKey == 'decisionTask'

    Given url baseUrl
    And path 'api', 'v1', 'tasks', decisionTaskLookup.task.taskId, 'claim'
    And header Authorization = 'Bearer ' + decisionAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200
    And match response.assigneeUserId == 'decision-jkt'

    * def decisionPayload =
    """
    {
      title: 'Karate workflow decision ' + suffix,
      summary: 'Decision published from Karate workflow regression.',
      violationProven: false,
      sanctionSummary: null,
      obligationTitle: null,
      obligationDetails: null,
      obligationDueDate: null,
      appealDeadline: '2026-08-01'
    }
    """
    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'decisions'
    And header Authorization = 'Bearer ' + decisionAuth.accessToken
    And request decisionPayload
    When method post
    Then status 201
    And match response.id == '#uuid'
    * def decisionId = response.id

    Given url baseUrl
    And path 'api', 'v1', 'decisions', decisionId, 'approve'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200
    And match response.id == decisionId

    Given url baseUrl
    And path 'api', 'v1', 'decisions', decisionId, 'publish'
    And header Authorization = 'Bearer ' + decisionAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200
    And match response.id == decisionId

    Given url baseUrl
    And path 'api', 'v1', 'tasks', decisionTaskLookup.task.taskId, 'complete'
    And header Authorization = 'Bearer ' + decisionAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 204

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId
    And header Authorization = 'Bearer ' + decisionAuth.accessToken
    When method get
    Then status 200
    And match response.status == 'DECIDED'
    And match response.version == 6

    Given url baseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = 'Bearer ' + decisionAuth.accessToken
    And param caseId = caseId
    And param limit = 10
    When method get
    Then status 200
    And match response.items == '#[0]'
