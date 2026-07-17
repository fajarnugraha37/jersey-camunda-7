@regression
@full
@maintenance
Feature: Maintenance operations work against the running application

  Background:
    * def intakeAuth = callonce read('classpath:karate/common/auth.feature') { username: 'intake-jkt' }
    * def triageAuth = callonce read('classpath:karate/common/auth.feature') { username: 'triage-jkt' }
    * def investigatorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'investigator-jkt' }
    * def reviewerAuth = callonce read('classpath:karate/common/auth.feature') { username: 'reviewer-jkt' }
    * def decisionAuth = callonce read('classpath:karate/common/auth.feature') { username: 'decision-jkt' }
    * def supervisorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'supervisor-jkt' }
    * def DbSupport = Java.type('com.sentinel.enforcement.integration.karate.support.LiveDbSupport')

  Scenario: overdue recalculation marks active obligations overdue and closure remains blocked
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
      reportTitle: 'Karate maintenance report ' + suffix,
      caseTitle: 'Karate maintenance case ' + suffix,
      caseSummary: 'Maintenance overdue regression driven by Karate.',
      violationProven: true,
      obligationDueDate: '2026-07-10',
      appealDeadline: '2026-07-01'
    }
    """
    * def expectedAffectedRows = DbSupport.countOverdueSanctionObligations('2026-07-16')

    Given url baseUrl
    And path 'api', 'v1', 'operations', 'sanction-obligations', 'recalculate-overdue'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And request { effectiveDate: '2026-07-16' }
    When method post
    Then status 200
    And match response.runId == '#uuid'
    And match response.operationName == 'recalculate-overdue-sanction-obligations'
    And match response.requestedBy == 'supervisor-jkt'
    And match response.effectiveDate == '2026-07-16'
    And match response.resultStatus == 'COMPLETED'
    And match response.affectedRows == expectedAffectedRows
    * def maintenanceRun = response

    * match DbSupport.sanctionObligationStatusByDecisionId(decisionFlow.decision.id) == 'OVERDUE'
    * match DbSupport.maintenanceRunCount(maintenanceRun.runId) == 1

    Given url baseUrl
    And path 'api', 'v1', 'cases', decisionFlow.decidedCase.id, 'transitions'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And request
    """
    {
      targetStatus: 'ENFORCEMENT_IN_PROGRESS',
      expectedVersion: #(decisionFlow.decidedCase.version),
      reason: 'Entering enforcement after overdue recalculation.'
    }
    """
    When method post
    Then status 200
    And match response.status == 'ENFORCEMENT_IN_PROGRESS'
    * def enforcementCase = response

    Given url baseUrl
    And path 'api', 'v1', 'cases', decisionFlow.decidedCase.id, 'transitions'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And request
    """
    {
      targetStatus: 'CLOSED',
      expectedVersion: #(enforcementCase.version),
      reason: 'Attempting closure while overdue obligation remains.'
    }
    """
    When method post
    Then status 409
    And match response.code == 'CASE_TRANSITION_NOT_ALLOWED'

  Scenario: overdue recalculation fails fast while sanction obligation table is locked
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
      reportTitle: 'Karate maintenance lock report ' + suffix,
      caseTitle: 'Karate maintenance lock case ' + suffix,
      caseSummary: 'Maintenance lock conflict regression driven by Karate.',
      violationProven: true,
      obligationDueDate: '2026-07-10',
      appealDeadline: '2026-07-01'
    }
    """
    * def maintenanceRunCountBefore = DbSupport.maintenanceRunCount()
    * def lockId = DbSupport.acquireTableLock('sanction_obligation', 'SHARE ROW EXCLUSIVE')

    Given url baseUrl
    And path 'api', 'v1', 'operations', 'sanction-obligations', 'recalculate-overdue'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And request { effectiveDate: '2026-07-16' }
    When method post
    Then status 409
    And match response.code == 'MAINTENANCE_OPERATION_LOCKED'

    * match DbSupport.maintenanceRunCount() == maintenanceRunCountBefore
    * match DbSupport.releaseLock(lockId) == true
