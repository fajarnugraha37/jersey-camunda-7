@regression
@appeal
Feature: Appeal APIs work against the running application

  Background:
    * def intakeAuth = callonce read('classpath:karate/common/auth.feature') { username: 'intake-jkt' }
    * def triageAuth = callonce read('classpath:karate/common/auth.feature') { username: 'triage-jkt' }
    * def investigatorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'investigator-jkt' }
    * def reviewerAuth = callonce read('classpath:karate/common/auth.feature') { username: 'reviewer-jkt' }
    * def decisionAuth = callonce read('classpath:karate/common/auth.feature') { username: 'decision-jkt' }
    * def supervisorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'supervisor-jkt' }
    * def appealAuth = callonce read('classpath:karate/common/auth.feature') { username: 'appeal-jkt' }
    * def DbSupport = Java.type('com.sentinel.enforcement.integration.karate.support.LiveDbSupport')

  Scenario: granted appeal closes the case and cancels sanction artifacts
    * def suffix = karate.uuid()
    * def decisionFlow = call read('classpath:karate/common/create-published-decision.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      investigatorToken: '#(investigatorAuth.accessToken)',
      reviewerToken: '#(reviewerAuth.accessToken)',
      decisionToken: '#(decisionAuth.accessToken)',
      supervisorToken: '#(supervisorAuth.accessToken)',
      suffix: '#(suffix)',
      reportTitle: 'Karate appeal report ' + suffix,
      caseTitle: 'Karate appeal case ' + suffix,
      caseSummary: 'Appeal lifecycle regression driven by Karate.',
      violationProven: true,
      obligationDueDate: '2026-08-29',
      appealDeadline: '2026-08-15'
    }
    """

    Given url baseUrl
    And path 'api', 'v1', 'decisions', decisionFlow.decision.id, 'appeals'
    And header Authorization = 'Bearer ' + appealAuth.accessToken
    And request
    """
    {
      rationale: 'Decision overlooked exculpatory evidence.',
      submittedAt: '2026-07-20T10:00:00Z',
      supervisorOverride: false
    }
    """
    When method post
    Then status 201
    And match response.id == '#uuid'
    And match response.caseId == decisionFlow.decidedCase.id
    And match response.decisionId == decisionFlow.decision.id
    And match response.status == 'ACTIVE'
    * def appeal = response

    Given url baseUrl
    And path 'api', 'v1', 'cases', decisionFlow.decidedCase.id
    And header Authorization = 'Bearer ' + appealAuth.accessToken
    When method get
    Then status 200
    And match response.status == 'UNDER_APPEAL'

    Given url baseUrl
    And path 'api', 'v1', 'appeals', appeal.id, 'decisions'
    And header Authorization = 'Bearer ' + appealAuth.accessToken
    And request
    """
    {
      outcome: 'GRANTED',
      summary: 'Appeal granted after reviewing missing context.'
    }
    """
    When method post
    Then status 200
    And match response.id == appeal.id
    And match response.status == 'DECIDED'

    * def appealReviewTaskLookup = call read('classpath:karate/common/list-single-task.feature') { accessToken: '#(appealAuth.accessToken)', caseId: '#(decisionFlow.decidedCase.id)' }
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
    And path 'api', 'v1', 'cases', decisionFlow.decidedCase.id
    And header Authorization = 'Bearer ' + appealAuth.accessToken
    When method get
    Then status 200
    And match response.status == 'CLOSED'

    * match DbSupport.sanctionStatusByDecisionId(decisionFlow.decision.id) == 'CANCELLED'
    * match DbSupport.sanctionObligationStatusByDecisionId(decisionFlow.decision.id) == 'CANCELLED'
    * match DbSupport.appealStatus(appeal.id) == 'DECIDED'

  Scenario: late appeal requires supervisor override
    * def suffix = karate.uuid()
    * def decisionFlow = call read('classpath:karate/common/create-published-decision.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      investigatorToken: '#(investigatorAuth.accessToken)',
      reviewerToken: '#(reviewerAuth.accessToken)',
      decisionToken: '#(decisionAuth.accessToken)',
      supervisorToken: '#(supervisorAuth.accessToken)',
      suffix: '#(suffix)',
      reportTitle: 'Karate late-appeal report ' + suffix,
      caseTitle: 'Karate late-appeal case ' + suffix,
      caseSummary: 'Late appeal override regression driven by Karate.',
      appealDeadline: '2026-07-01'
    }
    """

    Given url baseUrl
    And path 'api', 'v1', 'decisions', decisionFlow.decision.id, 'appeals'
    And header Authorization = 'Bearer ' + appealAuth.accessToken
    And request
    """
    {
      rationale: 'Late filing without override.',
      submittedAt: '2026-07-05T09:00:00Z'
    }
    """
    When method post
    Then status 409
    And match response.code == 'APPEAL_LATE_OVERRIDE_REQUIRED'

    Given url baseUrl
    And path 'api', 'v1', 'decisions', decisionFlow.decision.id, 'appeals'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And request
    """
    {
      rationale: 'Supervisor accepted the late filing.',
      submittedAt: '2026-07-05T09:00:00Z',
      supervisorOverride: true,
      supervisorOverrideReason: 'Exceptional circumstances documented.'
    }
    """
    When method post
    Then status 201
    And match response.id == '#uuid'
    And match response.caseId == decisionFlow.decidedCase.id
    And match response.status == 'ACTIVE'

    Given url baseUrl
    And path 'api', 'v1', 'cases', decisionFlow.decidedCase.id
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    When method get
    Then status 200
    And match response.status == 'UNDER_APPEAL'
