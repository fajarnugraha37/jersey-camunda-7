@full
@messaging
Feature: Notification and outbox side effects remain observable against the running application

  Background:
    * def intakeAuth = callonce read('classpath:karate/common/auth.feature') { username: 'intake-jkt' }
    * def triageAuth = callonce read('classpath:karate/common/auth.feature') { username: 'triage-jkt' }
    * def investigatorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'investigator-jkt' }
    * def reviewerAuth = callonce read('classpath:karate/common/auth.feature') { username: 'reviewer-jkt' }
    * def decisionAuth = callonce read('classpath:karate/common/auth.feature') { username: 'decision-jkt' }
    * def appealAuth = callonce read('classpath:karate/common/auth.feature') { username: 'appeal-jkt' }
    * def supervisorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'supervisor-jkt' }
    * def DbSupport = Java.type('com.sentinel.enforcement.integration.karate.support.LiveDbSupport')
    * def MessagingSupport = Java.type('com.sentinel.enforcement.integration.karate.support.LiveMessagingSupport')

  Scenario: case creation publishes notification and audit outbox effects that are visible downstream
    * def suffix = karate.uuid()
    * def bootstrap = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      suffix: '#(suffix)',
      reportTitle: 'Kafka observable report ' + suffix,
      caseTitle: 'Kafka observable case ' + suffix,
      caseSummary: 'Outbox and notification visibility coverage.'
    }
    """
    * def caseId = bootstrap.createdCase.id
    * def caseNumber = bootstrap.createdCase.caseNumber

    * assert waitUntil(function(){ return DbSupport.publishedOutboxCountByTopicAndCaseId('notification.command.v1', caseId) > 0 }, 120000, 500)
    * assert waitUntil(function(){ return DbSupport.publishedOutboxCountByTopicAndCaseId('notification.result.v1', caseId) > 0 }, 120000, 500)
    * assert waitUntil(function(){ return DbSupport.publishedAuditOutboxCount(caseId, 'CaseCreated') > 0 }, 120000, 500)
    * assert waitUntil(function(){ return DbSupport.notificationCountByCaseTypeAndStatus(caseId, 'CaseCreated', 'SENT') > 0 }, 120000, 500)

    Given url mailpitBaseUrl
    And path 'api', 'v1', 'messages'
    When method get
    Then status 200
    * def caseEmails = messagesBySubjectFragment(response.messages, caseNumber)
    * assert caseEmails.length >= 1

  Scenario: duplicate lifecycle delivery does not create duplicate notification side effects
    * def bootstrap = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: 'Duplicate event case',
      caseSummary: 'Inbox deduplication coverage.'
    }
    """
    * def caseId = bootstrap.createdCase.id
    * def eventId = DbSupport.outboxEventIdForAggregateAndType(caseId, 'CaseCreated')
    * match eventId == '#string'
    * assert waitUntil(function(){ return DbSupport.publishedOutboxCountByTopicAndCaseId('notification.command.v1', caseId) > 0 }, 120000, 500)
    * assert waitUntil(function(){ return DbSupport.notificationCountByCaseTypeAndStatus(caseId, 'CaseCreated', 'SENT') > 0 }, 120000, 500)
    * def notificationCountBefore = DbSupport.notificationCountByCaseTypeAndStatus(caseId, 'CaseCreated', 'SENT')

    * def envelopeJson = DbSupport.outboxEnvelopeJson(eventId)
    * eval MessagingSupport.produceRawEvent('case.lifecycle.v1', caseId, envelopeJson)
    * eval MessagingSupport.produceRawEvent('case.lifecycle.v1', caseId, envelopeJson)

    * karate.pause(3000)
    * match DbSupport.notificationCountByCaseTypeAndStatus(caseId, 'CaseCreated', 'SENT') == notificationCountBefore
    * assert DbSupport.inboxEventCount('notification-consumer', eventId) > 0

  Scenario: decision, sanction, and appeal lifecycle notifications appear without missing-topic regressions
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
      reportTitle: 'Phase seven notifications report ' + suffix,
      caseTitle: 'Phase seven notifications case ' + suffix,
      caseSummary: 'Notification fan-out coverage.',
      violationProven: true,
      obligationDueDate: '2026-08-29',
      appealDeadline: '2026-08-15'
    }
    """
    * def caseId = decisionFlow.decidedCase.id

    * assert waitUntil(function(){ return DbSupport.notificationCountByCaseAndType(caseId, 'DecisionPublished') > 0 }, 120000, 500)
    * assert waitUntil(function(){ return DbSupport.notificationCountByCaseAndType(caseId, 'SanctionCreated') > 0 }, 120000, 500)

    Given url baseUrl
    And path 'api', 'v1', 'decisions', decisionFlow.decision.id, 'appeals'
    And header Authorization = 'Bearer ' + appealAuth.accessToken
    And request
    """
    {
      rationale: 'New mitigating evidence',
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
    And request { outcome: 'GRANTED', summary: 'Granted' }
    When method post
    Then status 200

    * def appealReviewTaskLookup = call read('classpath:karate/common/list-single-task.feature') { accessToken: '#(appealAuth.accessToken)', caseId: '#(caseId)' }
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

    * assert waitUntil(function(){ return DbSupport.notificationCountByCaseAndType(caseId, 'AppealFiled') > 0 }, 120000, 500)
    * assert waitUntil(function(){ return DbSupport.notificationCountByCaseAndType(caseId, 'AppealDecided') > 0 }, 120000, 500)
    * assert waitUntil(function(){ return DbSupport.notificationCountByCaseAndType(caseId, 'SanctionCancelled') > 0 }, 120000, 500)
