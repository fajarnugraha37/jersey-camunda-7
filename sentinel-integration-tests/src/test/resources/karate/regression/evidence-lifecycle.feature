@regression
@evidence
Feature: Evidence upload and download flows work against the running app

  Background:
    * def intakeAuth = callonce read('classpath:karate/common/auth.feature') { username: 'intake-jkt' }
    * def triageAuth = callonce read('classpath:karate/common/auth.feature') { username: 'triage-jkt' }
    * def investigatorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'investigator-jkt' }
    * def auditorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'auditor-jkt' }
    * def evidenceContent = 'gift,amount\nbook,100'
    * def checksum = '041cbe1ae81b0a15679537491891a60ffef030f0dda0c4ecf43e691e327e92f3'

  Scenario: upload finalize fetch and download evidence
    * def suffix = karate.uuid()
    * def bootstrap = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      suffix: '#(suffix)',
      reportTitle: 'Karate evidence report ' + suffix,
      caseTitle: 'Karate evidence case ' + suffix,
      caseSummary: 'Evidence lifecycle regression driven by Karate.'
    }
    """
    * def assignment = call read('classpath:karate/common/assign-case.feature')
    """
    {
      accessToken: '#(triageAuth.accessToken)',
      caseId: '#(bootstrap.createdCase.id)',
      expectedVersion: '#(bootstrap.createdCase.version)',
      assignedUnitId: 'JKT-UNIT-1',
      assigneeUserId: 'investigator-jkt',
      reason: 'Assign investigator for Karate evidence flow.'
    }
    """

    Given url baseUrl
    And path 'api', 'v1', 'cases', bootstrap.createdCase.id, 'evidence', 'upload-sessions'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And request
    """
    {
      title: 'Karate evidence ' + suffix,
      classification: 'CONFIDENTIAL',
      originalFilename: 'ledger.csv',
      mediaType: 'text/csv',
      sizeBytes: 20,
      sha256Checksum: '#(checksum)'
    }
    """
    When method post
    Then status 201
    And match response.evidenceId == '#uuid'
    And match response.uploadSessionId == '#uuid'
    And match response.uploadUrl == '#regex http://localhost:9000/.*'
    * def uploadSession = response

    Given url uploadSession.uploadUrl
    And header Content-Type = 'text/csv'
    And request evidenceContent
    When method put
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'evidence', uploadSession.evidenceId, 'versions', 'finalize'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And request { uploadSessionId: '#(uploadSession.uploadSessionId)' }
    When method post
    Then status 200
    And match response.id == uploadSession.evidenceId
    And match response.storageStatus == 'ACTIVE'
    And match response.latestVersion == 1
    And match response.latestVersionMetadata.sha256Checksum == checksum

    Given url baseUrl
    And path 'api', 'v1', 'evidence', uploadSession.evidenceId
    And header Authorization = 'Bearer ' + auditorAuth.accessToken
    When method get
    Then status 200
    And match response.id == uploadSession.evidenceId
    And match response.latestVersionMetadata.originalFilename == 'ledger.csv'

    Given url baseUrl
    And path 'api', 'v1', 'evidence', uploadSession.evidenceId, 'download-sessions'
    And header Authorization = 'Bearer ' + auditorAuth.accessToken
    And request { reason: 'Karate audit verification.' }
    When method post
    Then status 201
    And match response.downloadUrl == '#string'
    And match response.downloadUrl == '#regex http://localhost:9000/.*'
    * def downloadSession = response

    Given url downloadSession.downloadUrl
    When method get
    Then status 200
    And match response == evidenceContent

  Scenario: finalize rejects checksum mismatch
    * def suffix = karate.uuid()
    * def bootstrap = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      suffix: '#(suffix)',
      reportTitle: 'Karate checksum report ' + suffix,
      caseTitle: 'Karate checksum case ' + suffix,
      caseSummary: 'Evidence checksum mismatch regression driven by Karate.'
    }
    """
    * def assignment = call read('classpath:karate/common/assign-case.feature')
    """
    {
      accessToken: '#(triageAuth.accessToken)',
      caseId: '#(bootstrap.createdCase.id)',
      expectedVersion: '#(bootstrap.createdCase.version)',
      assignedUnitId: 'JKT-UNIT-1',
      assigneeUserId: 'investigator-jkt',
      reason: 'Assign investigator for Karate checksum mismatch flow.'
    }
    """

    Given url baseUrl
    And path 'api', 'v1', 'cases', bootstrap.createdCase.id, 'evidence', 'upload-sessions'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And request
    """
    {
      title: 'Karate invalid checksum evidence ' + suffix,
      classification: 'CONFIDENTIAL',
      originalFilename: 'ledger.csv',
      mediaType: 'text/csv',
      sizeBytes: 20,
      sha256Checksum: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
    }
    """
    When method post
    Then status 201
    * def uploadSession = response

    Given url uploadSession.uploadUrl
    And header Content-Type = 'text/csv'
    And request evidenceContent
    When method put
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'evidence', uploadSession.evidenceId, 'versions', 'finalize'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And request { uploadSessionId: '#(uploadSession.uploadSessionId)' }
    When method post
    Then status 409
    And match response.code == 'EVIDENCE_CHECKSUM_MISMATCH'
