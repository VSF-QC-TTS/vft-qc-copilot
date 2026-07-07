# VinFast QC Copilot - Workspace & Project API Contract

Version: `v0.3`  
Status: API Contract Draft  
Base path: `/api/v1`  
Applies to: Workspace and Project management APIs  
Related docs: `docs/api/00-api-overview.md`, `docs/api/01-api-conventions.md`, `docs/api/02-auth-permission-contract.md`, PRD v0.7, ERD v0.5

---

## 1. Purpose

This document defines REST API contracts for:

```text
1. Workspace listing and creation.
2. Workspace detail and update.
3. Workspace member management.
4. Project listing and creation inside a workspace.
5. Project detail, update and soft delete/archive.
6. Project setup status/readiness for frontend setup wizard.
```

This file covers Workspace and Project APIs plus the project setup readiness endpoint. Dataset, secret/provider, chatbot target, evaluator and test run details are defined separately.

---

## 2. Resource Model Summary

### Workspace

A workspace groups projects, members, providers, secrets and shared datasets.

ERD table:

```text
workspaces
```

Main fields:

```text
id
name
type
created_by
created_at
updated_at
deleted_at
```

Workspace types:

```text
PERSONAL
TEAM
```

Rules:

```text
1. User registration should create one PERSONAL workspace automatically.
2. User-created team spaces use type TEAM.
3. Workspace delete is soft delete through deleted_at.
```

---

### Workspace Member

A workspace member links a user to a workspace role.

ERD table:

```text
workspace_members
```

Main fields:

```text
id
workspace_id
user_id
role
status
invited_by
joined_at
created_at
updated_at
```

Workspace roles:

```text
OWNER
ADMIN
MEMBER
VIEWER
```

Workspace member statuses:

```text
INVITED
ACTIVE
DISABLED
```

Rules:

```text
1. unique(workspace_id, user_id).
2. Creator of a workspace becomes OWNER.
3. Normal permission checks use only ACTIVE members.
4. MVP may create invited users as ACTIVE directly if invitation email flow is not implemented yet.
```

---

### Project

A project is the main container for chatbot target, dataset, evaluator and test run configs.

ERD table:

```text
projects
```

Main fields:

```text
id
workspace_id
name
description
created_by
created_at
updated_at
archived_at
deleted_at
```

Important project creation rule:

```text
When a project is created, backend must also create a default PROJECT dataset schema with columns `question` and `expected` in the same transaction.
```

---

## 3. Common DTOs

### 3.1. WorkspaceResponse

```json
{
  "id": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
  "name": "VinFast QC Team",
  "type": "TEAM",
  "currentUserRole": "OWNER",
  "createdBy": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
  "createdByUser": {
    "id": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
    "email": "owner@example.com",
    "name": "Workspace Owner",
    "avatarUrl": null
  },
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:00:00+07:00"
}
```

Field notes:

```text
currentUserRole:
  Role of the current user in this workspace.
  For PLATFORM_ADMIN who is not a workspace member, value may be null.
```

---

### 3.2. WorkspaceMemberResponse

```json
{
  "id": 1001,
  "workspaceId": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
  "userId": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
  "user": {
    "id": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
    "email": "qc.member@example.com",
    "name": "QC Member",
    "avatarUrl": null
  },
  "role": "MEMBER",
  "status": "ACTIVE",
  "invitedBy": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
  "invitedByUser": {
    "id": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
    "email": "owner@example.com",
    "name": "Workspace Owner",
    "avatarUrl": null
  },
  "joinedAt": "2026-07-02T10:30:00+07:00",
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:30:00+07:00"
}
```

---

### 3.3. ProjectResponse

```json
{
  "id": "0ddf0c99-c632-4ef8-9044-8e37c940d1b9",
  "workspaceId": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
  "name": "VF Sales Chatbot",
  "description": "Evaluation project for sales chatbot",
  "createdBy": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
  "createdByUser": {
    "id": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
    "email": "owner@example.com",
    "name": "Workspace Owner",
    "avatarUrl": null
  },
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:00:00+07:00",
  "archivedAt": null
}
```

