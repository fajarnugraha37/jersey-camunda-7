# Message and Asynchronous Call Traces

This page documents function calls for asynchronous flows: outbox publication, Kafka notification projection, notification delivery, retry/DLQ routing, and workflow task operations.

## Outbox Publisher

Startup:

```text
MessagingRuntime.start()
  → ensureTopicsExist()
  → create Kafka producer/consumer
  → construct KafkaOutboxPublisher
  → start publisher daemon
  → start notification consumer daemon
```

One publisher cycle:

```text
KafkaOutboxPublisher.publishPendingBatch()
  → OutboxRepository.claimPending(leaseOwner, now, leaseDuration, batchSize)
  → for each OutboxEvent:
      EventEnvelopeJsonCodec.serialize()
      → KafkaProducer.send(topic, messageKey, payload).get()
      → OutboxRepository.markPublished(eventId)
    on failure:
      OutboxRepository.releaseForRetry(eventId, nextAttemptAt, error)
      → ensure retry topic exists
```

The publisher uses leases and `FOR UPDATE SKIP LOCKED`; expired leases can be claimed by another instance. Retry delay is exponential, capped at 60 seconds.

## Kafka Consumer Dispatch

```text
KafkaNotificationConsumer.run()
  → KafkaConsumer.poll()
  → processRecord()
  → resolveOriginalTopic()
  → EventEnvelopeJsonCodec.deserialize()
  → if original topic == notification.command.v1:
       NotificationCommandHandler.handle()
    else:
       NotificationEventHandler.handle()
  → consumer.commitSync()
```

The consumer reads domain lifecycle topics, `notification.command.v1`, and their `.retry` topics. It does not auto-consume `.dlq` topics.

## Domain Lifecycle Notification Projection

Supported domain events include:

```text
CaseCreated
CaseAssigned
CaseTransitioned
EvidenceVersionFinalized
DecisionPublished
SanctionCreated
SanctionCancelled
AppealFiled
AppealDecided
```

Call chain:

```text
NotificationEventHandler.handle()
  → EventEnvelope validation / event-type dispatch
  → ApplicationTransactionManager.required()
      → InboxRepository.beginProcessing(consumerName, eventId)
      → if false: return duplicate/no-op
      → NotificationEventHandler.toNotificationRecord()
      → NotificationRepository.save()
      → MessagingEventFactory.notificationCommand()
      → OutboxRepository.enqueue(notification.command.v1)
      → InboxRepository.completeProcessing()
```

The inbox uniqueness key is `(consumer_name, event_id)`. Duplicate delivery is skipped before notification side effects are created.

## Notification Command Delivery

```text
NotificationCommandHandler.handle()
  → NotificationPayload.from(eventEnvelope)
  → NotificationEmailSender.send()
      → JavaMail/SMTP Transport.send()
  → ApplicationTransactionManager.required()
      → NotificationRepository.findById(notificationId)
      → NotificationRepository.save() or updateStatus(SENT)
      → MessagingEventFactory.notificationResult(SENT)
      → OutboxRepository.enqueue(notification.result.v1)
      → InboxRepository.completeProcessing()
```

Important implementation detail: SMTP delivery occurs before the database transaction records `SENT`. If the email succeeds but database persistence fails, a later retry can send the email again.

## Permanent Notification Failure

```text
KafkaNotificationConsumer.handleFailure()
  → inspect x-retry-attempt
  → if attempts remain:
      KafkaProducer.send(originalTopic.retry)
    else:
      KafkaProducer.send(originalTopic.dlq)
      → NotificationCommandHandler.markPermanentFailure()
          → ApplicationTransactionManager.required()
              → InboxRepository.beginProcessing()
              → NotificationRepository.save() or updateStatus(FAILED)
              → MessagingEventFactory.notificationResult(FAILED)
              → OutboxRepository.enqueue(notification.result.v1)
              → InboxRepository.completeProcessing()
  → commit original Kafka offset after routing
```

Retry/DLQ headers:

