# Database Schema

PostgreSQL database with 23 tables, managed via Liquibase migrations.

---

## Entity Relationship Summary

```
report ──→ case_record ──→ case_assignment
                       ├──→ case_status_history
                       ├──→ case_relationship (parent/child)
                       ├──→ audit_event
                       ├──→ workflow_instance
                       ├──→ evidence ──→ evidence_version
                       │            └──→ evidence_upload_session
                       ├──→ notification
                       ├──→ recommendation ──→ recommendation_review
                       ├──→ decision ──→ decision_version
                       │          └──→ sanction ──→ sanction_obligation
                       └──→ appeal ──→ appeal_decision

autonomous:
  case_number_sequence
  outbox_event
  inbox_event
  maintenance_operation_run
```

---

## Table Definitions

### 1. `report`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| title | VARCHAR(255) | NOT NULL |
| description | TEXT | NOT NULL |
| jurisdiction_code | VARCHAR(10) | NOT NULL |
| reporter_name | VARCHAR(255) | NOT NULL |
| status | VARCHAR(32) | CHECK (SUBMITTED, TRIAGED) |
| created_at | TIMESTAMPTZ | NOT NULL |
| created_by | VARCHAR(255) | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |
| updated_by | VARCHAR(255) | NOT NULL |
| version | BIGINT | NOT NULL, CHECK >= 0 |

**Indexes:** (jurisdiction_code, created_at), (jurisdiction_code, status, created_at)

### 2. `case_number_sequence`
| Column | Type | Constraints |
|--------|------|-------------|
| jurisdiction_code | VARCHAR(10) | PK |
| calendar_year | INT | PK |
| next_value | BIGINT | NOT NULL |

**Function:** `generate_case_number(jurisdiction, type, year)` → atomic increment

### 3. `case_record`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| case_number | VARCHAR(64) | UNIQUE NOT NULL |
| report_id | UUID | FK → report(id) |
| title | VARCHAR(255) | NOT NULL |
| summary | TEXT | NOT NULL |
| jurisdiction_code | VARCHAR(10) | NOT NULL |
| classification | VARCHAR(32) | DEFAULT 'CONFIDENTIAL', CHECK (PUBLIC, CONFIDENTIAL, SECRET) |
| status | VARCHAR(32) | NOT NULL, CHECK (10 values) |
| assigned_unit_id | VARCHAR(64) | |
| assignee_user_id | VARCHAR(255) | |
| created_at | TIMESTAMPTZ | NOT NULL |
| created_by | VARCHAR(255) | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |
| updated_by | VARCHAR(255) | NOT NULL |
| version | BIGINT | NOT NULL |

**Indexes:** (jurisdiction_code, assigned_unit_id, classification, created_at)

### 4. `case_assignment`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| case_id | UUID | FK → case_record(id) |
| assigned_unit_id | VARCHAR(64) | |
| assignee_user_id | VARCHAR(255) | |
| assignment_reason | TEXT | |
| assigned_at | TIMESTAMPTZ | |
| assigned_by | VARCHAR(255) | |
| released_at | TIMESTAMPTZ | |
| released_by | VARCHAR(255) | |
| superseded_by_assignment_id | UUID | |
| is_active | BOOLEAN | NOT NULL DEFAULT true |
| active_case_id | UUID | UNIQUE (deferred) |
| created_at | TIMESTAMPTZ | |
| created_by | VARCHAR(255) | |
| updated_at | TIMESTAMPTZ | |
| updated_by | VARCHAR(255) | |
| version | BIGINT | |

### 5. `case_status_history`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| case_id | UUID | FK → case_record(id) |
| from_status | VARCHAR(32) | |
| to_status | VARCHAR(32) | NOT NULL |
| transition_reason | TEXT | |
| transitioned_at | TIMESTAMPTZ | |
| transitioned_by | VARCHAR(255) | |

### 6. `case_relationship`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| parent_case_id | UUID | FK → case_record(id) |
| child_case_id | UUID | FK → case_record(id) |
| relationship_type | VARCHAR(32) | CHECK (MERGE, DERIVATION, SPLIT) |
| relationship_reason | TEXT | |
| created_at | TIMESTAMPTZ | |
| created_by | VARCHAR(255) | |
| updated_at | TIMESTAMPTZ | |
| updated_by | VARCHAR(255) | |
| version | BIGINT | |

**Constraints:** NOT self-reference, UNIQUE (parent_case_id, child_case_id, relationship_type)

