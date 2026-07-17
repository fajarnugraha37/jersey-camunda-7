@full
@case-state
@authorization
Feature: Case state, assignment, and authorization rules hold against the running application

  Background:
    * def intakeAuth = callonce read('classpath:karate/common/auth.feature') { username: 'intake-jkt' }
    * def triageAuth = callonce read('classpath:karate/common/auth.feature') { username: 'triage-jkt' }
    * def investigatorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'investigator-jkt' }
    * def reviewerAuth = callonce read('classpath:karate/common/auth.feature') { username: 'reviewer-jkt' }
    * def decisionAuth = callonce read('classpath:karate/common/auth.feature') { username: 'decision-jkt' }
    * def appealAuth = callonce read('classpath:karate/common/auth.feature') { username: 'appeal-jkt' }
    * def supervisorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'supervisor-jkt' }
    * def supervisorUnitTwoAuth = callonce read('classpath:karate/common/auth.feature') { username: 'supervisor-jkt-unit-2' }
    * def auditorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'auditor-jkt' }
    * def reviewerPublicAuth = callonce read('classpath:karate/common/auth.feature') { username: 'reviewer-jkt-public' }
    * def conflictedReviewerAuth = callonce read('classpath:karate/common/auth.feature') { username: 'reviewer-jkt-conflicted' }
    * def DbSupport = Java.type('com.sentinel.enforcement.integration.karate.support.LiveDbSupport')

  Scenario: full direct lifecycle persists history and audit trail
    * def suffix = karate.uuid()
    * def decisionFlow = call read('classpath:karate/common/create-published-decision-direct.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      investigatorToken: '#(investigatorAuth.accessToken)',
      reviewerToken: '#(reviewerAuth.accessToken)',
      decisionToken: '#(decisionAuth.accessToken)',
      supervisorToken: '#(supervisorAuth.accessToken)',
      suffix: '#(suffix)',
      reportTitle: 'Karate direct lifecycle report ' + suffix,
      caseTitle: 'Karate direct lifecycle case ' + suffix,
      caseSummary: 'Direct lifecycle history and audit coverage.'
    }
    """

    Given url baseUrl
    And path 'api', 'v1', 'cases', decisionFlow.decidedCase.id, 'transitions'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And request
    """
    {
      targetStatus: 'ENFORCEMENT_IN_PROGRESS',
      expectedVersion: #(decisionFlow.decidedCase.version),
      reason: 'Entering enforcement monitoring.'
    }
    """
    When method post
    Then status 200
    * def enforcementCase = response

    Given url baseUrl
    And path 'api', 'v1', 'cases', decisionFlow.decidedCase.id, 'transitions'
    And header Authorization = 'Bearer ' + supervisorAuth.accessToken
    And request
    """
    {
      targetStatus: 'CLOSED',
      expectedVersion: #(enforcementCase.version),
      reason: 'All obligations closed.'
    }
    """
    When method post
    Then status 200
    And match response.status == 'CLOSED'
    And match response.version == 8
    * def closedCase = response

    Given url baseUrl
    And path 'api', 'v1', 'cases', closedCase.id, 'audit-events'
    And header Authorization = 'Bearer ' + auditorAuth.accessToken
    And param limit = 20
    When method get
    Then status 200
    And assert response.items.length == 15

    * match DbSupport.countCaseStatusHistory(closedCase.id) == 8
    * match DbSupport.countAuditEvents(closedCase.id) == 15
    * match DbSupport.countCaseAssignments(closedCase.id) == 1
    * match DbSupport.countRecommendations(closedCase.id) == 1
    * match DbSupport.countDecisions(closedCase.id) == 1

  Scenario: assignment rotation preserves one active row and no-effect reassignment is rejected
    * def rotationFlow = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: 'Reassignment case',
      caseSummary: 'Assignment rotation should preserve one active row.'
    }
    """
    * def caseId = rotationFlow.createdCase.id

    * def firstAssignment = call read('classpath:karate/common/assign-case.feature')
    """
    {
      accessToken: '#(triageAuth.accessToken)',
      caseId: '#(caseId)',
      expectedVersion: '#(rotationFlow.createdCase.version)',
      assignedUnitId: 'JKT-UNIT-1',
      assigneeUserId: 'investigator-jkt',
      reason: 'Initial assignment.'
    }
    """

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'assignments'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request
    """
    {
      assignedUnitId: 'JKT-UNIT-1',
      assigneeUserId: 'other-investigator',
      expectedVersion: #(firstAssignment.assignedCase.version),
      reason: 'Reassign to another investigator.'
    }
    """
    When method post
    Then status 200
    And match response.assigneeUserId == 'other-investigator'
    * def reassignedCase = response

    * match DbSupport.countCaseAssignments(caseId) == 2
    * match DbSupport.activeAssignmentCount(caseId) == 1
    * match DbSupport.inactiveReleasedAssignmentCount(caseId, 'triage-jkt') == 1
    * match DbSupport.activeAssignee(caseId) == 'other-investigator'

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'assignments'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request
    """
    {
      assignedUnitId: 'JKT-UNIT-1',
      assigneeUserId: 'other-investigator',
      expectedVersion: #(reassignedCase.version),
      reason: 'Retry same assignment target.'
    }
    """
    When method post
    Then status 409
    And match response.code == 'NO_EFFECT_ASSIGNMENT'

  Scenario: invalid transition jump and stale expected version return conflict envelopes
    * def bootstrap = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: 'Decision case',
      caseSummary: 'Conflict checks.'
    }
    """
    * def createdCase = bootstrap.createdCase

    Given url baseUrl
    And path 'api', 'v1', 'cases', createdCase.id, 'transitions'
    And header Authorization = 'Bearer ' + decisionAuth.accessToken
    And request
    """
    {
      targetStatus: 'DECIDED',
      expectedVersion: #(createdCase.version),
      reason: 'Attempting to skip required states.'
    }
    """
    When method post
    Then status 409
    And match response.code == 'CASE_TRANSITION_NOT_ALLOWED'

    Given url baseUrl
    And path 'api', 'v1', 'cases', createdCase.id, 'transitions'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request
    """
    {
      targetStatus: 'UNDER_TRIAGE',
      expectedVersion: #(createdCase.version),
      reason: 'Proper triage start.'
    }
    """
    When method post
    Then status 200
    * def underTriageCase = response

    Given url baseUrl
    And path 'api', 'v1', 'cases', createdCase.id, 'transitions'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request
    """
    {
      targetStatus: 'UNDER_INVESTIGATION',
      expectedVersion: #(createdCase.version),
      reason: 'Retry with stale version.'
    }
    """
    When method post
    Then status 409
    And match response.code == 'CONCURRENT_MODIFICATION'

  Scenario: investigators only see directly assigned cases
    * def caseOne = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: 'Case one',
      caseSummary: 'Assigned to investigator.'
    }
    """
    * def caseTwo = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: 'Case two',
      caseSummary: 'Assigned elsewhere.'
    }
    """

    * call read('classpath:karate/common/assign-case.feature')
    """
    {
      accessToken: '#(triageAuth.accessToken)',
      caseId: '#(caseOne.createdCase.id)',
      expectedVersion: '#(caseOne.createdCase.version)',
      assignedUnitId: 'JKT-UNIT-1',
      assigneeUserId: 'investigator-jkt',
      reason: 'Direct investigator assignment.'
    }
    """
    * call read('classpath:karate/common/assign-case.feature')
    """
    {
      accessToken: '#(triageAuth.accessToken)',
      caseId: '#(caseTwo.createdCase.id)',
      expectedVersion: '#(caseTwo.createdCase.version)',
      assignedUnitId: 'JKT-UNIT-1',
      assigneeUserId: 'other-investigator',
      reason: 'Assigned to another investigator.'
    }
    """

    Given url baseUrl
    And path 'api', 'v1', 'cases'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    When method get
    Then status 200
    And match response.items[*].id contains caseOne.createdCase.id
    And match response.items[*].id !contains caseTwo.createdCase.id

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseOne.createdCase.id
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    When method get
    Then status 200
    And match response.id == caseOne.createdCase.id

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseTwo.createdCase.id
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    When method get
    Then status 403
    And match response.code == 'FORBIDDEN'

  Scenario: jurisdiction, classification, conflict-of-interest, and auditor mutability rules are enforced
    * def secretCase = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: 'Secret case',
      caseSummary: 'Restricted by classification and unit.',
      classification: 'SECRET'
    }
    """

    Given url baseUrl
    And path 'api', 'v1', 'cases', secretCase.createdCase.id, 'assignments'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request
    """
    {
      assignedUnitId: 'JKT-UNIT-1',
      assigneeUserId: 'investigator-jkt',
      expectedVersion: #(secretCase.createdCase.version),
      reason: 'Assign to unit one.'
    }
    """
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'cases'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And param classification = 'SECRET'
    And param assignedUnitId = 'JKT-UNIT-1'
    And param limit = 10
    When method get
    Then status 200
    And match response.items[*].id contains secretCase.createdCase.id

    Given url baseUrl
    And path 'api', 'v1', 'cases', secretCase.createdCase.id
    And header Authorization = 'Bearer ' + supervisorUnitTwoAuth.accessToken
    When method get
    Then status 403
    And match response.code == 'FORBIDDEN'

    Given url baseUrl
    And path 'api', 'v1', 'cases', secretCase.createdCase.id
    And header Authorization = 'Bearer ' + reviewerPublicAuth.accessToken
    When method get
    Then status 403
    And match response.code == 'FORBIDDEN'

    Given url baseUrl
    And path 'api', 'v1', 'cases'
    And header Authorization = 'Bearer ' + reviewerPublicAuth.accessToken
    And param classification = 'SECRET'
    And param limit = 10
    When method get
    Then status 200
    And match response.items[*].id !contains secretCase.createdCase.id

    * def conflictFlow = call read('classpath:karate/common/create-assigned-case.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: 'Conflict approval case',
      caseSummary: 'Reviewer has declared conflict.'
    }
    """
    * def conflictCaseId = conflictFlow.bootstrap.createdCase.id

    Given url baseUrl
    And path 'api', 'v1', 'cases', conflictCaseId, 'transitions'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request
    """
    {
      targetStatus: 'UNDER_TRIAGE',
      expectedVersion: #(conflictFlow.assignedCase.version),
      reason: 'Move into triage.'
    }
    """
    When method post
    Then status 200
    * def conflictUnderTriage = response

    Given url baseUrl
    And path 'api', 'v1', 'cases', conflictCaseId, 'transitions'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request
    """
    {
      targetStatus: 'UNDER_INVESTIGATION',
      expectedVersion: #(conflictUnderTriage.version),
      reason: 'Open investigation.'
    }
    """
    When method post
    Then status 200
    * def conflictUnderInvestigation = response

    Given url baseUrl
    And path 'api', 'v1', 'cases', conflictCaseId, 'recommendations'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And request
    """
    {
      title: 'Recommendation with conflict',
      summary: 'Conflict should block reviewer approval.',
      proposedDecision: 'Proceed with review.',
      proposedSanction: null
    }
    """
    When method post
    Then status 201
    * def conflictRecommendation = response

    Given url baseUrl
    And path 'api', 'v1', 'recommendations', conflictRecommendation.id, 'submit'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'cases', conflictCaseId, 'transitions'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And request
    """
    {
      targetStatus: 'PENDING_REVIEW',
      expectedVersion: #(conflictUnderInvestigation.version),
      reason: 'Recommendation submitted.'
    }
    """
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'recommendations', conflictRecommendation.id, 'reviews'
    And header Authorization = 'Bearer ' + conflictedReviewerAuth.accessToken
    And request { reviewSummary: 'Attempting conflicted approval.' }
    When method post
    Then status 403
    And match response.code == 'FORBIDDEN'

    Given url baseUrl
    And path 'api', 'v1', 'cases', secretCase.createdCase.id
    And header Authorization = 'Bearer ' + auditorAuth.accessToken
    When method get
    Then status 200
    And match response.id == secretCase.createdCase.id

    Given url baseUrl
    And path 'api', 'v1', 'cases', secretCase.createdCase.id, 'transitions'
    And header Authorization = 'Bearer ' + auditorAuth.accessToken
    And request
    """
    {
      targetStatus: 'UNDER_TRIAGE',
      expectedVersion: #(secretCase.createdCase.version),
      reason: 'Auditor should not mutate.'
    }
    """
    When method post
    Then status 403
    And match response.code == 'FORBIDDEN'
