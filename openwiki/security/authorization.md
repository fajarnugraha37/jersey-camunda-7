---
type: Architecture
title: Authorization
description: Multi-axis RoleBasedAuthorizationService for the Sentinel Enforcement Platform — role-permission mapping, jurisdiction scoping, classification clearance, unit scoping, direct assignment, and conflict-of-interest checks.
tags: [sentinel, security, authorization, roles, permissions, access-control]
---

# Authorization

The Sentinel Enforcement Platform uses a **multi-factor Role-Based Access Control (RBAC)** model. Every protected operation is gated by a `Permission` enum value, and access is granted only after passing a six-axis authorization check.

## Authorization Architecture

```mermaid
flowchart TD
    subgraph Check["Authorization Axes (applied in order)"]
        A1[1. Admin Bypass]
        A2[2. Role-Permission Mapping]
        A3[3. Jurisdiction Match]
        A4[4. Classification Clearance]
        A5[5. Conflict-of-Interest]
        A6[6. Assigned Unit Scope]
        A7[7. Direct Assignment<br/>(INVESTIGATOR only)]
    end
    subgraph Result["Result"]
        R1[Access Granted]
        R2[403 Forbidden]
    end

    A1 -->|SYSTEM_ADMIN role| R1
    A1 -->|Not admin| A2
    A2 -->|No matching permission| R2
    A2 -->|Permission found| A3
    A3 -->|Jurisdiction mismatch| R2
    A3 -->|Jurisdiction matches| A4
    A4 -->|Insufficient clearance| R2
    A4 -->|Clearance sufficient| A5
    A5 -->|Actor is conflicted| R2
    A5 -->|No conflict| A6
    A6 -->|Unit restriction applies| A7
    A6 -->|No unit restriction| R1
    A7 -->|Not the assignee| R2
    A7 -->|Is the assignee| R1
```

**Source:** `sentinel-security/src/main/java/.../security/RoleBasedAuthorizationService.java`

## Permission Enum

The `Permission` enum defines 27 distinct permissions:

| Permission | Category | Required Roles |
|---|---|---|
| CREATE_REPORT | Reports | CASE_INTAKE_OFFICER |
| READ_REPORT | Reports | CASE_INTAKE_OFFICER, TRIAGE_OFFICER |
| TRIAGE_REPORT | Reports | TRIAGE_OFFICER, SUPERVISOR |
| CREATE_CASE | Cases | TRIAGE_OFFICER, SUPERVISOR |
| READ_CASE | Cases | INVESTIGATOR, CASE_REVIEWER, DECISION_MAKER, SUPERVISOR, AUDITOR |
| LIST_CASES | Cases | INVESTIGATOR, CASE_REVIEWER, DECISION_MAKER, SUPERVISOR, AUDITOR |
| ASSIGN_CASE | Cases | SUPERVISOR |
| TRANSITION_CASE | Cases | INVESTIGATOR, SUPERVISOR |
| READ_CASE_AUDIT | Cases | AUDITOR, SUPERVISOR |
| MANAGE_CASE_RELATIONSHIPS | Cases | SUPERVISOR |
| CREATE_EVIDENCE_UPLOAD_SESSION | Evidence | INVESTIGATOR, CASE_REVIEWER |
| FINALIZE_EVIDENCE | Evidence | INVESTIGATOR |
| READ_EVIDENCE | Evidence | INVESTIGATOR, CASE_REVIEWER, DECISION_MAKER, SUPERVISOR, AUDITOR |
| CREATE_EVIDENCE_DOWNLOAD_SESSION | Evidence | INVESTIGATOR, CASE_REVIEWER, DECISION_MAKER, SUPERVISOR, AUDITOR |
| CREATE_RECOMMENDATION | Recommendations | CASE_REVIEWER |
| SUBMIT_RECOMMENDATION | Recommendations | CASE_REVIEWER |
| REVIEW_RECOMMENDATION | Recommendations | DECISION_MAKER, SUPERVISOR |
| CREATE_DECISION | Decisions | DECISION_MAKER |
| APPROVE_DECISION | Decisions | DECISION_MAKER, SUPERVISOR |
| PUBLISH_DECISION | Decisions | DECISION_MAKER, SUPERVISOR |
| CREATE_APPEAL | Appeals | APPEAL_OFFICER |
| DECIDE_APPEAL | Appeals | SUPERVISOR, APPEAL_OFFICER (appeal panel) |
| LIST_TASKS | Workflow | INVESTIGATOR, CASE_REVIEWER, DECISION_MAKER, SUPERVISOR |
| CLAIM_TASK | Workflow | INVESTIGATOR, CASE_REVIEWER |
| COMPLETE_TASK | Workflow | INVESTIGATOR, CASE_REVIEWER |
| RECONCILE_WORKFLOW | Workflow | SUPERVISOR |
| RUN_MAINTENANCE_OPERATION | Operations | SYSTEM_ADMIN |

