Feature: Assign a case

  Scenario:
    * def assignedUnitId = karate.get('assignedUnitId', 'JKT-UNIT-1')
    * def assigneeUserId = karate.get('assigneeUserId', 'investigator-jkt')
    * def reason = karate.get('reason', 'Karate assignment flow.')

    Given url baseUrl
    And path 'api', 'v1', 'cases', caseId, 'assignments'
    And header Authorization = 'Bearer ' + accessToken
    And request
    """
    {
      assignedUnitId: '#(assignedUnitId)',
      assigneeUserId: '#(assigneeUserId)',
      expectedVersion: #(expectedVersion),
      reason: '#(reason)'
    }
    """
    When method post
    Then status 200
    And match response.id == caseId
    And match response.assignedUnitId == assignedUnitId
    And match response.assigneeUserId == assigneeUserId
    * def assignedCase = response
