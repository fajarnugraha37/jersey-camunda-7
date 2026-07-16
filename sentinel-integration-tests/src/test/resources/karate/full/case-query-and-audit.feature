@full
@case-query
@audit
Feature: Case listing and audit listing support search, sorting, and cursor validation

  Background:
    * def intakeAuth = callonce read('classpath:karate/common/auth.feature') { username: 'intake-jkt' }
    * def triageAuth = callonce read('classpath:karate/common/auth.feature') { username: 'triage-jkt' }
    * def auditorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'auditor-jkt' }

  Scenario: case list supports quick search, field search, dynamic sort, and cursor pagination
    * def token = 'list-pattern-' + karate.uuid().substring(0, 8)
    * def alpha = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: '#(token + " Alpha")',
      caseSummary: 'Title field carries the search token.'
    }
    """
    * def zulu = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: 'Zulu unrelated',
      caseSummary: '#(token + " summary match only.")'
    }
    """
    * def bravo = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: '#(token + " Bravo")',
      caseSummary: 'Another title match for sort verification.'
    }
    """

    Given url baseUrl
    And path 'api', 'v1', 'cases'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And param q = token
    And param sortBy = 'TITLE'
    And param sortDirection = 'ASC'
    And param limit = 10
    When method get
    Then status 200
    And match response.items == '#[3]'
    And match response.items[0].id == alpha.createdCase.id
    And match response.items[1].id == bravo.createdCase.id
    And match response.items[2].id == zulu.createdCase.id

    Given url baseUrl
    And path 'api', 'v1', 'cases'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And param searchField = 'TITLE'
    And param searchValue = token
    And param sortBy = 'TITLE'
    And param sortDirection = 'ASC'
    And param limit = 10
    When method get
    Then status 200
    And match response.items == '#[2]'
    And match response.items[0].id == alpha.createdCase.id
    And match response.items[1].id == bravo.createdCase.id

    Given url baseUrl
    And path 'api', 'v1', 'cases'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And param q = token
    And param sortBy = 'TITLE'
    And param sortDirection = 'ASC'
    And param limit = 2
    When method get
    Then status 200
    And match response.items == '#[2]'
    And match response.nextCursor == '#string'
    * def firstPage = response

    Given url baseUrl
    And path 'api', 'v1', 'cases'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And param q = token
    And param sortBy = 'TITLE'
    And param sortDirection = 'ASC'
    And param limit = 2
    And param cursor = firstPage.nextCursor
    When method get
    Then status 200
    And match response.items == '#[1]'
    And match response.items[0].id == zulu.createdCase.id

  Scenario: get case and query validation return expected envelopes
    * def bootstrap = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: 'Karate get-case coverage',
      caseSummary: 'Query validation and not-found coverage.'
    }
    """

    Given url baseUrl
    And path 'api', 'v1', 'cases', bootstrap.createdCase.id
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    When method get
    Then status 200
    And match response.id == bootstrap.createdCase.id

    * def missingCaseId = karate.uuid()
    Given url baseUrl
    And path 'api', 'v1', 'cases', missingCaseId
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    When method get
    Then status 404
    And match response.code == 'CASE_NOT_FOUND'

    * def token = 'cursor-mismatch-' + karate.uuid().substring(0, 8)
    * def alpha = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: '#(token + " Alpha")',
      caseSummary: 'Cursor mismatch validation.'
    }
    """
    * def bravo = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: '#(token + " Bravo")',
      caseSummary: 'Cursor mismatch validation.'
    }
    """

    Given url baseUrl
    And path 'api', 'v1', 'cases'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And param q = token
    And param sortBy = 'TITLE'
    And param sortDirection = 'ASC'
    And param limit = 1
    When method get
    Then status 200
    And match response.nextCursor == '#string'
    * def cursorPage = response

    Given url baseUrl
    And path 'api', 'v1', 'cases'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And param q = token
    And param sortBy = 'CREATED_AT'
    And param sortDirection = 'DESC'
    And param limit = 1
    And param cursor = cursorPage.nextCursor
    When method get
    Then status 400
    And match response.code == 'MALFORMED_REQUEST'

    Given url baseUrl
    And path 'api', 'v1', 'cases'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And param searchField = 'TITLE'
    And param limit = 10
    When method get
    Then status 400
    And match response.code == 'MALFORMED_REQUEST'

  Scenario: audit event list supports quick search, field search, sorting, and cursor pagination
    * def assignedFlow = call read('classpath:karate/common/create-assigned-case.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: 'Audit list verification',
      caseSummary: 'Exercise dynamic audit listing.'
    }
    """
    * def caseId = assignedFlow.bootstrap.createdCase.id

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'transitions'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request
    """
    {
      targetStatus: 'UNDER_TRIAGE',
      expectedVersion: #(assignedFlow.assignedCase.version),
      reason: 'Audit query transition one.'
    }
    """
    When method post
    Then status 200
    * def underTriageCase = response

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'transitions'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request
    """
    {
      targetStatus: 'UNDER_INVESTIGATION',
      expectedVersion: #(underTriageCase.version),
      reason: 'Audit query transition two.'
    }
    """
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'audit-events'
    And header Authorization = 'Bearer ' + auditorAuth.accessToken
    And param q = 'assigned'
    And param sortBy = 'EVENT_TYPE'
    And param sortDirection = 'ASC'
    And param limit = 10
    When method get
    Then status 200
    And match response.items == '#[1]'
    And match response.items[0].eventType == 'CaseAssigned'

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'audit-events'
    And header Authorization = 'Bearer ' + auditorAuth.accessToken
    And param searchField = 'ACTION'
    And param searchValue = 'transitioned'
    And param sortBy = 'TIMESTAMP'
    And param sortDirection = 'DESC'
    And param limit = 10
    When method get
    Then status 200
    And match response.items == '#[2]'
    And match each response.items[*].action == 'CASE_TRANSITIONED'

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'audit-events'
    And header Authorization = 'Bearer ' + auditorAuth.accessToken
    And param sortBy = 'EVENT_TYPE'
    And param sortDirection = 'ASC'
    And param limit = 2
    When method get
    Then status 200
    And match response.items == '#[2]'
    And match response.nextCursor == '#string'
    * def auditPage = response

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'audit-events'
    And header Authorization = 'Bearer ' + auditorAuth.accessToken
    And param sortBy = 'EVENT_TYPE'
    And param sortDirection = 'ASC'
    And param limit = 2
    And param cursor = auditPage.nextCursor
    When method get
    Then status 200
    And assert response.items.length >= 1

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'audit-events'
    And header Authorization = 'Bearer ' + auditorAuth.accessToken
    And param searchField = 'ACTION'
    And param limit = 10
    When method get
    Then status 400
    And match response.code == 'MALFORMED_REQUEST'
