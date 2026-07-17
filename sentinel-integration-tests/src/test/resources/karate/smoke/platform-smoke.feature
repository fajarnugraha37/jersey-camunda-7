@smoke
@regression
@full
Feature: Sentinel running-app smoke flow

  Background:
    * def intakeAuth = callonce read('classpath:karate/common/auth.feature') { username: 'intake-jkt' }
    * def triageAuth = callonce read('classpath:karate/common/auth.feature') { username: 'triage-jkt' }

  Scenario: health endpoint is up
    Given url baseUrl
    And path 'health'
    When method get
    Then status 200
    And match response.status == 'UP'
    And match response.database == 'UP'

  Scenario: report intake to case bootstrap works against the running app
    * def suffix = karate.uuid()
    * def reportPayload =
    """
    {
      title: 'Karate smoke report ' + suffix,
      description: 'Karate smoke flow to bootstrap a case on the running app.',
      jurisdictionCode: 'JKT',
      reporterName: 'Karate Smoke'
    }
    """
    Given url baseUrl
    And path 'api', 'v1', 'reports'
    And header Authorization = 'Bearer ' + intakeAuth.accessToken
    And request reportPayload
    When method post
    Then status 201
    And match response.id == '#uuid'
    And match response.version == 0
    And match response.title == reportPayload.title
    * def reportId = response.id
    * def reportVersion = response.version

    Given url baseUrl
    And path 'api', 'v1', 'reports', reportId
    And header Authorization = 'Bearer ' + intakeAuth.accessToken
    When method get
    Then status 200
    And match response.id == reportId
    And match response.status == 'SUBMITTED'

    Given url baseUrl
    And path 'api', 'v1', 'reports', reportId, 'triage'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request { expectedVersion: '#(reportVersion)', reason: 'Karate smoke triage.' }
    When method post
    Then status 200
    And match response.id == reportId
    And match response.status == 'TRIAGED'

    * def casePayload =
    """
    {
      reportId: '#(reportId)',
      title: 'Karate smoke case ' + suffix,
      summary: 'Case created from Karate smoke flow.',
      classification: 'CONFIDENTIAL'
    }
    """
    Given url baseUrl
    And path 'api', 'v1', 'cases'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request casePayload
    When method post
    Then status 201
    And match response.id == '#uuid'
    And match response.reportId == reportId
    And match response.title == casePayload.title
    And match response.status == 'CREATED'
    And match response.caseNumber == '#regex JKT-ENF-2026-\\d{8}'
    * def caseId = response.id

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    When method get
    Then status 200
    And match response.id == caseId
    And match response.reportId == reportId
    And match response.title == casePayload.title
