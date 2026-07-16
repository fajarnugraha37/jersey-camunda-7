@full
@locking
@decision
Feature: Decision approval fails fast while the decision row is locked

  Background:
    * def intakeAuth = callonce read('classpath:karate/common/auth.feature') { username: 'intake-jkt' }
    * def triageAuth = callonce read('classpath:karate/common/auth.feature') { username: 'triage-jkt' }
    * def investigatorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'investigator-jkt' }
    * def reviewerAuth = callonce read('classpath:karate/common/auth.feature') { username: 'reviewer-jkt' }
    * def decisionAuth = callonce read('classpath:karate/common/auth.feature') { username: 'decision-jkt' }
    * def supervisorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'supervisor-jkt' }
    * def DbSupport = Java.type('com.sentinel.enforcement.integration.karate.support.LiveDbSupport')

  Scenario: row lock returns DECISION_LOCKED and approval succeeds after release
    * def suffix = karate.uuid()
    * def draftFlow = call read('classpath:karate/common/create-draft-decision.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      investigatorToken: '#(investigatorAuth.accessToken)',
      reviewerToken: '#(reviewerAuth.accessToken)',
      decisionToken: '#(decisionAuth.accessToken)',
      suffix: '#(suffix)',
      reportTitle: 'Locked approval report ' + suffix,
      caseTitle: 'Locked approval case ' + suffix,
      caseSummary: 'Approval should fail fast while another transaction holds the row lock.'
    }
    """
    * def lockId = DbSupport.acquireDecisionLock(draftFlow.decision.id)

    Given url baseUrl
    And path 'api', 'v1', 'decisions', draftFlow.decision.id, 'approve'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 409
    And match response.code == 'DECISION_LOCKED'

    * match DbSupport.countAuditEventsByType(draftFlow.pendingDecisionCase.id, 'DecisionApproved') == 0
    * match DbSupport.releaseLock(lockId) == true

    Given url baseUrl
    And path 'api', 'v1', 'decisions', draftFlow.decision.id, 'approve'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200
    And match response.approvedAt == '#string'

    * match DbSupport.countAuditEventsByType(draftFlow.pendingDecisionCase.id, 'DecisionApproved') == 1