### 7. `audit_event`
| Column | Type | Constraints |
|--------|------|-------------|
| event_id | UUID | PK |
| event_type | VARCHAR(64) | |
| actor_type | VARCHAR(32) | |
| actor_id | VARCHAR(255) | |
| actor_roles | TEXT | |
| action | VARCHAR(64) | |
| resource_type | VARCHAR(32) | |
| resource_id | VARCHAR(255) | |
| case_id | UUID | FK → case_record(id) |
| timestamp | TIMESTAMPTZ | |
| correlation_id | VARCHAR(64) | |
| source_ip | VARCHAR(45) | |
| result | VARCHAR(16) | |
| reason | TEXT | |
| before_summary | TEXT | |
| after_summary | TEXT | |
| metadata | TEXT | |

### 8. `workflow_instance`
| Column | Type | Constraints |
|--------|------|-------------|
| case_id | UUID | PK (with workflow_type) |
| workflow_type | VARCHAR(32) | PK, CHECK (CASE_MAIN, APPEAL) |
| process_instance_id | VARCHAR(64) | UNIQUE |
| process_definition_id | VARCHAR(64) | |
| process_definition_version | INTEGER | |
| business_key | VARCHAR(255) | |
| status | VARCHAR(32) | CHECK (ACTIVE, COMPLETED, CANCELLED) |
| created_at | TIMESTAMPTZ | |
| updated_at | TIMESTAMPTZ | |

### 9. `evidence`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| case_id | UUID | FK → case_record(id) |
| title | VARCHAR(255) | |
| classification | VARCHAR(32) | CHECK (PUBLIC, CONFIDENTIAL, SECRET) |
| storage_status | VARCHAR(32) | CHECK (PENDING_UPLOAD, ACTIVE) |
| latest_version | INTEGER | |
| created_at | TIMESTAMPTZ | |
| created_by | VARCHAR(255) | |
| updated_at | TIMESTAMPTZ | |
| updated_by | VARCHAR(255) | |
| version | BIGINT | |

### 10. `evidence_upload_session`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| case_id | UUID | FK |
| evidence_id | UUID | FK |
| target_version_number | INT | |
| original_filename | VARCHAR(512) | |
| generated_filename | VARCHAR(512) | |
| bucket | VARCHAR(255) | |
| object_key | VARCHAR(1024) | |
| media_type | VARCHAR(127) | |
| size_bytes | BIGINT | |
| sha256_checksum | CHAR(64) | CHECK regex |
| classification | VARCHAR(32) | |
| status | VARCHAR(32) | CHECK (PENDING, FINALIZED) |
| expires_at | TIMESTAMPTZ | |
| created_at | TIMESTAMPTZ | |
| created_by | VARCHAR(255) | |
| updated_at | TIMESTAMPTZ | |
| updated_by | VARCHAR(255) | |
| version | BIGINT | |

### 11. `evidence_version`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| evidence_id | UUID | FK |
| version_number | INT | |
| original_filename | VARCHAR(512) | |
| generated_filename | VARCHAR(512) | |
| bucket | VARCHAR(255) | |
| object_key | VARCHAR(1024) | UNIQUE |
| media_type | VARCHAR(127) | |
| size_bytes | BIGINT | |
| sha256_checksum | CHAR(64) | |
| uploaded_at | TIMESTAMPTZ | |
| uploaded_by | VARCHAR(255) | |

### 12. `outbox_event`
| Column | Type | Constraints |
|--------|------|-------------|
| event_id | UUID | PK |
| topic | VARCHAR(255) | |
| message_key | VARCHAR(255) | |
| event_type | VARCHAR(128) | |
| event_version | INT | |
| aggregate_type | VARCHAR(64) | |
| aggregate_id | UUID | |
| occurred_at | TIMESTAMPTZ | |
| correlation_id | VARCHAR(64) | |
| causation_id | VARCHAR(64) | |
| actor_type | VARCHAR(32) | |
| actor_id | VARCHAR(255) | |
| payload_json | JSONB | |
| status | VARCHAR(16) | CHECK (PENDING, PUBLISHED) |
| available_at | TIMESTAMPTZ | |
| lease_owner | VARCHAR(255) | |
| lease_expires_at | TIMESTAMPTZ | |
| publish_attempts | INT | |
| last_error | VARCHAR(500) | |
| published_at | TIMESTAMPTZ | |
| version | BIGINT | |

