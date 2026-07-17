Feature: Drive workflow until a published decision exists for a case

  Scenario:
    * def suffix = karate.get('suffix', karate.uuid())
    * def violationProven = karate.get('violationProven', false)
    * def appealDeadline = karate.get('appealDeadline', '2026-08-01')
    * def reportTitle = karate.get('reportTitle', 'Karate published-decision report ' + suffix)
    * def caseTitle = karate.get('caseTitle', 'Karate published-decision case ' + suffix)
    * def caseSummary = karate.get('caseSummary', 'Published decision bootstrap flow for Karate.')
    * def recommendationTitle = karate.get('recommendationTitle', 'Karate recommendation ' + suffix)
    * def recommendationSummary = karate.get('recommendationSummary', 'Recommendation prepared during published-decision bootstrap.')
    * def proposedDecision = karate.get('proposedDecision', 'Proceed to formal decision.')
    * def proposedSanction = karate.get('proposedSanction', violationProven ? 'Impose corrective sanction.' : null)
    * def decisionTitle = karate.get('decisionTitle', 'Karate decision ' + suffix)
    * def decisionSummary = karate.get('decisionSummary', 'Decision published during Karate bootstrap.')
    * def sanctionSummary = karate.get('sanctionSummary', violationProven ? 'Formal sanction imposed.' : null)
    * def obligationTitle = karate.get('obligationTitle', violationProven ? 'Submit remediation report' : null)
    * def obligationDetails = karate.get('obligationDetails', violationProven ? 'Provide written remediation evidence.' : null)
    * def obligationDueDate = karate.get('obligationDueDate', violationProven ? '2026-08-15' : null)

    * def bootstrap = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeToken)',
      triageToken: '#(triageToken)',
      suffix: '#(suffix)',
      reportTitle: '#(reportTitle)',
      caseTitle: '#(caseTitle)',
      caseSummary: '#(caseSummary)'
    }
    """
    * def caseId = bootstrap.createdCase.id

    * def triageTaskLookup = call read('classpath:karate/common/list-single-task.feature') { accessToken: '#(triageToken)', caseId: '#(caseId)' }
    Given url baseUrl
    And path 'api', 'v1', 'tasks', triageTaskLookup.task.taskId, 'claim'
    And header Authorization = 'Bearer ' + triageToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'tasks', triageTaskLookup.task.taskId, 'complete'
    And header Authorization = 'Bearer ' + triageToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 204

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId
    And header Authorization = 'Bearer ' + triageToken
    When method get
    Then status 200
    And match response.status == 'UNDER_INVESTIGATION'
    * def underInvestigationCase = response

    * def assignment = call read('classpath:karate/common/assign-case.feature')
    """
    {
      accessToken: '#(triageToken)',
      caseId: '#(caseId)',
      expectedVersion: '#(underInvestigationCase.version)',
      assignedUnitId: 'JKT-UNIT-1',
      assigneeUserId: 'investigator-jkt',
      reason: 'Assign investigator during published-decision bootstrap.'
    }
    """

    * def investigationTaskLookup = call read('classpath:karate/common/list-single-task.feature') { accessToken: '#(investigatorToken)', caseId: '#(caseId)' }
    Given url baseUrl
    And path 'api', 'v1', 'tasks', investigationTaskLookup.task.taskId, 'claim'
    And header Authorization = 'Bearer ' + investigatorToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'recommendations'
    And header Authorization = 'Bearer ' + investigatorToken
    And request
    """
    {
      title: '#(recommendationTitle)',
      summary: '#(recommendationSummary)',
      proposedDecision: '#(proposedDecision)',
      proposedSanction: #(proposedSanction)
    }
    """
    When method post
    Then status 201
    * def recommendation = response

    Given url baseUrl
    And path 'api', 'v1', 'recommendations', recommendation.id, 'submit'
    And header Authorization = 'Bearer ' + investigatorToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'tasks', investigationTaskLookup.task.taskId, 'complete'
    And header Authorization = 'Bearer ' + investigatorToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 204

    * def reviewTaskLookup = call read('classpath:karate/common/list-single-task.feature') { accessToken: '#(reviewerToken)', caseId: '#(caseId)' }
    Given url baseUrl
    And path 'api', 'v1', 'tasks', reviewTaskLookup.task.taskId, 'claim'
    And header Authorization = 'Bearer ' + reviewerToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'recommendations', recommendation.id, 'reviews'
    And header Authorization = 'Bearer ' + reviewerToken
    And request { reviewSummary: 'Published-decision bootstrap review approved.' }
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'tasks', reviewTaskLookup.task.taskId, 'complete'
    And header Authorization = 'Bearer ' + reviewerToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 204

    * def decisionTaskLookup = call read('classpath:karate/common/list-single-task.feature') { accessToken: '#(decisionToken)', caseId: '#(caseId)' }
    Given url baseUrl
    And path 'api', 'v1', 'tasks', decisionTaskLookup.task.taskId, 'claim'
    And header Authorization = 'Bearer ' + decisionToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'decisions'
    And header Authorization = 'Bearer ' + decisionToken
    And request
    """
    {
      title: '#(decisionTitle)',
      summary: '#(decisionSummary)',
      violationProven: #(violationProven),
      sanctionSummary: #(sanctionSummary),
      obligationTitle: #(obligationTitle),
      obligationDetails: #(obligationDetails),
      obligationDueDate: #(obligationDueDate),
      appealDeadline: '#(appealDeadline)'
    }
    """
    When method post
    Then status 201
    * def decision = response

    Given url baseUrl
    And path 'api', 'v1', 'decisions', decision.id, 'approve'
    And header Authorization = 'Bearer ' + supervisorToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'decisions', decision.id, 'publish'
    And header Authorization = 'Bearer ' + decisionToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 200

    Given url baseUrl
    And path 'api', 'v1', 'tasks', decisionTaskLookup.task.taskId, 'complete'
    And header Authorization = 'Bearer ' + decisionToken
    And header Content-Type = 'application/json'
    And request ''
    When method post
    Then status 204

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId
    And header Authorization = 'Bearer ' + decisionToken
    When method get
    Then status 200
    And match response.status == 'DECIDED'
    * def decidedCase = response