---

### 3.4. ProjectSetupStatusResponse

Used by the frontend setup wizard to show whether a project is ready for a single run or comparison run. This is a lightweight aggregate view; it does not replace TestRun preflight.

```json
{
  "projectId": "0ddf0c99-c632-4ef8-9044-8e37c940d1b9",
  "workspaceId": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
  "steps": {
    "hasDataset": true,
    "hasActiveDatasetVersion": true,
    "hasActiveChatbotTarget": true,
    "hasResponseExtraction": true,
    "hasEvaluator": true,
    "hasUsableJudgeProvider": true
  },
  "counts": {
    "datasets": 1,
    "activeDatasetVersions": 1,
    "activeChatbotTargets": 2,
    "activeChatbotTargetsWithExtraction": 2,
    "activeEvaluators": 1,
    "usableJudgeProviders": 1,
    "usableBaselineProviders": 1,
    "comparisonCandidates": 3
  },
  "canRunSingle": true,
  "canRunComparison": true,
  "blockers": []
}
```

Blocker example:

```json
{
  "code": "PROJECT_SETUP_NO_ACTIVE_CHATBOT_TARGET",
  "step": "CHATBOT_TARGET",
  "severity": "ERROR",
  "message": "Create and activate at least one chatbot target before starting a run.",
  "actionEndpoint": "/api/v1/projects/0ddf0c99-c632-4ef8-9044-8e37c940d1b9/chatbot-targets"
}
```

Rules:

```text
1. `steps` are coarse booleans for wizard UI.
2. `counts` help UI show progress and comparison readiness.
3. `canRunSingle` means the project has enough configuration to create at least one valid SINGLE run.
4. `canRunComparison` means the project has enough configuration to create at least one valid COMPARISON run with two or more comparison candidates.
5. A comparison candidate may be an ACTIVE chatbot target with response extraction or a usable LLM baseline provider/model.
6. `hasUsableJudgeProvider` is required for LLM Judge evaluators and LLM baseline comparison, but rule-only evaluator projects may still be runnable without it.
7. The endpoint must return sanitized metadata only and must not expose secrets, raw cURL, raw request/response payloads or artifacts.
```

---

### 3.5. CollectionResponse

All list endpoints use:

```json
{
  "items": [],
  "page": {
    "limit": 20,
    "nextCursor": null,
    "hasMore": false
  }
}
```

---

## 4. Workspace APIs

### 4.1. List Workspaces

```http
GET /api/v1/workspaces
```

#### Purpose

Return workspaces visible to the current user.

#### Permission

```text
Authenticated ACTIVE user.
```

For normal USER:

```text
Return workspaces where user has ACTIVE workspace_members row.
```

For PLATFORM_ADMIN:

```text
May return all workspaces depending on admin policy.
```

#### Query parameters

| Name | Type | Required | Description |
| ---- | ---- | -------- | ----------- |
| `q` | string | No | Search by workspace name |
| `type` | enum | No | `PERSONAL` or `TEAM` |
| `limit` | int | No | Default 20, max 100 |
| `cursor` | string | No | Opaque pagination cursor |
| `sort` | string | No | Default `name`, supports `name`, `-createdAt` |

#### Response - 200 OK

```json
{
  "items": [
    {
      "id": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
      "name": "VinFast QC Team",
      "type": "TEAM",
      "currentUserRole": "MEMBER",
      "createdBy": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
      "createdByUser": {
        "id": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
        "email": "owner@example.com",
        "name": "Workspace Owner",
        "avatarUrl": null
      },
      "createdAt": "2026-07-02T10:00:00+07:00",
      "updatedAt": "2026-07-02T10:00:00+07:00"
    }
  ],
  "page": {
    "limit": 20,
    "nextCursor": null,
    "hasMore": false
  }
}
```

#### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
VALIDATION_ERROR
```

---

### 4.2. Create Workspace

```http
POST /api/v1/workspaces
```

#### Purpose

Create a new TEAM workspace.

Personal workspace creation is normally automatic during user onboarding and should not be created repeatedly by this endpoint.

#### Permission

```text
Authenticated ACTIVE user.
```

#### Request body

```json
{
  "name": "VinFast QC Team",
  "type": "TEAM"
}
```

Rules:

```text
1. MVP user-created workspace type must be TEAM.
2. Backend inserts creator as OWNER in workspace_members.
3. Workspace name must be unique enough for the current user's visible workspace list, but DB-level global uniqueness is not required.
```

#### Response - 201 Created

Headers:

```http
Location: /api/v1/workspaces/4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1
```

Body:

```json
{
  "id": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
  "name": "VinFast QC Team",
  "type": "TEAM",
  "currentUserRole": "OWNER",
  "createdBy": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
  "createdByUser": {
    "id": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
    "email": "owner@example.com",
    "name": "Workspace Owner",
    "avatarUrl": null
  },
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:00:00+07:00"
}
```

#### Entities touched

```text
workspaces
workspace_members
```

#### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
VALIDATION_ERROR
WORKSPACE_NAME_ALREADY_EXISTS
```

---

### 4.3. Get Workspace Detail

```http
GET /api/v1/workspaces/{workspaceId}
```

#### Purpose

Return workspace detail.

#### Permission

```text
Workspace VIEW permission.
```

#### Response - 200 OK

```json
{
  "id": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
  "name": "VinFast QC Team",
  "type": "TEAM",
  "currentUserRole": "ADMIN",
  "createdBy": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
  "createdByUser": {
    "id": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
    "email": "owner@example.com",
    "name": "Workspace Owner",
    "avatarUrl": null
  },
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:00:00+07:00"
}
```

#### Error codes

```text
AUTHENTICATION_REQUIRED
PERMISSION_DENIED
WORKSPACE_NOT_FOUND
```

---

### 4.4. Update Workspace

```http
PATCH /api/v1/workspaces/{workspaceId}
```

#### Purpose

Update workspace metadata.

#### Permission

```text
OWNER, ADMIN or PLATFORM_ADMIN.
```

#### Request body

```json
{
  "name": "VinFast QC Automation Team"
}
```

Rules:

```text
1. `type` cannot be changed after creation.
2. PERSONAL workspace should have stricter update policy if needed.
3. Empty PATCH body returns 400 VALIDATION_ERROR.
```

#### Response - 200 OK

Returns `WorkspaceResponse`.

#### Error codes

```text
VALIDATION_ERROR
PERMISSION_DENIED
WORKSPACE_NOT_FOUND
WORKSPACE_NAME_ALREADY_EXISTS
```

---

### 4.5. Delete Workspace

```http
DELETE /api/v1/workspaces/{workspaceId}
```

#### Purpose

Soft delete/archive a workspace.

#### Permission

```text
OWNER or PLATFORM_ADMIN.
```

#### Response - 204 No Content

No response body.

#### Rules

```text
1. Delete is soft delete using workspaces.deleted_at.
2. Normal users should not delete PERSONAL workspace in MVP unless account deletion flow exists.
3. Deleting a workspace hides its projects from normal list APIs.
4. Execution data should not be hard-deleted by this endpoint.
```

#### Error codes

```text
PERMISSION_DENIED
WORKSPACE_NOT_FOUND
WORKSPACE_OWNER_REQUIRED
INVALID_STATE_TRANSITION
```

---

## 5. Workspace Member APIs

### 5.1. List Workspace Members

```http
GET /api/v1/workspaces/{workspaceId}/members
```

#### Purpose

Return workspace members.

#### Permission

```text
Workspace VIEW permission.
```

#### Query parameters

