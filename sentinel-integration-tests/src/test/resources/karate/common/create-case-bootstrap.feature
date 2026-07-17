Feature: Create a report, triage it, and bootstrap a case

  Scenario:
    * def suffix = karate.get('suffix', karate.uuid())
    * def jurisdictionCode = karate.get('jurisdictionCode', 'JKT')
    * def reportTitle = karate.get('reportTitle', 'Karate report ' + suffix)
    * def reportDescription = karate.get('reportDescription', 'Karate-generated report bootstrap flow.')
    * def reporterName = karate.get('reporterName', 'Karate Runner')
    * def triageReason = karate.get('triageReason', 'Karate triage bootstrap.')
    * def caseTitle = karate.get('caseTitle', 'Karate case ' + suffix)
    * def caseSummary = karate.get('caseSummary', 'Karate-generated case bootstrap flow.')
    * def classification = karate.get('classification', 'CONFIDENTIAL')

    Given url baseUrl
    And path 'api', 'v1', 'reports'
    And header Authorization = 'Bearer ' + intakeToken
    And request
    """
    {
      title: '#(reportTitle)',
      description: '#(reportDescription)',
      jurisdictionCode: '#(jurisdictionCode)',
      reporterName: '#(reporterName)'
    }
    """
    When method post
    Then status 201
    And match response.id == '#uuid'
    And match response.status == 'SUBMITTED'
    * def report = response

    Given url baseUrl
    And path 'api', 'v1', 'reports', report.id, 'triage'
    And header Authorization = 'Bearer ' + triageToken
    And request { expectedVersion: '#(report.version)', reason: '#(triageReason)' }
    When method post
    Then status 200
    And match response.id == report.id
    And match response.status == 'TRIAGED'
    * def triagedReport = response

    Given url baseUrl
    And path 'api', 'v1', 'cases'
    And header Authorization = 'Bearer ' + triageToken
    And request
    """
    {
      reportId: '#(report.id)',
      title: '#(caseTitle)',
      summary: '#(caseSummary)',
      classification: '#(classification)'
    }
    """
    When method post
    Then status 201
    And match response.id == '#uuid'
    And match response.reportId == report.id
    And match response.title == caseTitle
    And match response.status == 'CREATED'
    And match response.caseNumber == '#regex JKT-ENF-2026-\\d{8}'
    * def createdCase = response