| Header | Meaning |
|--------|---------|
| `x-original-topic` | Base topic before retry suffix |
| `x-retry-attempt` | Current retry number |
| `x-error` | Exception class name |

## Event Construction

`MessagingEventFactory` creates event envelopes for:

```text
MessagingEventFactory.notificationCommand()
MessagingEventFactory.notificationResult()
MessagingEventFactory.auditIntegrated()
```

The envelope carries event ID/type/version, aggregate identity, timestamps, correlation/causation IDs, actor, and payload.

## Workflow Message Calls

### Start Case Workflow

```text
CaseApplicationService.createCase()
  → CaseWorkflowPort.startCaseWorkflow()
  → CamundaCaseWorkflowAdapter.startCaseWorkflow()
  → RuntimeService.startProcessInstanceByMessage(CaseCreatedMessage)
  → WorkflowInstanceStore.saveStarted()
```

If workflow correlation persistence fails, the adapter attempts to delete the process instance as compensation.

### Start Appeal Workflow

```text
AppealApplicationService.createAppeal()
  → CaseWorkflowPort.startAppealWorkflow()
  → CamundaCaseWorkflowAdapter.startAppealWorkflow()
  → RuntimeService.startProcessInstanceByMessage(AppealWorkflowStarted)
  → WorkflowInstanceStore.saveStarted()
```

### Claim Task

```text
WorkflowTaskApplicationService.claimTask()
  → CaseWorkflowPort.findActiveTask()
  → CaseRepository.findById()
  → visibility/authorization checks
  → CaseWorkflowPort.claimTask()
  → CamundaCaseWorkflowAdapter.claimTask()
  → TaskService.claim(taskId, username)
  → reload task
```

### Complete Task

```text
WorkflowTaskApplicationService.completeTask()
  → CaseWorkflowPort.findActiveTask()
  → prerequisite checks
  → CaseApplicationService.transitionCase() [when task maps to domain transition]
  → CaseWorkflowPort.completeTask()
  → CamundaCaseWorkflowAdapter.completeTask()
  → TaskService.complete(taskId, variables)
  → if process ended: WorkflowInstanceStore.markCompleted()
```

If the active task is absent, `isTaskCompleted()` is checked so duplicate completion can return idempotently.

### Appeal Correlation

```text
AppealApplicationService
  → CaseWorkflowPort.correlateAppealFiled()
  → CamundaCaseWorkflowAdapter.correlateCaseMessage()
  → RuntimeService.createMessageCorrelation(AppealFiled)
  → correlateWithResult()
```

Similarly, appeal resolution calls `correlateAppealResolved()`. A `MismatchingMessageCorrelationException` is converted to a `false` result by the adapter.

## Workflow Reconciliation Calls

```text
WorkflowReconciliationApplicationService.reconcileCase()
  → WorkflowReconciliationQueryPort.findCandidateByCaseId()
  → WorkflowAdministrationPort.findActiveProcessInstance()
  → detect issue type
  → AUTO_REPAIR:
      WorkflowInstanceStore.upsert()
    or TERMINATE_RUNTIME:
      WorkflowAdministrationPort.terminateActiveProcessInstance()
      → WorkflowInstanceStore.upsert()
  → ApplicationTransactionManager.required()
      → CaseRepository.appendAuditEvent()
      → OutboxRepository.enqueue(audit.integration.v1)
```

## Failure and Consistency Notes

| Area | Implemented behavior |
|------|----------------------|
| DB event enqueue | Domain transaction writes outbox row before commit |
| Kafka publish | Publisher claims leased rows and retries failures |
| Consumer delivery | Offset is committed after processing/routing |
| Duplicate domain event | Inbox unique key prevents duplicate projection |
| Duplicate notification command | Handler uses notification lookup/upsert; permanent failure also uses inbox |
| DLQ replay | No automatic DLQ consumer/replay flow is present in inspected source |
| Camunda + DB atomicity | Workflow and DB compensation exists, but a shared transaction boundary is not established by source |
