@full
@relationships
Feature: Case relationships expose recursive lineage and reject duplicate or cyclic links

  Background:
    * def intakeAuth = callonce read('classpath:karate/common/auth.feature') { username: 'intake-jkt' }
    * def triageAuth = callonce read('classpath:karate/common/auth.feature') { username: 'triage-jkt' }
    * def DbSupport = Java.type('com.sentinel.enforcement.integration.karate.support.LiveDbSupport')

  Scenario: descendants and ancestors support recursion, filters, and conflict paths
    * def root = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: 'Root relationship case',
      caseSummary: 'Parent case for recursive lineage coverage.'
    }
    """
    * def middle = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: 'Middle relationship case',
      caseSummary: 'Intermediate case for recursive lineage coverage.'
    }
    """
    * def leaf = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeAuth.accessToken)',
      triageToken: '#(triageAuth.accessToken)',
      caseTitle: 'Leaf relationship case',
      caseSummary: 'Child case for recursive lineage coverage.'
    }
    """

    * def relationshipCountBefore = DbSupport.caseRelationshipCount()

    Given url baseUrl
    And path 'api', 'v1', 'cases', root.createdCase.id, 'relationships'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request
    """
    {
      relatedCaseId: '#(middle.createdCase.id)',
      relationshipType: 'MERGE',
      direction: 'PARENT_OF',
      relationshipReason: 'Merge related investigations.'
    }
    """
    When method post
    Then status 201

    Given url baseUrl
    And path 'api', 'v1', 'cases', middle.createdCase.id, 'relationships'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request
    """
    {
      relatedCaseId: '#(leaf.createdCase.id)',
      relationshipType: 'DERIVATION',
      direction: 'PARENT_OF',
      relationshipReason: 'Derive follow-up case from merged matter.'
    }
    """
    When method post
    Then status 201

    Given url baseUrl
    And path 'api', 'v1', 'cases', root.createdCase.id, 'relationships'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And param direction = 'DESCENDANTS'
    And param maxDepth = 5
    When method get
    Then status 200
    And match response.items == '#[2]'
    And match response.items[0].relatedCaseId == middle.createdCase.id
    And match response.items[0].depth == 1
    And match response.items[0].pathCaseIds[0] == root.createdCase.id
    And match response.items[0].pathCaseIds[1] == middle.createdCase.id
    And match response.items[1].relatedCaseId == leaf.createdCase.id
    And match response.items[1].depth == 2
    And match response.items[1].pathCaseIds[0] == root.createdCase.id
    And match response.items[1].pathCaseIds[1] == middle.createdCase.id
    And match response.items[1].pathCaseIds[2] == leaf.createdCase.id

    Given url baseUrl
    And path 'api', 'v1', 'cases', leaf.createdCase.id, 'relationships'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And param direction = 'ANCESTORS'
    And param maxDepth = 5
    When method get
    Then status 200
    And match response.items == '#[2]'
    And match response.items[0].relatedCaseId == middle.createdCase.id
    And match response.items[0].depth == 1
    And match response.items[0].pathCaseIds[0] == leaf.createdCase.id
    And match response.items[0].pathCaseIds[1] == middle.createdCase.id
    And match response.items[1].relatedCaseId == root.createdCase.id
    And match response.items[1].depth == 2
    And match response.items[1].pathCaseIds[0] == leaf.createdCase.id
    And match response.items[1].pathCaseIds[1] == middle.createdCase.id
    And match response.items[1].pathCaseIds[2] == root.createdCase.id

    Given url baseUrl
    And path 'api', 'v1', 'cases', root.createdCase.id, 'relationships'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And param direction = 'DESCENDANTS'
    And param maxDepth = 5
    And param relationshipType = 'MERGE'
    When method get
    Then status 200
    And match response.items == '#[1]'
    And match response.items[0].relatedCaseId == middle.createdCase.id

    Given url baseUrl
    And path 'api', 'v1', 'cases', root.createdCase.id, 'relationships'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And param direction = 'DESCENDANTS'
    And param maxDepth = 1
    When method get
    Then status 200
    And match response.items == '#[1]'
    And match response.items[0].relatedCaseId == middle.createdCase.id

    Given url baseUrl
    And path 'api', 'v1', 'cases', root.createdCase.id, 'relationships'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request
    """
    {
      relatedCaseId: '#(middle.createdCase.id)',
      relationshipType: 'MERGE',
      direction: 'PARENT_OF',
      relationshipReason: 'Duplicate relationship should be rejected.'
    }
    """
    When method post
    Then status 409
    And match response.code == 'CASE_RELATIONSHIP_ALREADY_EXISTS'

    Given url baseUrl
    And path 'api', 'v1', 'cases', leaf.createdCase.id, 'relationships'
    And header Authorization = 'Bearer ' + triageAuth.accessToken
    And request
    """
    {
      relatedCaseId: '#(root.createdCase.id)',
      relationshipType: 'SPLIT',
      direction: 'PARENT_OF',
      relationshipReason: 'Cycle should be rejected.'
    }
    """
    When method post
    Then status 409
    And match response.code == 'CASE_RELATIONSHIP_CYCLE'

    * match DbSupport.caseRelationshipCount() == relationshipCountBefore + 2