| Name | Type | Required | Description |
| ---- | ---- | -------- | ----------- |
| `role` | enum | No | `OWNER`, `ADMIN`, `MEMBER`, `VIEWER` |
| `status` | enum | No | `INVITED`, `ACTIVE`, `DISABLED` |
| `q` | string | No | Search by user email/name |
| `limit` | int | No | Default 20, max 100 |
| `cursor` | string | No | Opaque pagination cursor |

#### Response - 200 OK

```json
{
  "items": [
    {
      "id": 1001,
      "workspaceId": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
      "userId": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
      "user": {
        "id": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
        "email": "qc.member@example.com",
        "name": "QC Member",
        "avatarUrl": null
      },
      "role": "MEMBER",
      "status": "ACTIVE",
      "invitedBy": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
      "invitedByUser": {
        "id": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
        "email": "owner@example.com",
        "name": "Workspace Owner",
        "avatarUrl": null
      },
      "joinedAt": "2026-07-02T10:30:00+07:00",
      "createdAt": "2026-07-02T10:00:00+07:00",
      "updatedAt": "2026-07-02T10:30:00+07:00"
    }
  ],
  "page": {
    "limit": 20,
    "nextCursor": null,
    "hasMore": false
  }
}
```

#### Error codes

```text
PERMISSION_DENIED
WORKSPACE_NOT_FOUND
VALIDATION_ERROR
```

---

### 5.2. Add Workspace Member

```http
POST /api/v1/workspaces/{workspaceId}/members
```

#### Purpose

Invite or add a user to workspace.

#### Permission

```text
OWNER, ADMIN or PLATFORM_ADMIN.
```

#### Request body

```json
{
  "email": "new.qc@example.com",
  "role": "MEMBER"
}
```

Rules:

```text
1. role must be ADMIN, MEMBER or VIEWER by default.
2. Adding OWNER should require OWNER or PLATFORM_ADMIN and may be disabled in MVP.
3. If invitation email flow is not implemented, backend may create member status ACTIVE directly.
4. Duplicate member returns 409 WORKSPACE_MEMBER_ALREADY_EXISTS.
```

#### Response - 201 Created

Returns `WorkspaceMemberResponse`.

#### Entities touched

```text
users
workspace_members
```

If user does not exist:

```text
MVP option A: return 404 USER_NOT_FOUND.
MVP option B: create invited placeholder user if invitation system exists.
```

Recommended MVP:

```text
Use option A until invitation flow is implemented.
```

#### Error codes

```text
VALIDATION_ERROR
PERMISSION_DENIED
WORKSPACE_NOT_FOUND
USER_NOT_FOUND
WORKSPACE_MEMBER_ALREADY_EXISTS
```

---

### 5.3. Update Workspace Member

```http
PATCH /api/v1/workspaces/{workspaceId}/members/{memberId}
```

#### Purpose

Update member role or status.

#### Permission

```text
OWNER, ADMIN or PLATFORM_ADMIN.
```

#### Request body

```json
{
  "role": "ADMIN",
  "status": "ACTIVE"
}
```

Rules:

```text
1. ADMIN cannot demote/remove the last OWNER.
2. ADMIN should not change OWNER role unless policy later allows it.
3. A user should not be able to demote themselves if they are the last OWNER.
4. status DISABLED means user no longer passes normal workspace permission checks.
```

#### Response - 200 OK

Returns `WorkspaceMemberResponse`.

#### Error codes

```text
VALIDATION_ERROR
PERMISSION_DENIED
WORKSPACE_NOT_FOUND
WORKSPACE_MEMBER_NOT_FOUND
WORKSPACE_OWNER_REQUIRED
INVALID_STATE_TRANSITION
```

---

### 5.4. Remove Workspace Member

```http
DELETE /api/v1/workspaces/{workspaceId}/members/{memberId}
```

#### Purpose

Disable/remove a member from workspace.

#### Permission

```text
OWNER, ADMIN or PLATFORM_ADMIN.
```

