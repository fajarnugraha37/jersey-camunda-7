Feature: Run the full live Karate suite sequentially against the running application

  Scenario:
    * call read('classpath:karate/full/platform-auth-report.feature')
    * call read('classpath:karate/full/case-query-and-audit.feature')
    * call read('classpath:karate/full/case-state-and-authorization.feature')
    * call read('classpath:karate/full/case-relationships.feature')
    * call read('classpath:karate/full/evidence-negative.feature')
    * call read('classpath:karate/full/decision-locking.feature')
    * call read('classpath:karate/full/workflow-task-advanced.feature')
    * call read('classpath:karate/full/workflow-reconciliation-advanced.feature')
    * call read('classpath:karate/full/messaging-observable.feature')
