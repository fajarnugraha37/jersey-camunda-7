# Transaction Management

## MyBatisTransactionManager

Implements `ApplicationTransactionManager` port interface from `sentinel-application`.

### Key Features
- **Thread-local session propagation** via `MyBatisSessionContext` (ThreadLocal\<SqlSession\>)
- **Nested transaction support** — reuses existing session if already in a transaction
- **Isolation level mapping** — `TransactionIsolation.READ_COMMITTED`, `REPEATABLE_READ`, `SERIALIZABLE`
- **Read-only optimization** — sets `connection.setReadOnly(true/false)`
- **Automatic rollback** on `SQLException`, `RuntimeException`, or `Error`
- **Best-effort cleanup** in `finally` block

### Transaction Flow

```
required(options, work)
  │
  ├─ Check MyBatisSessionContext.currentSession()
  │   ├─ Found → reuse (nested call)
  │   └─ None → open new SqlSession
  │
  ├─ Bind session to ThreadLocal
  ├─ Set isolation level
  ├─ Set readOnly if specified
  │
  ├─ Execute work
  │   ├─ Success → commit, return result
  │   └─ Failure → rollback, throw IllegalStateException
  │
  └─ Finally: clear ThreadLocal, reset readOnly
```

---

## Optimistic Concurrency Control (OCC)

Every mutable aggregate carries a `long version` field.

### Pattern
```sql
UPDATE table SET ..., version = version + 1
WHERE id = ? AND version = ?
```

- If `version` doesn't match → 0 rows updated → `CONCURRENT_MODIFICATION` error
- Client must retry the operation with the current version

### Enforcement Points
| Aggregate | OCC on |
|-----------|--------|
| `Report` | triage, update |
| `CaseRecord` | assign, transition |
| `CaseAssignment` | rotate |
| `Evidence` | activate |
| `EvidenceUploadSession` | finalize |
| `Recommendation` | submit, approve |
| `Decision` | approve, publish |
| `Sanction` | cancel |
| `Appeal` | decide |
| `OutboxEvent` | claim, markPublished, releaseForRetry |

---

## Pessimistic Locking

Used where OCC is insufficient:

| Scenario | Lock Type | SQL |
|----------|-----------|-----|
| Decision editing | Row-level | `SELECT ... FOR UPDATE NOWAIT` |
| Assignment rotation | Row-level | `SELECT ... FOR UPDATE` on case_record |
| Obligation recalculation | Table-level | `LOCK TABLE sanction_obligation IN SHARE ROW EXCLUSIVE MODE NOWAIT` |

---

## Session Propagation

`MyBatisSessionContext` uses `ThreadLocal` to propagate the active `SqlSession`:

```java
// In repository adapter:
MyBatisSessionContext.currentSession().getMapper(CaseMyBatisMapper.class)
```

This allows multiple repository calls within a single transaction to share the same session and connection.

---

## Exception Classification

`PersistenceExceptionClassifier` provides utilities for classifying PostgreSQL errors:

| Method | SQL State | Meaning |
|--------|-----------|---------|
| `isLockNotAvailable()` | `55P03` | Lock not available (NOWAIT) |
| `isUniqueViolation()` | `23505` | Unique constraint violation |

Used by repository adapters to wrap raw `PersistenceException` into domain-specific exceptions.