#### Response - 204 No Content

No response body.

#### Rules

```text
1. MVP should set workspace_members.status = DISABLED instead of hard delete.
2. Cannot remove the last OWNER.
3. ADMIN should not remove OWNER unless policy later allows it.
```

#### Error codes

```text
PERMISSION_DENIED
WORKSPACE_NOT_FOUND
WORKSPACE_MEMBER_NOT_FOUND
WORKSPACE_OWNER_REQUIRED
INVALID_STATE_TRANSITION
```

---

## 6. Project APIs

### 6.1. List Projects in Workspace

```http
GET /api/v1/workspaces/{workspaceId}/projects
```

#### Purpose

Return projects in a workspace visible to the current user.

#### Permission

```text
Workspace VIEW permission.
```

#### Query parameters

| Name | Type | Required | Description |
| ---- | ---- | -------- | ----------- |
| `q` | string | No | Search by project name/description |
| `includeArchived` | boolean | No | Default false |
| `limit` | int | No | Default 20, max 100 |
| `cursor` | string | No | Opaque pagination cursor |
| `sort` | string | No | Default `-createdAt`, supports `name`, `-createdAt`, `updatedAt`, `-updatedAt` |

#### Response - 200 OK

```json
{
  "items": [
    {
      "id": "0ddf0c99-c632-4ef8-9044-8e37c940d1b9",
      "workspaceId": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
      "name": "VF Sales Chatbot",
      "description": "Evaluation project for sales chatbot",
      "createdBy": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
      "createdByUser": {
        "id": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
        "email": "owner@example.com",
        "name": "Workspace Owner",
        "avatarUrl": null
      },
      "createdAt": "2026-07-02T10:00:00+07:00",
      "updatedAt": "2026-07-02T10:00:00+07:00",
      "archivedAt": null
    }
  ],
  "page": {
    "limit": 20,
    "nextCursor": null,
    "hasMore": false
  }
}
```

#### Error codes

```text
PERMISSION_DENIED
WORKSPACE_NOT_FOUND
VALIDATION_ERROR
```

---

### 6.2. Create Project

```http
POST /api/v1/workspaces/{workspaceId}/projects
```

#### Purpose

Create a project in a workspace.

#### Permission

```text
OWNER, ADMIN, MEMBER or PLATFORM_ADMIN.
```

#### Request body

```json
{
  "name": "VF Sales Chatbot",
  "description": "Evaluation project for sales chatbot"
}
```

Validation:

```text
name:
  required, trimmed, max 120 characters

description:
  optional, max 1000 characters
```

#### Response - 201 Created

Headers:

```http
Location: /api/v1/projects/0ddf0c99-c632-4ef8-9044-8e37c940d1b9
```

Body:

```json
{
  "id": "0ddf0c99-c632-4ef8-9044-8e37c940d1b9",
  "workspaceId": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
  "name": "VF Sales Chatbot",
  "description": "Evaluation project for sales chatbot",
  "createdBy": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
  "createdByUser": {
    "id": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
    "email": "owner@example.com",
    "name": "Workspace Owner",
    "avatarUrl": null
  },
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:00:00+07:00",
  "archivedAt": null
}
```

#### State changes

Backend must perform the following in one transaction:

```text
1. Insert projects row.
2. Insert dataset_schemas row:
   - scope = PROJECT
   - project_id = new project id
   - version = 1
   - name = default
3. Insert dataset_schema_columns rows:
   - question, data_type TEXT, required true, order_index 1
   - expected, data_type TEXT, required false, order_index 2
```

#### Entities touched

```text
projects
dataset_schemas
dataset_schema_columns
```

#### Error codes

```text
VALIDATION_ERROR
PERMISSION_DENIED
WORKSPACE_NOT_FOUND
PROJECT_NAME_ALREADY_EXISTS
```

---

### 6.3. Get Project Detail

```http
GET /api/v1/projects/{projectId}
```