### 13. `inbox_event`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| consumer_name | VARCHAR(255) | UNIQUE with event_id |
| event_id | UUID | UNIQUE with consumer_name |
| topic | VARCHAR(255) | |
| processed_at | TIMESTAMPTZ | |
| result_reference | UUID | |
| version | BIGINT | |

### 14. `notification`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| case_id | UUID | FK (nullable) |
| consumer_name | VARCHAR(255) | |
| event_id | UUID | UNIQUE with consumer_name |
| notification_type | VARCHAR(64) | |
| title | TEXT | |
| body | TEXT | |
| status | VARCHAR(16) | CHECK (GENERATED, SENT, FAILED) |
| created_at | TIMESTAMPTZ | |
| updated_at | TIMESTAMPTZ | |
| version | BIGINT | |

### 15. `recommendation`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| case_id | UUID | UNIQUE, FK |
| title | VARCHAR(255) | |
| summary | TEXT | |
| proposed_decision | TEXT | |
| proposed_sanction | TEXT | |
| status | VARCHAR(32) | CHECK (DRAFT, SUBMITTED, APPROVED) |
| submitted_at | TIMESTAMPTZ | |
| submitted_by | VARCHAR(255) | |
| approved_review_id | UUID | |
| version | BIGINT | |

### 16. `recommendation_review`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| recommendation_id | UUID | FK |
| outcome | VARCHAR(32) | CHECK (APPROVED) |
| review_summary | TEXT | |
| reviewed_at | TIMESTAMPTZ | |
| reviewed_by | VARCHAR(255) | |
| version | BIGINT | |

### 17. `decision`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| case_id | UUID | UNIQUE, FK |
| recommendation_id | UUID | FK |
| title | VARCHAR(255) | |
| summary | TEXT | |
| violation_proven | BOOLEAN | |
| sanction_summary | TEXT | |
| obligation_title | VARCHAR(255) | |
| obligation_details | TEXT | |
| obligation_due_date | DATE | |
| appeal_deadline | DATE | |
| status | VARCHAR(32) | CHECK (DRAFT, APPROVED, PUBLISHED) |
| approved_at | TIMESTAMPTZ | |
| approved_by | VARCHAR(255) | |
| published_at | TIMESTAMPTZ | |
| published_by | VARCHAR(255) | |
| version | BIGINT | |

### 18. `decision_version`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| decision_id | UUID | FK |
| version_number | INT | UNIQUE per decision |
| (full decision snapshot) | | All decision fields copied |
| published_at | TIMESTAMPTZ | |
| published_by | VARCHAR(255) | |

### 19. `sanction`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| case_id | UUID | FK |
| decision_id | UUID | UNIQUE, FK |
| summary | TEXT | |
| status | VARCHAR(32) | CHECK (ACTIVE, CANCELLED) |
| version | BIGINT | |

### 20. `sanction_obligation`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| sanction_id | UUID | UNIQUE, FK |
| title | VARCHAR(255) | |
| details | TEXT | |
| due_date | DATE | |
| status | VARCHAR(32) | CHECK (ACTIVE, OVERDUE, SATISFIED, CANCELLED) |
| version | BIGINT | |

### 21. `appeal`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| case_id | UUID | FK |
| decision_id | UUID | UNIQUE, FK |
| rationale | TEXT | |
| supervisor_override | BOOLEAN | |
| supervisor_override_reason | TEXT | |
| status | VARCHAR(32) | CHECK (ACTIVE, DECIDED) |
| submitted_at | TIMESTAMPTZ | |
| submitted_by | VARCHAR(255) | |
| decided_by_appeal_decision_id | UUID | |
| version | BIGINT | |

### 22. `appeal_decision`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| appeal_id | UUID | UNIQUE, FK |
| outcome | VARCHAR(32) | CHECK (DENIED, GRANTED) |
| summary | TEXT | |
| decided_at | TIMESTAMPTZ | |
| decided_by | VARCHAR(255) | |
| version | BIGINT | |

### 23. `maintenance_operation_run`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| operation_name | VARCHAR(128) | |
| requested_by | VARCHAR(255) | |
| requested_at | TIMESTAMPTZ | |
| completed_at | TIMESTAMPTZ | |
| effective_date | DATE | |
| result_status | VARCHAR(32) | CHECK (RUNNING, COMPLETED, FAILED) |
| affected_rows | BIGINT | |
| details_json | JSONB | |
