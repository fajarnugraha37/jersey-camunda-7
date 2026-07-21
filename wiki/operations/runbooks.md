# Operations Runbooks

Operational runbooks for recovery and maintenance.

All runbooks are located in [`docs/runbooks/`](../../docs/runbooks/).

---

| Runbook | Purpose |
|---------|---------|
| [Camunda Embedded Schema Migration](docs/runbooks/camunda-embedded-schema-migration.md) | How Camunda engine schema is migrated alongside application schema |
| [Dead Letter Events](docs/runbooks/dead-letter-events.md) | Investigating and replaying events stuck in DLQ topics |
| [Domain-Workflow Mismatch Reconciliation](docs/runbooks/domain-workflow-mismatch-reconciliation.md) | Manual steps when auto-reconciliation cannot resolve mismatches |
| [Kafka Backlog](docs/runbooks/kafka-backlog.md) | Handling Kafka consumer lag and backlog accumulation |
| [MinIO Evidence Storage](docs/runbooks/minio-evidence-storage.md) | Evidence storage operations, bucket maintenance, object recovery |
| [Stuck Outbox Recovery](docs/runbooks/outbox-stuck.md) | Investigating and resolving stuck outbox events |

---

## Quick Reference

### Check Outbox Health
```sql
SELECT status, COUNT(*), MIN(created_at), MAX(created_at)
FROM outbox_event
GROUP BY status;
```

### Check Consumer Lag
```bash
make kafka-topics
# Look for offset lag in notification consumer group
```

### Reconcile Workflow
```bash
# Via API
curl -X GET http://localhost:8080/api/v1/workflow-reconciliation \
  -H "Authorization: Bearer $SUPERVISOR_TOKEN"
```

### Check Dead Letter Topics
```bash
docker compose exec kafka \
  kafka-console-consumer --bootstrap-server localhost:29092 \
  --topic case.lifecycle.v1.dlq --from-beginning \
  --max-messages 10
```
