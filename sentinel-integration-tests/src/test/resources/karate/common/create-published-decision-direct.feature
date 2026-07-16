Feature: Create and publish a decision using workflow-compatible helper steps

  Scenario:
    * def suffix = karate.get('suffix', karate.uuid())
    * def reportTitle = karate.get('reportTitle', 'Karate published-decision report ' + suffix)
    * def caseTitle = karate.get('caseTitle', 'Karate published-decision case ' + suffix)
    * def caseSummary = karate.get('caseSummary', 'Published decision helper flow.')
    * def recommendationTitle = karate.get('recommendationTitle', 'Recommendation for ' + suffix)
    * def recommendationSummary = karate.get('recommendationSummary', 'Investigation summary for published decision helper.')
    * def proposedDecision = karate.get('proposedDecision', 'Proceed to formal decision.')
    * def proposedSanction = karate.get('proposedSanction', null)
    * def violationProven = karate.get('violationProven', false)
    * def decisionTitle = karate.get('decisionTitle', 'Decision for ' + suffix)
    * def decisionSummary = karate.get('decisionSummary', 'Decision summary for published decision helper.')
    * def sanctionSummary = karate.get('sanctionSummary', null)
    * def obligationTitle = karate.get('obligationTitle', null)
    * def obligationDetails = karate.get('obligationDetails', null)
    * def obligationDueDate = karate.get('obligationDueDate', null)
    * def appealDeadline = karate.get('appealDeadline', '2026-08-01')
    * def publishedFlow = call read('classpath:karate/common/create-published-decision.feature')
    """
    {
      intakeToken: '#(intakeToken)',
      triageToken: '#(triageToken)',
      investigatorToken: '#(investigatorToken)',
      reviewerToken: '#(reviewerToken)',
      decisionToken: '#(decisionToken)',
      supervisorToken: '#(supervisorToken)',
      suffix: '#(suffix)',
      reportTitle: '#(reportTitle)',
      caseTitle: '#(caseTitle)',
      caseSummary: '#(caseSummary)',
      recommendationTitle: '#(recommendationTitle)',
      recommendationSummary: '#(recommendationSummary)',
      proposedDecision: '#(proposedDecision)',
      proposedSanction: #(proposedSanction),
      decisionTitle: '#(decisionTitle)',
      decisionSummary: '#(decisionSummary)',
      violationProven: #(violationProven),
      sanctionSummary: #(sanctionSummary),
      obligationTitle: #(obligationTitle),
      obligationDetails: #(obligationDetails),
      obligationDueDate: #(obligationDueDate),
      appealDeadline: '#(appealDeadline)'
    }
    """
    * def draftFlow = publishedFlow
    * def decision = publishedFlow.decision
    * def approvedDecision = publishedFlow.decision
    * def publishedDecision = publishedFlow.decision
    * def decidedCase = publishedFlow.decidedCase