**Source:** `sentinel-application/src/main/java/.../application/security/Permission.java`

## Role Definitions

The platform defines 8 roles with a hierarchy of privileges:

| Role | Typical Access Scope |
|---|---|
| SYSTEM_ADMIN | Bypasses all authorization checks (admin bypass) |
| SUPERVISOR | Cross-cutting role — can triage, assign, review decisions, decide appeals, reconcile workflows |
| CASE_INTAKE_OFFICER | Report intake only |
| TRIAGE_OFFICER | Report triage and case creation |
| INVESTIGATOR | Manage cases, evidence, workflow tasks (direct assignment required) |
| CASE_REVIEWER | Review evidence, submit recommendations |
| DECISION_MAKER | Create, approve, and publish decisions |
| APPEAL_OFFICER | Manage appeals and participate in appeal panels |
| AUDITOR | Read-only access to cases, evidence, audit events |

**Source:** `sentinel-security/src/main/java/.../security/RoleBasedAuthorizationService.java`

## Authorization Axes (Checked in Order)

### 1. Admin Bypass
If the actor has `SYSTEM_ADMIN` role, all authorization checks pass immediately.

### 2. Role-Permission Mapping
Each `Permission` has an associated set of allowed roles. If the actor's role set does not include any allowed role for the requested permission, authorization fails with HTTP 403.

### 3. Jurisdiction Match
For jurisdiction-scoped operations, the actor's `jurisdictionCode` (from JWT `jurisdictions` claim) must match the target resource's `jurisdictionCode`. If the actor has no matching jurisdiction, authorization fails.

### 4. Classification Clearance
For classification-sensitive operations (primarily evidence-related), the actor's `caseClassification` level must be at least equal to the resource's classification. The hierarchy is: `PUBLIC < CONFIDENTIAL < SECRET`.

### 5. Conflict-of-Interest
If the actor's `conflictedActorIds` (from JWT `conflicted_actor_ids` claim) includes the resource owner's actor ID, access is denied.

### 6. Assigned Unit Scope
If the required permission is unit-scoped, the actor's `assignedUnitId` must match the resource's `assignedUnitId`. Not all permissions have unit scoping.

### 7. Direct Assignment (INVESTIGATOR Only)
For pure `INVESTIGATOR` role holders, certain operations (e.g., TRANSITION_CASE) require the actor to be the case's `assignee_user_id`. Other roles (e.g., SUPERVISOR) bypass this check.

## Authorization Context

The `AuthorizationContext` object bundles all authorization parameters for a request:
- `Permission` — The operation being authorized
- `ApplicationActor` — The authenticated actor
- `CaseAuthorizationScope` — Optional scope containing:
  - `caseId` — Target case identifier
  - `jurisdictionCode` — Case jurisdiction
  - `classification` — Case classification level
  - `assignedUnitId` — Case's assigned unit
  - `assigneeUserId` — Case's current assignee

**Source:** `sentinel-application/src/main/java/.../application/security/AuthorizationContext.java`, `sentinel-application/src/main/java/.../application/security/CaseAuthorizationScope.java`

## Integration Pattern

Application services declare required permissions at the method level:

```java
// Pattern used in all application service methods
public CaseRecordResult transitionCase(UUID caseId, CaseTransitionCommand command, ApplicationActor actor) {
    AuthorizationContext auth = AuthorizationContext.forPermission(Permission.TRANSITION_CASE)
        .withActor(actor)
        .withCaseScope(caseAuthorizationScope);
    authorizationService.authorize(auth);
    // ... proceed with domain logic
}
```

## Knowledge Gaps

- The authorization model uses a hard-coded role-to-permission mapping (switch expression in `RoleBasedAuthorizationService`). Dynamic or externalized permissions are not supported.
- Fine-grained field-level authorization (e.g., actor can see case title but not case summary) is not implemented.
- Authorization audit logging beyond the general audit event system is not implemented.

## Source References

- `sentinel-application/src/main/java/.../application/security/AuthorizationService.java` — Authorization interface
- `sentinel-security/src/main/java/.../security/RoleBasedAuthorizationService.java` — Multi-axis authorization implementation
- `sentinel-application/src/main/java/.../application/security/Permission.java` — Permission enum (27 values)
- `sentinel-application/src/main/java/.../application/security/ApplicationActor.java` — Actor value object with all axes
- `sentinel-application/src/main/java/.../application/security/AuthorizationContext.java` — Authorization parameter bundle
- `sentinel-application/src/main/java/.../application/security/CaseAuthorizationScope.java` — Case-scoped authorization parameters
- `sentinel-api/src/main/java/.../security/BearerAuthenticationFilter.java` — JWT extraction and actor setup
- `/openwiki/security/authentication.md` — JWT authentication and token verification
- `/openwiki/runtime/context-propagation.md` — Actor context propagation
