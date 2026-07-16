Feature: Fetch exactly one task for a case

  Scenario:
    Given url baseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = 'Bearer ' + accessToken
    And param caseId = caseId
    And param limit = 10
    When method get
    Then status 200
    And match response.items == '#[1]'
    * def task = response.items[0]
