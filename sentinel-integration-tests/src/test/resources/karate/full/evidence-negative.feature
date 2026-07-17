@full
@evidence-negative
Feature: Evidence negative paths remain enforced on the running application

  Background:
    * def intakeAuth = callonce read('classpath:karate/common/auth.feature') { username: 'intake-jkt' }
    * def triageAuth = callonce read('classpath:karate/common/auth.feature') { username: 'triage-jkt' }
    * def investigatorAuth = callonce read('classpath:karate/common/auth.feature') { username: 'investigator-jkt' }
    * def DbSupport = Java.type('com.sentinel.enforcement.integration.karate.support.LiveDbSupport')
    * def evidenceContent = 'gift,amount\nbook,100'
    * def checksum = '041cbe1ae81b0a15679537491891a60ffef030f0dda0c4ecf43e691e327e92f3'

  Scenario: unauthorized download session is rejected and audited
    * def assignedFlow = call read('classpath:karate/common/create-assigned-case.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: 'Evidence authorization case',
      caseSummary: 'Used for unauthorized download coverage.'
    }
    """
    * def caseId = assignedFlow.bootstrap.createdCase.id

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'evidence', 'upload-sessions'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And request
    """
    {
      title: 'Gift ledger export',
      classification: 'CONFIDENTIAL',
      originalFilename: 'ledger.csv',
      mediaType: 'text/csv',
      sizeBytes: 20,
      sha256Checksum: '#(checksum)'
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
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'evidence', uploadSession.evidenceId, 'download-sessions'
    And header Authorization = 'Bearer ' + intakeAuth.accessToken
    And request { reason: 'Not allowed.' }
    When method post
    Then status 403
    And match response.code == 'FORBIDDEN'

    * match DbSupport.countAuditEventsByType(caseId, 'EvidenceDownloadDenied') == 1

  Scenario: duplicate finalize is rejected after the first successful finalize
    * def assignedFlow = call read('classpath:karate/common/create-assigned-case.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: 'Evidence duplicate finalize case',
      caseSummary: 'Finalize must be idempotent-safe.'
    }
    """
    * def caseId = assignedFlow.bootstrap.createdCase.id

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'evidence', 'upload-sessions'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And request
    """
    {
      title: 'Gift ledger export',
      classification: 'CONFIDENTIAL',
      originalFilename: 'ledger.csv',
      mediaType: 'text/csv',
      sizeBytes: 20,
      sha256Checksum: '#(checksum)'
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
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'evidence', uploadSession.evidenceId, 'versions', 'finalize'
    And header Authorization = 'Bearer ' + investigatorAuth.accessToken
    And request { uploadSessionId: '#(uploadSession.uploadSessionId)' }
    When method post
    Then status 409
    And match response.code == 'EVIDENCE_UPLOAD_SESSION_STALE'
