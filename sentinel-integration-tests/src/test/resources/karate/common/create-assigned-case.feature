Feature: Bootstrap a case and assign it to a unit and assignee

  Scenario:
    * def suffix = karate.get('suffix', karate.uuid())
    * def assignedUnitId = karate.get('assignedUnitId', 'JKT-UNIT-1')
    * def assigneeUserId = karate.get('assigneeUserId', 'investigator-jkt')
    * def reason = karate.get('reason', 'Assign case during Karate helper flow.')
    * def bootstrap = call read('classpath:karate/common/create-case-bootstrap.feature')
    """
    {
      intakeToken: '#(intakeToken)',
      triageToken: '#(triageToken)',
      suffix: '#(suffix)',
      reportTitle: '#(karate.get("reportTitle", "Karate assigned-case report " + suffix))',
      caseTitle: '#(karate.get("caseTitle", "Karate assigned-case " + suffix))',
      caseSummary: '#(karate.get("caseSummary", "Assigned case helper flow."))',
      classification: '#(karate.get("classification", "CONFIDENTIAL"))'
    }
    """
    * def assignmentResult = call read('classpath:karate/common/assign-case.feature')
    """
    {
      accessToken: '#(triageToken)',
      caseId: '#(bootstrap.createdCase.id)',
      expectedVersion: '#(bootstrap.createdCase.version)',
      assignedUnitId: '#(assignedUnitId)',
      assigneeUserId: '#(assigneeUserId)',
      reason: '#(reason)'
    }
    """
    * def assignedCase = assignmentResult.assignedCase
