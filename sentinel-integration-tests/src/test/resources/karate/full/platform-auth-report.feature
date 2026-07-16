@full
@auth
@report
Feature: Authentication and report intake guardrails work against the running application

  Background:
    * def intakeAuth = callonce read('classpath:karate/common/auth.feature') { username: 'intake-jkt' }
    * def triageAuth = callonce read('classpath:karate/common/auth.feature') { username: 'triage-jkt' }
    * def investigatorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'investigator-jkt' }
    * def reviewerAuth = callonce read('classpath:karate/common/auth.feature') { username: 'reviewer-jkt' }
    * def decisionAuth = callonce read('classpath:karate/common/auth.feature') { username: 'decision-jkt' }
    * def appealAuth = callonce read('classpath:karate/common/auth.feature') { username: 'appeal-jkt' }
    * def supervisorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'supervisor-jkt' }
    * def auditorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'auditor-jkt' }
    * def systemAdminAuth = callonce read('classpath:karate/common/auth.feature') { username: 'system-admin' }

  Scenario: primary roles can authenticate and missing bearer token is rejected
    * match intakeAuth.accessToken == '#string'
    * match triageAuth.accessToken == '#string'
    * match investigatorAuth.accessToken == '#string'
    * match reviewerAuth.accessToken == '#string'
    * match decisionAuth.accessToken == '#string'
    * match appealAuth.accessToken == '#string'
    * match supervisorAuth.accessToken == '#string'
    * match auditorAuth.accessToken == '#string'
    * match systemAdminAuth.accessToken == '#string'

    Given url baseUrl
    And path 'api', 'v1', 'cases'
    When method get
    Then status 401
    And match response.code == 'UNAUTHENTICATED'

  Scenario: report creation validates payload and enforces intake permissions
    Given url baseUrl
    And path 'api', 'v1', 'reports'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request
    """
    {
      title: 'Forbidden report creation',
      description: 'Only intake should be able to create this report.',
      jurisdictionCode: 'JKT',
      reporterName: 'Karate'
    }
    """
    When method post
    Then status 403
    And match response.code == 'FORBIDDEN'

    Given url baseUrl
    And path 'api', 'v1', 'reports'
    And header Authorization = 'Bearer ' + intakeAuth.accessToken
    And request
    """
    {
      description: 'Missing title should fail validation.',
      jurisdictionCode: 'JKT',
      reporterName: 'Karate'
    }
    """
    When method post
    Then status 400

  Scenario: triage enforces actor permissions and optimistic version checks
    * def suffix = karate.uuid()
    * def reportPayload =
    """
    {
      title: 'Karate auth report ' + suffix,
      description: 'Triage authorization and version coverage.',
      jurisdictionCode: 'JKT',
      reporterName: 'Karate Auth'
    }
    """
    Given url baseUrl
    And path 'api', 'v1', 'reports'
    And header Authorization = 'Bearer ' + intakeAuth.accessToken
    And request reportPayload
    When method post
    Then status 201
    * def report = response

    Given url baseUrl
    And path 'api', 'v1', 'reports', report.id, 'triage'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And request { expectedVersion: '#(report.version)', reason: 'Investigator should not triage reports.' }
    When method post
    Then status 403
    And match response.code == 'FORBIDDEN'

    Given url baseUrl
    And path 'api', 'v1', 'reports', report.id, 'triage'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request { expectedVersion: '#(report.version)', reason: 'Valid triage for optimistic locking coverage.' }
    When method post
    Then status 200
    And match response.status == 'TRIAGED'

    Given url baseUrl
    And path 'api', 'v1', 'reports', report.id, 'triage'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request { expectedVersion: '#(report.version)', reason: 'Retry with stale version.' }
    When method post
    Then status 409
    And match response.code == 'CONCURRENT_MODIFICATION'