#### Purpose

Return project detail.

#### Permission

```text
Project VIEW permission through workspace membership.
```

#### Response - 200 OK

Returns `ProjectResponse`.

#### Error codes

```text
PERMISSION_DENIED
PROJECT_NOT_FOUND
```

Visibility rule:

```text
If the project exists but belongs to a workspace the user cannot access, return 404 PROJECT_NOT_FOUND.
```

---


### 6.4. Get Project Setup Status

```http
GET /api/v1/projects/{projectId}/setup-status
```

#### Purpose

Return an aggregated readiness checklist for the project setup wizard.

This endpoint helps frontend guide QC through:

```text
1. Create project.
2. Configure secret/provider.
3. Configure chatbot target and response extraction.
4. Import or create dataset and publish ACTIVE version.
5. Configure evaluator.
6. Confirm the project can run SINGLE and/or COMPARISON test runs.
```

This endpoint is intentionally earlier and lighter than `POST /api/v1/projects/{projectId}/test-runs/preflight`. Preflight still validates a concrete run request with selected dataset version, targets, evaluator versions and options.

#### Permission

```text
Workspace VIEW permission.
```

VIEWER can see setup status but cannot perform setup actions.

#### Response - 200 OK

```json
{
  "projectId": "0ddf0c99-c632-4ef8-9044-8e37c940d1b9",
  "workspaceId": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
  "steps": {
    "hasDataset": true,
    "hasActiveDatasetVersion": true,
    "hasActiveChatbotTarget": true,
    "hasResponseExtraction": true,
    "hasEvaluator": true,
    "hasUsableJudgeProvider": true
  },
  "counts": {
    "datasets": 1,
    "activeDatasetVersions": 1,
    "activeChatbotTargets": 2,
    "activeChatbotTargetsWithExtraction": 2,
    "activeEvaluators": 1,
    "usableJudgeProviders": 1,
    "usableBaselineProviders": 1,
    "comparisonCandidates": 3
  },
  "canRunSingle": true,
  "canRunComparison": true,
  "blockers": []
}
```

#### Readiness computation

```text
hasDataset:
  Project has at least one non-deleted dataset.

hasActiveDatasetVersion:
  At least one project dataset has current ACTIVE DatasetVersion with row_count > 0.

hasActiveChatbotTarget:
  Project has at least one ACTIVE chatbot target.

hasResponseExtraction:
  At least one ACTIVE chatbot target has a valid response_extraction_config_json.mainAnswerPath.

hasEvaluator:
  Project has at least one non-deleted evaluator with at least one version.

hasUsableJudgeProvider:
  Current user can use at least one ACTIVE personal or workspace LLM provider for LLM Judge/baseline use.

canRunSingle:
  hasActiveDatasetVersion && hasActiveChatbotTarget && hasResponseExtraction && hasEvaluator, and any required judge provider exists for selected/current evaluator style.

canRunComparison:
  hasActiveDatasetVersion && hasEvaluator && comparisonCandidates >= 2, and any required judge/baseline providers are usable.
```

#### Blockers

Possible blocker codes:

```text
PROJECT_SETUP_NO_DATASET
PROJECT_SETUP_NO_ACTIVE_DATASET_VERSION
PROJECT_SETUP_EMPTY_DATASET_VERSION
PROJECT_SETUP_NO_ACTIVE_CHATBOT_TARGET
PROJECT_SETUP_RESPONSE_EXTRACTION_MISSING
PROJECT_SETUP_NO_EVALUATOR
PROJECT_SETUP_JUDGE_PROVIDER_REQUIRED
PROJECT_SETUP_COMPARISON_TARGETS_REQUIRED
PROJECT_SETUP_PROVIDER_ERROR
```

Severity values:

```text
ERROR
WARNING
INFO
```

Rules:

