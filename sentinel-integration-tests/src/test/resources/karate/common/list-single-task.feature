Feature: Fetch exactly one task for a case

  Scenario:
    Given url baseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = 'Bearer ' + accessToken
    And param caseId = caseId
    And param limit = 10
    And configure retry = { count: 120, interval: 500 }
    And retry until responseStatus == 200 && response.items.length == 1
    When method get
    Then status 200
    And match response.items == '#[1]'
    * def task = response.items[0]