```text
1. ERROR blockers prevent canRunSingle or canRunComparison depending on context.
2. WARNING blockers should guide setup but may not block rule-only single runs.
3. actionEndpoint should point to the next relevant frontend/API resource when possible.
4. The endpoint must not validate every selected run option; that belongs to preflight.
```

#### Entities read

```text
projects
datasets
dataset_versions
chatbot_targets
evaluators
evaluator_versions
llm_providers
secrets
workspace_members
```

#### Error codes

```text
AUTHENTICATION_REQUIRED
USER_DISABLED
PERMISSION_DENIED
PROJECT_NOT_FOUND
```

---

### 6.5. Update Project

```http
PATCH /api/v1/projects/{projectId}
```

#### Purpose

Update project metadata.

#### Permission

```text
OWNER, ADMIN, MEMBER or PLATFORM_ADMIN.
```

#### Request body

```json
{
  "name": "VF Sales Chatbot Regression",
  "description": "Regression evaluation project for sales chatbot"
}
```

Rules:

```text
1. Empty PATCH body returns 400 VALIDATION_ERROR.
2. Project name must remain unique inside the workspace among non-deleted projects.
3. Updating project metadata does not modify dataset schema or test run snapshots.
```

#### Response - 200 OK

Returns `ProjectResponse`.

#### Error codes

```text
VALIDATION_ERROR
PERMISSION_DENIED
PROJECT_NOT_FOUND
PROJECT_NAME_ALREADY_EXISTS
```

---

### 6.6. Delete Project

```http
DELETE /api/v1/projects/{projectId}
```

#### Purpose

Soft delete/archive a project.

#### Permission

```text
OWNER, ADMIN, MEMBER or PLATFORM_ADMIN.
```

#### Response - 204 No Content

No response body.

#### Rules

```text
1. Delete is soft delete using projects.deleted_at.
2. Normal list endpoints must exclude deleted projects.
3. Deleting a project must not hard-delete test_runs, test_case_results, runner_events or artifacts.
4. If there are active RUNNING/CANCELLING test runs, backend should reject deletion with 409 PROJECT_HAS_ACTIVE_RUNS.
```

#### Entities touched

```text
projects
```

#### Error codes

```text
PERMISSION_DENIED
PROJECT_NOT_FOUND
PROJECT_HAS_ACTIVE_RUNS
INVALID_STATE_TRANSITION
```

---

## 7. Common Error Examples

### 7.1. Project name already exists

```http
409 Conflict
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/project-name-already-exists",
  "title": "Project name already exists",
  "status": 409,
  "detail": "A project with this name already exists in the workspace.",
  "instance": "/api/v1/workspaces/4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1/projects",
  "code": "PROJECT_NAME_ALREADY_EXISTS",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

---

### 7.2. Permission denied

```http
403 Forbidden
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/permission-denied",
  "title": "Permission denied",
  "status": 403,
  "detail": "You do not have permission to update this workspace.",
  "instance": "/api/v1/workspaces/4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
  "code": "PERMISSION_DENIED",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

---

## 8. Implementation Checklist

Workspace/project implementation is correct when:

```text
1. List workspaces returns only visible workspaces for normal USER.
2. Create workspace inserts creator as OWNER.
3. No endpoint allows non-member USER to access workspace resources.
4. Workspace update is denied for MEMBER and VIEWER.
5. Workspace delete is denied for ADMIN, MEMBER and VIEWER.
6. Member add/update/remove validates last OWNER protection.
7. Project create is allowed for OWNER, ADMIN and MEMBER.
8. Project create also creates default PROJECT dataset schema question/expected.
9. Project update does not mutate dataset schema or historical test run snapshots.
10. Project setup-status aggregates readiness without exposing secrets or raw payloads.
11. Project setup-status distinguishes SINGLE readiness from COMPARISON readiness.
12. Project delete is soft delete and does not hard-delete execution data.
13. All errors use RFC 9457 Problem Details.
14. All response fields use camelCase.
15. Collection endpoints return items/page envelope.
```
