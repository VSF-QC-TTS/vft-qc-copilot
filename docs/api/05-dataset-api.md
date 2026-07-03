# VinFast QC Copilot - Dataset API Contract

Version: `v0.2`  
Status: API Contract Draft  
Base path: `/api/v1`  
Related docs:

- `docs/api/00-api-overview.md`
- `docs/api/01-api-conventions.md`
- `docs/api/02-auth-permission-contract.md`
- `docs/api/03-workspace-project-api.md`
- `docs/api/04-secret-provider-api.md`
- `prd_v07.md`
- `erd_v05.md`

---

## 1. Purpose

This document defines the REST API contract for dataset schemas, datasets, dataset versions, dataset rows and dataset import jobs in VinFast QC Copilot.

This contract covers:

```text
1. Project dataset schema lookup.
2. Dataset schema version creation.
3. Dataset listing and metadata update.
4. Dataset version listing and row preview.
5. Excel dataset import.
6. Dataset clone/import from another project.
7. Dataset clone/import from workspace dataset.
8. Dataset clone/import from personal dataset.
9. Schema compatibility and mapping rules.
10. Dataset variable metadata for chatbot target/evaluator mapping UI.
11. Excel-like dataset table editing through DRAFT DatasetVersion.
12. Dataset table/card/library viewing for QC review.
13. Dataset export to Excel for QC handoff/reporting.
```

This document does not define:

```text
1. Frontend Excel parsing implementation.
2. Backend file storage internals for uploaded Excel files.
3. Scheduled dataset sync.
4. Live reference datasets.
5. Partial row clone or row subset management.
6. Test run preflight row limit enforcement.
```

Those details belong in frontend implementation, backend storage spec, and test run API spec.

---

## 2. Design Principles

```text
1. Dataset schema is versioned.
2. Dataset data is versioned.
3. Dataset rows belong to DatasetVersion, not directly to Dataset.
4. DatasetVersion ACTIVE is immutable once it may be used by TestRun.
5. Dataset editing must happen in a DRAFT DatasetVersion, not directly in an ACTIVE version.
6. Publishing a DRAFT version creates a new ACTIVE DatasetVersion and updates datasets.current_version_id.
7. Editing schema columns creates a new DatasetSchema version.
8. TestRun must lock dataset_id, dataset_version_id, dataset_schema_id and dataset_schema_version.
9. Dataset import must use preview -> mapping -> confirm flow.
10. Backend must preserve source metadata for imported/cloned datasets.
11. Workspace/personal datasets must use scope-aware schemas, not fake project schemas.
12. Dataset APIs must expose UI-friendly variables using `$dataset.<columnName>`.
13. Backend domain must not expose Promptfoo template syntax as source of truth.
14. Dataset is a QC workspace: table/card/library views should come from the same versioned row source.
15. Excel import/export and copy/paste ergonomics are core UX, not optional extras.
```

---

## 3. Resource Model Summary

### 3.1. Dataset Schema

A dataset schema defines the columns available for dataset rows.

Database entity:

```text
dataset_schemas
```

Main fields from ERD:

```text
id UUID PK
scope dataset_schema_scope NOT NULL
project_id UUID FK projects(id)
workspace_id UUID FK workspaces(id)
owner_user_id UUID FK users(id)
version INT NOT NULL
name TEXT NOT NULL
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
```

Supported scopes:

```text
PROJECT
WORKSPACE
PERSONAL
```

Scope rules:

```text
PROJECT:
  project_id is required
  workspace_id is null
  owner_user_id is null

WORKSPACE:
  workspace_id is required
  project_id is null
  owner_user_id is null

PERSONAL:
  owner_user_id is required
  workspace_id is null
  project_id is null
```

Project creation rule:

```text
When a project is created, backend must create a default PROJECT dataset schema with columns:
- question
- expected
```

---

### 3.2. Dataset Schema Column

A dataset schema column defines one field in a dataset row.

Database entity:

```text
dataset_schema_columns
```

Main fields from ERD:

```text
id BIGINT PK
schema_id UUID FK dataset_schemas(id)
name TEXT NOT NULL
display_name TEXT
data_type TEXT NOT NULL
required BOOLEAN NOT NULL DEFAULT false
description TEXT
order_index INT NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

MVP supported `dataType` values:

```text
TEXT
NUMBER
BOOLEAN
JSON
DATE
DATETIME
```

Column naming rule:

```text
1. `name` must be stable and machine-readable.
2. `name` must use lower camelCase or snake_case consistently inside a schema.
3. `displayName` may be human-friendly and localized later.
4. `$dataset.<name>` is generated from `name`.
```

Example:

```json
{
  "name": "expectedIntent",
  "displayName": "Expected intent",
  "dataType": "TEXT",
  "required": false,
  "variable": "$dataset.expectedIntent"
}
```

---

### 3.3. Dataset

A dataset is a logical container for dataset versions.

Database entity:

```text
datasets
```

Main fields from ERD:

```text
id UUID PK
scope dataset_scope NOT NULL
workspace_id UUID FK workspaces(id)
project_id UUID FK projects(id)
owner_user_id UUID FK users(id)
current_version_id UUID FK dataset_versions(id)
name TEXT NOT NULL
description TEXT
source_dataset_id UUID FK datasets(id)
source_project_id UUID FK projects(id)
source_workspace_id UUID FK workspaces(id)
source_type dataset_source_type
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
deleted_at TIMESTAMPTZ
```

Supported scopes:

```text
PROJECT
WORKSPACE
PERSONAL
```

Supported source types:

```text
EXCEL
PROJECT_CLONE
WORKSPACE_DATASET
PERSONAL_DATASET
MANUAL
```

Scope rules:

```text
PROJECT:
  project_id is required

WORKSPACE:
  workspace_id is required
  project_id is null

PERSONAL:
  owner_user_id is required
  workspace_id is null
  project_id is null
```

MVP primary scope:

```text
Most dataset APIs in this document are project-oriented because the MVP flow starts from Project Detail -> Datasets tab.
```

---

### 3.4. Dataset Version

A dataset version is an immutable snapshot of rows under a specific schema version.

Database entity:

```text
dataset_versions
```

Main fields from ERD:

```text
id UUID PK
dataset_id UUID FK datasets(id)
version INT NOT NULL
schema_id UUID FK dataset_schemas(id)
schema_version INT NOT NULL
source_dataset_version_id UUID FK dataset_versions(id)
import_job_id UUID FK dataset_import_jobs(id)
import_mapping_json JSONB
row_count INT NOT NULL DEFAULT 0
status dataset_version_status NOT NULL DEFAULT 'DRAFT'
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
```

Supported statuses:

```text
DRAFT
ACTIVE
ARCHIVED
```

Versioning rule:

```text
1. A Dataset may have many DatasetVersions.
2. Dataset.current_version_id points to the active/current version.
3. TestRun always references dataset_version_id directly.
4. ACTIVE DatasetVersion must not be mutated in place.
```

---

### 3.5. Dataset Row

A dataset row stores one test case input record.

Database entity:

```text
dataset_rows
```

Main fields from ERD:

```text
id BIGINT PK
dataset_version_id UUID FK dataset_versions(id)
row_index INT NOT NULL
data_json JSONB NOT NULL
metadata_json JSONB
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Mapping rule:

```text
Each DatasetRow becomes one Promptfoo test case.
Each data_json key becomes a variable in tests[].vars.
```

UI variable rule:

```text
UI shows:      $dataset.question
Runner uses:   {{question}}
```

Backend API responses must expose `$dataset.<columnName>` variables for UI mapping, but backend source of truth remains dataset schema/row JSON.

---

### 3.6. Dataset Import Job

A dataset import job represents preview, mapping and confirm state for dataset import.

Database entity:

```text
dataset_import_jobs
```

Main fields from ERD:

```text
id UUID PK
target_project_id UUID FK projects(id)
target_dataset_id UUID FK datasets(id)
source_type dataset_import_source_type NOT NULL
source_file_id TEXT
source_dataset_id UUID FK datasets(id)
source_dataset_version_id UUID FK dataset_versions(id)
status dataset_import_status NOT NULL
mapping_json JSONB
preview_json JSONB
error_message TEXT
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
finished_at TIMESTAMPTZ
```

Supported source types:

```text
EXCEL
PROJECT_CLONE
WORKSPACE_DATASET
PERSONAL_DATASET
```

Supported statuses:

```text
PENDING
PREVIEWED
IMPORTING
COMPLETED
FAILED
CANCELLED
```

MVP flow:

```text
Create import job -> Preview rows/schema -> User confirms mapping -> Backend imports rows -> DatasetVersion ACTIVE
```

---

## 4. Permission Rules

Dataset APIs are scoped through workspace/project access.

### 4.1. Project Dataset Permission

| Action | OWNER | ADMIN | MEMBER | VIEWER |
| ------ | ----- | ----- | ------ | ------ |
| List project datasets | Yes | Yes | Yes | Yes |
| View dataset metadata | Yes | Yes | Yes | Yes |
| View dataset rows | Yes | Yes | Yes | Yes |
| Import dataset | Yes | Yes | Yes | No |
| Update dataset metadata | Yes | Yes | Yes | No |
| Delete/archive dataset | Yes | Yes | Yes | No |
| Create schema version | Yes | Yes | Yes | No |

### 4.2. Source Dataset Permission

When importing from another project/workspace/personal dataset:

```text
1. User must have VIEW permission on the source dataset.
2. User must have IMPORT_DATASET permission on the target project.
3. Backend must not reveal source dataset existence if user cannot view it.
```

### 4.3. PLATFORM_ADMIN Rule

```text
PLATFORM_ADMIN may access dataset APIs for operational support, except where another policy explicitly denies an action.
```

Dataset APIs do not expose plaintext secrets, so the secret-deny rule does not apply directly here.

---

## 5. Endpoint Summary

### 5.1. Dataset Schema APIs

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| `GET` | `/api/v1/projects/{projectId}/dataset-schemas/current` | Get current project dataset schema |
| `GET` | `/api/v1/dataset-schemas/{schemaId}` | Get dataset schema detail |
| `GET` | `/api/v1/dataset-schemas/{schemaId}/columns` | List schema columns |
| `POST` | `/api/v1/projects/{projectId}/dataset-schemas/versions` | Create a new project schema version |

### 5.2. Dataset APIs

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| `GET` | `/api/v1/projects/{projectId}/datasets` | List datasets in a project |
| `GET` | `/api/v1/datasets/{datasetId}` | Get dataset metadata |
| `PATCH` | `/api/v1/datasets/{datasetId}` | Update dataset name/description |
| `DELETE` | `/api/v1/datasets/{datasetId}` | Soft delete/archive dataset |
| `GET` | `/api/v1/datasets/{datasetId}/versions` | List dataset versions |
| `GET` | `/api/v1/dataset-versions/{datasetVersionId}` | Get dataset version detail |
| `GET` | `/api/v1/dataset-versions/{datasetVersionId}/rows` | List dataset rows |
| `GET` | `/api/v1/dataset-versions/{datasetVersionId}/variables` | List UI variables from version schema |

### 5.3. Dataset Editing APIs

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| `POST` | `/api/v1/dataset-versions/{datasetVersionId}/draft` | Create or reuse an editable DRAFT version from an existing version |
| `POST` | `/api/v1/dataset-versions/{draftVersionId}/rows` | Insert one row into a DRAFT version |
| `PATCH` | `/api/v1/dataset-versions/{draftVersionId}/rows/{rowId}/cells` | Patch one or more cells in a DRAFT row |
| `POST` | `/api/v1/dataset-versions/{draftVersionId}/rows/bulk-upsert` | Bulk paste/upsert rows and cells into a DRAFT version |
| `DELETE` | `/api/v1/dataset-versions/{draftVersionId}/rows/{rowId}` | Delete one row from a DRAFT version |
| `POST` | `/api/v1/dataset-versions/{draftVersionId}/publish` | Publish a DRAFT version as the dataset current ACTIVE version |
| `POST` | `/api/v1/dataset-versions/{draftVersionId}/discard` | Discard a DRAFT version |

### 5.4. Dataset Import Source APIs

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| `GET` | `/api/v1/projects/{projectId}/dataset-import-sources/projects` | List source projects user can import from |
| `GET` | `/api/v1/projects/{projectId}/dataset-import-sources/projects/{sourceProjectId}/datasets` | List source project datasets |
| `GET` | `/api/v1/projects/{projectId}/dataset-import-sources/workspace-datasets` | List workspace datasets importable into project |
| `GET` | `/api/v1/projects/{projectId}/dataset-import-sources/personal-datasets` | List current user's personal datasets importable into project |

### 5.5. Dataset Import Job APIs

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| `POST` | `/api/v1/projects/{projectId}/dataset-import-jobs` | Create import preview job for Excel/project/workspace/personal source |
| `GET` | `/api/v1/dataset-import-jobs/{importJobId}` | Get import job status/preview |
| `POST` | `/api/v1/dataset-import-jobs/{importJobId}/confirm` | Confirm mapping and import dataset |
| `POST` | `/api/v1/dataset-import-jobs/{importJobId}/cancel` | Cancel pending/previewed import job |

### 5.6. Dataset Export APIs

| Method | Endpoint | Purpose |
| ------ | -------- | ------- |
| `POST` | `/api/v1/dataset-versions/{datasetVersionId}/exports` | Create an XLSX export job for a dataset version |
| `GET` | `/api/v1/export-jobs/{exportJobId}` | Get export job status |
| `GET` | `/api/v1/export-jobs/{exportJobId}/download` | Download completed export file |

---

## 6. Common DTOs

### 6.1. DatasetSchemaResponse

```json
{
  "id": "19fdf42e-6733-49f0-8745-c5d605a51b6d",
  "scope": "PROJECT",
  "projectId": "0ddf0c99-c632-4ef8-9044-8e37c940d1b9",
  "workspaceId": null,
  "ownerUserId": null,
  "version": 2,
  "name": "Default Project Dataset Schema",
  "columns": [
    {
      "id": 1001,
      "name": "question",
      "displayName": "Question",
      "dataType": "TEXT",
      "required": true,
      "description": "User question sent to chatbot target",
      "orderIndex": 1,
      "variable": "$dataset.question"
    },
    {
      "id": 1002,
      "name": "expected",
      "displayName": "Expected Answer",
      "dataType": "TEXT",
      "required": false,
      "description": "Expected answer or grading reference",
      "orderIndex": 2,
      "variable": "$dataset.expected"
    }
  ],
  "createdBy": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
  "createdAt": "2026-07-02T10:00:00+07:00"
}
```

---

### 6.2. DatasetSchemaColumnResponse

```json
{
  "id": 1001,
  "schemaId": "19fdf42e-6733-49f0-8745-c5d605a51b6d",
  "name": "question",
  "displayName": "Question",
  "dataType": "TEXT",
  "required": true,
  "description": "User question sent to chatbot target",
  "orderIndex": 1,
  "variable": "$dataset.question",
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:00:00+07:00"
}
```

---

### 6.3. DatasetResponse

```json
{
  "id": "500863fe-03e6-4946-ae98-0ed78d9832f8",
  "scope": "PROJECT",
  "workspaceId": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
  "projectId": "0ddf0c99-c632-4ef8-9044-8e37c940d1b9",
  "ownerUserId": null,
  "name": "VF Sales Chatbot Regression Dataset",
  "description": "Regression questions for sales chatbot",
  "sourceType": "EXCEL",
  "sourceDatasetId": null,
  "sourceProjectId": null,
  "sourceWorkspaceId": null,
  "currentVersion": {
    "id": "ca37c5c1-7eb6-43e1-8474-47355cc4cf9a",
    "version": 1,
    "schemaId": "19fdf42e-6733-49f0-8745-c5d605a51b6d",
    "schemaVersion": 2,
    "rowCount": 120,
    "status": "ACTIVE",
    "createdAt": "2026-07-02T10:00:00+07:00"
  },
  "createdBy": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:00:00+07:00"
}
```

---

### 6.4. DatasetVersionResponse

```json
{
  "id": "ca37c5c1-7eb6-43e1-8474-47355cc4cf9a",
  "datasetId": "500863fe-03e6-4946-ae98-0ed78d9832f8",
  "version": 1,
  "schemaId": "19fdf42e-6733-49f0-8745-c5d605a51b6d",
  "schemaVersion": 2,
  "sourceDatasetVersionId": null,
  "importJobId": "e99ba006-c3b8-40a6-a352-f17a1a38a206",
  "rowCount": 120,
  "status": "ACTIVE",
  "createdBy": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
  "createdAt": "2026-07-02T10:00:00+07:00"
}
```

---

### 6.5. DatasetRowResponse

```json
{
  "id": 9001,
  "datasetVersionId": "ca37c5c1-7eb6-43e1-8474-47355cc4cf9a",
  "rowIndex": 1,
  "data": {
    "question": "VF 8 có mấy phiên bản?",
    "expected": "VF 8 có các phiên bản Eco và Plus.",
    "context": "Thông tin sản phẩm VF 8",
    "category": "product_info"
  },
  "metadata": {
    "sourceRowNumber": 2,
    "sheetName": "Regression"
  },
  "createdAt": "2026-07-02T10:00:00+07:00",
  "updatedAt": "2026-07-02T10:00:00+07:00"
}
```

API field names use `data` and `metadata`. Backend persistence may map them to `data_json` and `metadata_json`.

---

### 6.6. DatasetVariableResponse

```json
{
  "datasetVersionId": "ca37c5c1-7eb6-43e1-8474-47355cc4cf9a",
  "schemaId": "19fdf42e-6733-49f0-8745-c5d605a51b6d",
  "schemaVersion": 2,
  "variables": [
    {
      "name": "question",
      "variable": "$dataset.question",
      "dataType": "TEXT",
      "required": true,
      "description": "User question sent to chatbot target"
    },
    {
      "name": "expected",
      "variable": "$dataset.expected",
      "dataType": "TEXT",
      "required": false,
      "description": "Expected answer or grading reference"
    }
  ]
}
```

Used by:

```text
1. Chatbot target request mapping UI.
2. Evaluator rule builder UI.
3. LLM Judge input template UI.
```

---

### 6.7. DatasetImportJobResponse

```json
{
  "id": "e99ba006-c3b8-40a6-a352-f17a1a38a206",
  "targetProjectId": "0ddf0c99-c632-4ef8-9044-8e37c940d1b9",
  "targetDatasetId": null,
  "sourceType": "EXCEL",
  "sourceFileId": "upload_20260702_abc123",
  "sourceDatasetId": null,
  "sourceDatasetVersionId": null,
  "status": "PREVIEWED",
  "schemaCompatibility": {
    "status": "REQUIRES_MAPPING",
    "requiresMapping": true,
    "sourceColumnCount": 4,
    "targetColumnCount": 2,
    "warnings": [
      "Column context does not exist in target schema and may be added as a new column."
    ]
  },
  "preview": {
    "sheetName": "Regression",
    "availableSheets": ["Regression", "Smoke"],
    "columns": ["question", "expected", "context", "category"],
    "rows": [
      {
        "rowIndex": 1,
        "data": {
          "question": "VF 8 có mấy phiên bản?",
          "expected": "VF 8 có các phiên bản Eco và Plus.",
          "context": "Thông tin sản phẩm VF 8",
          "category": "product_info"
        }
      }
    ],
    "totalRows": 120,
    "previewRowCount": 10
  },
  "mapping": null,
  "errorMessage": null,
  "createdBy": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
  "createdAt": "2026-07-02T10:00:00+07:00",
  "finishedAt": null
}
```

Supported `schemaCompatibility.status` values:

```text
COMPATIBLE
REQUIRES_MAPPING
INCOMPATIBLE
```

---

## 7. Dataset Schema APIs

### 7.1. Get Current Project Dataset Schema

```http
GET /api/v1/projects/{projectId}/dataset-schemas/current
```

Returns the latest/current project dataset schema.

#### Permission

```text
Project VIEW.
```

#### Response `200 OK`

```json
{
  "id": "19fdf42e-6733-49f0-8745-c5d605a51b6d",
  "scope": "PROJECT",
  "projectId": "0ddf0c99-c632-4ef8-9044-8e37c940d1b9",
  "workspaceId": null,
  "ownerUserId": null,
  "version": 2,
  "name": "Default Project Dataset Schema",
  "columns": [
    {
      "id": 1001,
      "name": "question",
      "displayName": "Question",
      "dataType": "TEXT",
      "required": true,
      "description": "User question sent to chatbot target",
      "orderIndex": 1,
      "variable": "$dataset.question"
    },
    {
      "id": 1002,
      "name": "expected",
      "displayName": "Expected Answer",
      "dataType": "TEXT",
      "required": false,
      "description": "Expected answer or grading reference",
      "orderIndex": 2,
      "variable": "$dataset.expected"
    }
  ],
  "createdBy": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
  "createdAt": "2026-07-02T10:00:00+07:00"
}
```

---

### 7.2. Get Dataset Schema Detail

```http
GET /api/v1/dataset-schemas/{schemaId}
```

#### Permission

```text
VIEW permission on schema scope:
- PROJECT schema -> Project VIEW.
- WORKSPACE schema -> Workspace VIEW.
- PERSONAL schema -> owner user only, or PLATFORM_ADMIN.
```

#### Response `200 OK`

Returns `DatasetSchemaResponse`.

---

### 7.3. List Dataset Schema Columns

```http
GET /api/v1/dataset-schemas/{schemaId}/columns
```

#### Query Parameters

| Name | Type | Required | Description |
| ---- | ---- | -------- | ----------- |
| `page` | integer | No | Defaults to `0` |
| `size` | integer | No | Defaults to `50` |

#### Response `200 OK`

```json
{
  "items": [
    {
      "id": 1001,
      "schemaId": "19fdf42e-6733-49f0-8745-c5d605a51b6d",
      "name": "question",
      "displayName": "Question",
      "dataType": "TEXT",
      "required": true,
      "description": "User question sent to chatbot target",
      "orderIndex": 1,
      "variable": "$dataset.question",
      "createdAt": "2026-07-02T10:00:00+07:00",
      "updatedAt": "2026-07-02T10:00:00+07:00"
    }
  ],
  "page": {
    "number": 0,
    "size": 50,
    "totalElements": 2,
    "totalPages": 1
  }
}
```

---

### 7.4. Create Project Dataset Schema Version

```http
POST /api/v1/projects/{projectId}/dataset-schemas/versions
```

Creates a new schema version for a project.

This endpoint is used when:

```text
1. User manually edits schema columns.
2. Import mapping adds new columns.
3. Backend needs to create a new target schema version before confirming import.
```

#### Permission

```text
Project UPDATE.
Allowed roles: OWNER, ADMIN, MEMBER.
Denied role: VIEWER.
```

#### Request Body

```json
{
  "name": "Default Project Dataset Schema",
  "baseSchemaId": "19fdf42e-6733-49f0-8745-c5d605a51b6d",
  "columns": [
    {
      "name": "question",
      "displayName": "Question",
      "dataType": "TEXT",
      "required": true,
      "description": "User question sent to chatbot target",
      "orderIndex": 1
    },
    {
      "name": "expected",
      "displayName": "Expected Answer",
      "dataType": "TEXT",
      "required": false,
      "description": "Expected answer or grading reference",
      "orderIndex": 2
    },
    {
      "name": "context",
      "displayName": "Context",
      "dataType": "TEXT",
      "required": false,
      "description": "Additional context for chatbot request",
      "orderIndex": 3
    }
  ],
  "changeReason": "Add context column from Excel import"
}
```

#### Validation Rules

```text
1. Column names must be unique within the schema.
2. orderIndex values must be unique within the schema.
3. name must match the allowed variable naming pattern.
4. question column should exist for MVP project schemas.
5. Removing a column used by active target/evaluator config should return 409 unless frontend confirms migration in a later phase.
```

#### Response `201 Created`

Returns `DatasetSchemaResponse`.

#### Error Cases

| Status | Code | Meaning |
| ------ | ---- | ------- |
| `400` | `VALIDATION_ERROR` | Invalid column definition |
| `404` | `PROJECT_NOT_FOUND` | Project not found or not visible |
| `409` | `DATASET_SCHEMA_VERSION_CONFLICT` | Schema version conflict or unsafe schema change |

---

## 8. Dataset APIs

### 8.1. List Project Datasets

```http
GET /api/v1/projects/{projectId}/datasets
```

#### Permission

```text
Project VIEW.
```

#### Query Parameters

| Name | Type | Required | Description |
| ---- | ---- | -------- | ----------- |
| `q` | string | No | Search by dataset name |
| `sourceType` | enum | No | Filter by source type |
| `page` | integer | No | Defaults to `0` |
| `size` | integer | No | Defaults to `20` |
| `sort` | string | No | Example: `createdAt,desc` |

#### Response `200 OK`

```json
{
  "items": [
    {
      "id": "500863fe-03e6-4946-ae98-0ed78d9832f8",
      "scope": "PROJECT",
      "workspaceId": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
      "projectId": "0ddf0c99-c632-4ef8-9044-8e37c940d1b9",
      "ownerUserId": null,
      "name": "VF Sales Chatbot Regression Dataset",
      "description": "Regression questions for sales chatbot",
      "sourceType": "EXCEL",
      "sourceDatasetId": null,
      "sourceProjectId": null,
      "sourceWorkspaceId": null,
      "currentVersion": {
        "id": "ca37c5c1-7eb6-43e1-8474-47355cc4cf9a",
        "version": 1,
        "schemaId": "19fdf42e-6733-49f0-8745-c5d605a51b6d",
        "schemaVersion": 2,
        "rowCount": 120,
        "status": "ACTIVE",
        "createdAt": "2026-07-02T10:00:00+07:00"
      },
      "createdBy": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
      "createdAt": "2026-07-02T10:00:00+07:00",
      "updatedAt": "2026-07-02T10:00:00+07:00"
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

### 8.2. Get Dataset Metadata

```http
GET /api/v1/datasets/{datasetId}
```

#### Permission

```text
Dataset VIEW.
```

#### Response `200 OK`

Returns `DatasetResponse`.

---

### 8.3. Update Dataset Metadata

```http
PATCH /api/v1/datasets/{datasetId}
```

Updates dataset name/description only.

This endpoint must not mutate dataset rows.

#### Permission

```text
Dataset UPDATE.
Allowed roles for project dataset: OWNER, ADMIN, MEMBER.
Denied role: VIEWER.
```

#### Request Body

```json
{
  "name": "VF Sales Chatbot Regression Dataset v2",
  "description": "Regression questions for sales chatbot after cleanup"
}
```

#### Response `200 OK`

Returns `DatasetResponse`.

#### Error Cases

| Status | Code | Meaning |
| ------ | ---- | ------- |
| `400` | `VALIDATION_ERROR` | Invalid name/description |
| `404` | `DATASET_NOT_FOUND` | Dataset not found or not visible |
| `409` | `DATASET_NAME_ALREADY_EXISTS` | Dataset name already exists in target scope |

---

### 8.4. Delete Dataset

```http
DELETE /api/v1/datasets/{datasetId}
```

Soft deletes a dataset.

#### Permission

```text
Dataset DELETE.
Allowed roles for project dataset: OWNER, ADMIN, MEMBER.
Denied role: VIEWER.
```

#### Rules

```text
1. Soft delete by setting deleted_at.
2. Do not hard delete dataset versions or rows.
3. If dataset has active/running test runs, return 409.
4. Historical test runs remain readable.
```

#### Response `204 No Content`

No response body.

#### Error Cases

| Status | Code | Meaning |
| ------ | ---- | ------- |
| `404` | `DATASET_NOT_FOUND` | Dataset not found or not visible |
| `409` | `DATASET_IN_USE_BY_TEST_RUN` | Dataset is used by active/running test run |

---

### 8.5. List Dataset Versions

```http
GET /api/v1/datasets/{datasetId}/versions
```

#### Permission

```text
Dataset VIEW.
```

#### Query Parameters

| Name | Type | Required | Description |
| ---- | ---- | -------- | ----------- |
| `status` | enum | No | `DRAFT`, `ACTIVE`, `ARCHIVED` |
| `page` | integer | No | Defaults to `0` |
| `size` | integer | No | Defaults to `20` |

#### Response `200 OK`

```json
{
  "items": [
    {
      "id": "ca37c5c1-7eb6-43e1-8474-47355cc4cf9a",
      "datasetId": "500863fe-03e6-4946-ae98-0ed78d9832f8",
      "version": 1,
      "schemaId": "19fdf42e-6733-49f0-8745-c5d605a51b6d",
      "schemaVersion": 2,
      "sourceDatasetVersionId": null,
      "importJobId": "e99ba006-c3b8-40a6-a352-f17a1a38a206",
      "rowCount": 120,
      "status": "ACTIVE",
      "createdBy": "3f8f62cf-2504-48e1-a9b4-805e87b6e8d2",
      "createdAt": "2026-07-02T10:00:00+07:00"
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

### 8.6. Get Dataset Version Detail

```http
GET /api/v1/dataset-versions/{datasetVersionId}
```

#### Permission

```text
Dataset VIEW through the parent dataset.
```

#### Response `200 OK`

Returns `DatasetVersionResponse`.

---

### 8.7. List Dataset Rows

```http
GET /api/v1/dataset-versions/{datasetVersionId}/rows
```

#### Permission

```text
Dataset VIEW through the parent dataset.
```

#### Query Parameters

| Name | Type | Required | Description |
| ---- | ---- | -------- | ----------- |
| `page` | integer | No | Defaults to `0` |
| `size` | integer | No | Defaults to `50`, max `200` |
| `q` | string | No | Search text in row JSON; MVP may implement basic search only |

#### Response `200 OK`

```json
{
  "items": [
    {
      "id": 9001,
      "datasetVersionId": "ca37c5c1-7eb6-43e1-8474-47355cc4cf9a",
      "rowIndex": 1,
      "data": {
        "question": "VF 8 có mấy phiên bản?",
        "expected": "VF 8 có các phiên bản Eco và Plus.",
        "context": "Thông tin sản phẩm VF 8",
        "category": "product_info"
      },
      "metadata": {
        "sourceRowNumber": 2,
        "sheetName": "Regression"
      },
      "createdAt": "2026-07-02T10:00:00+07:00",
      "updatedAt": "2026-07-02T10:00:00+07:00"
    }
  ],
  "page": {
    "number": 0,
    "size": 50,
    "totalElements": 120,
    "totalPages": 3
  }
}
```

---

### 8.8. List Dataset Variables

```http
GET /api/v1/dataset-versions/{datasetVersionId}/variables
```

Returns variable metadata for UI mapping.

#### Permission

```text
Dataset VIEW through the parent dataset.
```

#### Response `200 OK`

Returns `DatasetVariableResponse`.

---

## 9. Dataset Import Source APIs

### 9.1. List Importable Source Projects

```http
GET /api/v1/projects/{projectId}/dataset-import-sources/projects
```

Returns projects that the current user can view and import datasets from.

#### Permission

```text
Target project VIEW.
Source project VIEW is checked when listing source candidates.
```

#### Query Parameters

| Name | Type | Required | Description |
| ---- | ---- | -------- | ----------- |
| `q` | string | No | Search by project name |
| `page` | integer | No | Defaults to `0` |
| `size` | integer | No | Defaults to `20` |

#### Response `200 OK`

```json
{
  "items": [
    {
      "id": "846dd097-19cb-4d53-a231-75093d363987",
      "workspaceId": "4bcfb80f-b2e1-4449-ad72-5c3b4ecb42a1",
      "name": "VF Aftersales Chatbot",
      "description": "Source project with shared regression dataset",
      "datasetCount": 3,
      "createdAt": "2026-07-01T10:00:00+07:00"
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

### 9.2. List Source Project Datasets

```http
GET /api/v1/projects/{projectId}/dataset-import-sources/projects/{sourceProjectId}/datasets
```

Lists datasets from a source project for import into target project.

#### Permission

```text
1. Target project VIEW.
2. Source project VIEW.
```

#### Query Parameters

| Name | Type | Required | Description |
| ---- | ---- | -------- | ----------- |
| `q` | string | No | Search by dataset name |
| `page` | integer | No | Defaults to `0` |
| `size` | integer | No | Defaults to `20` |

#### Response `200 OK`

Returns collection of `DatasetResponse`.

---

### 9.3. List Workspace Datasets

```http
GET /api/v1/projects/{projectId}/dataset-import-sources/workspace-datasets
```

Lists workspace-scoped datasets that can be imported into the project.

#### Permission

```text
Project VIEW and workspace dataset VIEW.
```

#### Response `200 OK`

Returns collection of `DatasetResponse`.

---

### 9.4. List Personal Datasets

```http
GET /api/v1/projects/{projectId}/dataset-import-sources/personal-datasets
```

Lists current user's personal datasets that can be imported into the project.

#### Permission

```text
Project VIEW and personal dataset ownership.
```

#### Response `200 OK`

Returns collection of `DatasetResponse`.

---

## 10. Dataset Import Job APIs

### 10.1. Create Dataset Import Job

```http
POST /api/v1/projects/{projectId}/dataset-import-jobs
```

Creates an import job and returns preview/mapping information.

This endpoint supports different content types depending on source type:

```text
EXCEL:
  multipart/form-data

PROJECT_CLONE / WORKSPACE_DATASET / PERSONAL_DATASET:
  application/json
```

#### Permission

```text
Target project IMPORT_DATASET.
Allowed roles: OWNER, ADMIN, MEMBER.
Denied role: VIEWER.
```

---

### 10.1.1. Excel Import Request

#### Request `multipart/form-data`

| Field | Type | Required | Description |
| ----- | ---- | -------- | ----------- |
| `sourceType` | string | Yes | Must be `EXCEL` |
| `file` | file | Yes | Excel file: `.xlsx` or `.xls` |
| `sheetName` | string | No | Sheet to preview; if omitted backend may choose first sheet or return available sheets |
| `previewRowCount` | integer | No | Defaults to `10`, max `20` |

#### Response `201 Created`

Returns `DatasetImportJobResponse` with `status = PREVIEWED` if preview succeeds.

#### Excel Validation Rules

```text
1. File must be a valid Excel workbook.
2. File size must not exceed configured upload limit.
3. Sheet must exist if sheetName is provided.
4. Header row must contain at least one non-empty column.
5. Duplicate column names should be normalized or rejected with validation error.
6. Preview should return 10-20 rows only, not the full dataset.
```

---

### 10.1.2. Project Clone Import Request

#### Request Body

```json
{
  "sourceType": "PROJECT_CLONE",
  "sourceDatasetId": "85235d34-fbb1-452d-bafe-c9c57f961def",
  "sourceDatasetVersionId": "5ea3fa8a-d72b-4794-9e0c-aebd633f0df3",
  "previewRowCount": 10
}
```

#### Response `201 Created`

Returns `DatasetImportJobResponse` with source preview and schema compatibility result.

#### Rules

```text
1. User must have VIEW permission on source dataset.
2. Source DatasetVersion should be ACTIVE unless user explicitly selects another visible version.
3. MVP clones all rows from source version after confirm.
4. MVP does not keep a live reference to source dataset.
```

---

### 10.1.3. Workspace Dataset Import Request

#### Request Body

```json
{
  "sourceType": "WORKSPACE_DATASET",
  "sourceDatasetId": "85235d34-fbb1-452d-bafe-c9c57f961def",
  "sourceDatasetVersionId": "5ea3fa8a-d72b-4794-9e0c-aebd633f0df3",
  "previewRowCount": 10
}
```

#### Rules

```text
1. User must have VIEW permission on workspace dataset.
2. Target project must belong to a workspace where user can import datasets.
3. Confirm creates a PROJECT dataset clone in the target project.
```

---

### 10.1.4. Personal Dataset Import Request

#### Request Body

```json
{
  "sourceType": "PERSONAL_DATASET",
  "sourceDatasetId": "85235d34-fbb1-452d-bafe-c9c57f961def",
  "sourceDatasetVersionId": "5ea3fa8a-d72b-4794-9e0c-aebd633f0df3",
  "previewRowCount": 10
}
```

#### Rules

```text
1. User must own the personal dataset.
2. Confirm creates a PROJECT dataset clone in the target project.
```

---

### 10.2. Get Dataset Import Job

```http
GET /api/v1/dataset-import-jobs/{importJobId}
```

#### Permission

```text
Target project VIEW for the import job.
```

#### Response `200 OK`

Returns `DatasetImportJobResponse`.

---

### 10.3. Confirm Dataset Import Job

```http
POST /api/v1/dataset-import-jobs/{importJobId}/confirm
```

Confirms mapping and imports rows into a new project dataset and dataset version.

#### Permission

```text
Target project IMPORT_DATASET.
Allowed roles: OWNER, ADMIN, MEMBER.
Denied role: VIEWER.
```

#### Request Body

```json
{
  "datasetName": "VF Sales Chatbot Regression Dataset",
  "datasetDescription": "Imported from Excel on 2026-07-02",
  "targetSchemaMode": "EXTEND_CURRENT_SCHEMA",
  "mapping": [
    {
      "sourceColumn": "question",
      "action": "MAP_TO_EXISTING_COLUMN",
      "targetColumn": "question"
    },
    {
      "sourceColumn": "expected",
      "action": "MAP_TO_EXISTING_COLUMN",
      "targetColumn": "expected"
    },
    {
      "sourceColumn": "context",
      "action": "ADD_AS_NEW_COLUMN",
      "targetColumn": "context",
      "displayName": "Context",
      "dataType": "TEXT",
      "required": false,
      "description": "Additional context for chatbot request"
    },
    {
      "sourceColumn": "debug_note",
      "action": "IGNORE"
    }
  ]
}
```

Supported `targetSchemaMode` values:

```text
USE_CURRENT_SCHEMA
EXTEND_CURRENT_SCHEMA
CREATE_NEW_SCHEMA_VERSION
```

Supported mapping actions:

```text
MAP_TO_EXISTING_COLUMN
ADD_AS_NEW_COLUMN
IGNORE
```

#### Confirm Behavior

```text
1. Backend validates import job status is PREVIEWED.
2. Backend validates source dataset/file still available.
3. Backend validates mapping is complete and compatible.
4. If mapping adds new columns, backend creates a new PROJECT DatasetSchema version.
5. Backend creates a PROJECT Dataset row.
6. Backend creates DatasetVersion version 1 with status ACTIVE.
7. Backend inserts DatasetRows.
8. Backend sets datasets.current_version_id.
9. Backend stores import_mapping_json in dataset_versions.
10. Backend updates dataset_import_jobs.target_dataset_id.
11. Backend sets dataset_import_jobs.status = COMPLETED.
12. Backend returns DatasetResponse.
```

All confirm steps should run in a transaction where practical. If row import is large and async import is introduced later, the response contract may return `202 Accepted`; MVP should prefer synchronous import for small/medium datasets.

#### Response `201 Created`

Returns `DatasetResponse`.

#### Error Cases

| Status | Code | Meaning |
| ------ | ---- | ------- |
| `400` | `DATASET_IMPORT_MAPPING_INVALID` | Mapping is incomplete or invalid |
| `404` | `DATASET_IMPORT_JOB_NOT_FOUND` | Import job not found or not visible |
| `409` | `DATASET_IMPORT_JOB_INVALID_STATE` | Import job is not PREVIEWED |
| `409` | `DATASET_SCHEMA_INCOMPATIBLE` | Source cannot be mapped to target schema |
| `409` | `DATASET_NAME_ALREADY_EXISTS` | Dataset name already exists in target project |

---

### 10.4. Cancel Dataset Import Job

```http
POST /api/v1/dataset-import-jobs/{importJobId}/cancel
```

Cancels a pending/previewed import job.

#### Permission

```text
Target project IMPORT_DATASET.
```

#### Rules

```text
1. PENDING -> CANCELLED is allowed.
2. PREVIEWED -> CANCELLED is allowed.
3. IMPORTING may be cancelled only if backend import implementation supports safe cancellation.
4. COMPLETED cannot be cancelled.
5. FAILED does not need cancellation.
```

#### Response `200 OK`

Returns `DatasetImportJobResponse` with `status = CANCELLED`.

---

## 11. Schema Compatibility and Mapping Rules

### 11.1. Compatibility Decision

When creating an import job, backend compares source columns with target project current schema.

```text
COMPATIBLE:
  Every required target column can be mapped automatically.
  No source column requires user decision.

REQUIRES_MAPPING:
  Source and target schema differ but can be mapped by user.

INCOMPATIBLE:
  Source data cannot be safely mapped, or required columns are missing without replacement.
```

### 11.2. Auto Mapping Rule

Backend may auto-map columns when names match exactly after normalization.

Example:

```text
question -> question
expected -> expected
expected_intent -> expectedIntent if naming normalization is enabled
```

Auto mapping must be visible to UI and user can override before confirm.

### 11.3. Required Column Rule

```text
1. Required target columns must be mapped or have a default value.
2. MVP should reject confirm if a required column is missing.
3. Future versions may support default value expressions.
```

### 11.4. Added Column Rule

If user chooses `ADD_AS_NEW_COLUMN`:

```text
1. Backend creates a new DatasetSchema version in target scope.
2. New column receives orderIndex after existing columns unless explicitly provided.
3. DatasetVersion locks the new schema_id and schema_version.
```

### 11.5. Ignored Column Rule

If user chooses `IGNORE`:

```text
1. Source column is not stored in data_json.
2. Backend may keep ignored source column names inside import_mapping_json for audit.
3. Ignored values should not appear in DatasetRow.data.
```

---

## 12. Dataset Editable Workspace

Dataset is not only an uploaded file. It is an Excel-compatible QC workspace where users can inspect and edit testcase rows in a table/card/library UI before starting a run.

### 12.1. Immutable Version Rule

```text
DatasetVersion ACTIVE must not be updated in place if it has been or can be used by TestRun.
```

Do not implement APIs that patch rows inside an ACTIVE version.

Invalid API design:

```http
PATCH /api/v1/dataset-rows/{rowId}
```

The correct design is:

```text
ACTIVE version -> create DRAFT version -> edit DRAFT rows/cells -> publish DRAFT as new ACTIVE version
```

### 12.2. Dataset Version Lifecycle for Editing

Supported statuses from ERD:

```text
DRAFT
ACTIVE
ARCHIVED
```

Editing rules:

```text
1. Only DRAFT DatasetVersion can be edited.
2. Creating a draft from an ACTIVE version copies rows and schema references into a new version number.
3. DRAFT rows may be inserted, patched, bulk-upserted or deleted.
4. Publishing a DRAFT version validates schema/required columns and marks it ACTIVE.
5. Publishing updates datasets.current_version_id to the new ACTIVE version.
6. Old ACTIVE versions remain readable and continue to back historical TestRuns.
7. If a DRAFT changes schema columns, backend must create/lock a new DatasetSchema version before publishing.
8. MVP may allow only one open DRAFT per dataset per user or per dataset globally; implementation must document the chosen policy.
```

### 12.3. POST /dataset-versions/{datasetVersionId}/draft

```http
POST /api/v1/dataset-versions/{datasetVersionId}/draft
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Create or reuse an editable DRAFT version from an existing dataset version. This powers the Edit button in the dataset table UI.

Request:

```json
{
  "mode": "COPY_ROWS",
  "name": "Draft from v3",
  "reuseExistingDraft": true
}
```

Response - 201 Created or 200 OK if reused:

```json
{
  "id": "8f7e6c5d-4b3a-4a33-9c73-32a18e75e8b1",
  "datasetId": "f68f1ec8-4b60-49e3-b438-8bb5bc5ca6bb",
  "version": 4,
  "schemaId": "19fdf42e-6733-49f0-8745-c5d605a51b6d",
  "schemaVersion": 2,
  "rowCount": 250,
  "status": "DRAFT",
  "baseVersionId": "2bb81d74-8a64-4a26-b05b-41f2c041d05d",
  "createdAt": "2026-07-03T10:00:00+07:00"
}
```

Rules:

```text
1. Source version may be ACTIVE, DRAFT or ARCHIVED, but normal UI should start from current ACTIVE.
2. Backend must reject if user lacks dataset update permission.
3. Backend must reject if draft policy does not allow another open DRAFT.
4. Draft creation must not affect historical TestRuns.
```

### 12.4. PATCH /dataset-versions/{draftVersionId}/rows/{rowId}/cells

```http
PATCH /api/v1/dataset-versions/{draftVersionId}/rows/{rowId}/cells
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Patch one or more cells in one DRAFT row. This is the primary inline cell edit endpoint for Excel-like table UI.

Request:

```json
{
  "changes": [
    {
      "columnName": "question",
      "value": "VF 8 có những phiên bản nào?"
    },
    {
      "columnName": "expected",
      "value": "VF 8 có hai phiên bản Eco và Plus."
    }
  ],
  "clientRevision": 12
}
```

Response:

```json
{
  "id": 1021,
  "datasetVersionId": "8f7e6c5d-4b3a-4a33-9c73-32a18e75e8b1",
  "rowIndex": 15,
  "data": {
    "question": "VF 8 có những phiên bản nào?",
    "expected": "VF 8 có hai phiên bản Eco và Plus.",
    "category": "product_info"
  },
  "updatedAt": "2026-07-03T10:02:00+07:00",
  "revision": 13
}
```

Rules:

```text
1. draftVersionId must have status DRAFT.
2. columnName must exist in the locked schema.
3. value must match schema column dataType/required rules.
4. Backend should return the updated row so UI can patch table state.
5. clientRevision is optional MVP optimistic concurrency metadata. If provided and stale, backend may return 409 CONFLICT.
```

### 12.5. POST /dataset-versions/{draftVersionId}/rows

Insert one row into a DRAFT dataset version.

```http
POST /api/v1/dataset-versions/{draftVersionId}/rows
```

Request:

```json
{
  "insertAfterRowIndex": 15,
  "data": {
    "question": "VF 9 có mấy phiên bản?",
    "expected": "VF 9 có các phiên bản Eco và Plus.",
    "category": "product_info"
  }
}
```

Rules:

```text
1. Backend assigns a stable row_index.
2. Backend may renumber row_index after insert/delete to keep order deterministic.
3. Required columns must be present unless row is saved as incomplete draft; MVP should validate required columns on publish at minimum.
```

### 12.6. POST /dataset-versions/{draftVersionId}/rows/bulk-upsert

Bulk paste/upsert rows and cells into a DRAFT version. This supports copy/paste from Excel into the web table.

```http
POST /api/v1/dataset-versions/{draftVersionId}/rows/bulk-upsert
```

Request:

```json
{
  "mode": "PASTE_APPEND",
  "startRowIndex": 251,
  "columns": ["question", "expected", "category", "difficulty"],
  "rows": [
    ["VF 8 có mấy phiên bản?", "Eco và Plus", "product_info", "easy"],
    ["VF 9 có mấy phiên bản?", "Eco và Plus", "product_info", "easy"]
  ]
}
```

Supported modes MVP:

```text
PASTE_APPEND
PASTE_OVERWRITE_FROM_ROW
```

Rules:

```text
1. Backend must enforce max rows/cells per bulk request.
2. Backend must validate columns before applying changes.
3. Bulk paste must be all-or-nothing for MVP unless partial failure semantics are explicitly implemented.
4. Response must include affected row count and validation errors if rejected.
```

Response:

```json
{
  "affectedRows": 2,
  "insertedRows": 2,
  "updatedRows": 0,
  "rowCount": 252
}
```

### 12.7. DELETE /dataset-versions/{draftVersionId}/rows/{rowId}

Delete one row from a DRAFT version.

```http
DELETE /api/v1/dataset-versions/{draftVersionId}/rows/{rowId}
```

Rules:

```text
1. Only DRAFT versions are mutable.
2. Delete is a draft edit and does not affect old ACTIVE versions.
3. Backend may renumber row_index to preserve deterministic ordering.
```

### 12.8. POST /dataset-versions/{draftVersionId}/publish

Publish a DRAFT version as the dataset current ACTIVE version.

```http
POST /api/v1/dataset-versions/{draftVersionId}/publish
```

Request:

```json
{
  "activate": true,
  "archivePreviousCurrent": false,
  "validationMode": "STRICT"
}
```

Rules:

```text
1. Backend validates required columns and row_count.
2. Backend validates schema compatibility.
3. Backend marks draft status ACTIVE.
4. Backend updates datasets.current_version_id.
5. Previous ACTIVE versions remain readable.
6. Publishing must not modify TestRuns that already locked an older dataset_version_id.
```

### 12.9. POST /dataset-versions/{draftVersionId}/discard

Discard an unpublished DRAFT version.

```http
POST /api/v1/dataset-versions/{draftVersionId}/discard
```

Rules:

```text
1. Only DRAFT versions can be discarded.
2. Discarded draft may be hard-deleted or marked ARCHIVED depending on backend retention policy.
3. Discarding a draft must not affect datasets.current_version_id.
```

### 12.10. Dataset View Modes

The same row listing endpoint powers different frontend views:

```http
GET /api/v1/dataset-versions/{datasetVersionId}/rows?viewMode=TABLE&page=0&size=50
GET /api/v1/dataset-versions/{datasetVersionId}/rows?viewMode=LIBRARY&page=0&size=20
```

Rules:

```text
1. viewMode is a UI hint. Backend may return the same schema for TABLE and LIBRARY.
2. TABLE view should prioritize compact cell values.
3. LIBRARY/CARD view may include longer preview fields.
4. List endpoint must still be paginated.
5. Detail/edit endpoints are used when QC opens or edits a specific row.
```

---

## 13. Relationship with Test Run API

Dataset API does not start test runs.

Test Run API must:

```text
1. Validate selected dataset_version_id exists.
2. Validate dataset version status is ACTIVE.
3. Lock dataset_id.
4. Lock dataset_version_id.
5. Lock dataset_schema_id.
6. Lock dataset_schema_version.
7. Use dataset row_count for preflight.
8. Reject or warn when row_count exceeds maxRowsPerRun according to test run policy.
```

Dataset API may expose row_count to help Run Setup UI show early warnings, but final enforcement belongs to Test Run API.

---

## 14. Error Response Examples

### 14.1. Dataset Not Found

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/dataset-not-found",
  "title": "Dataset not found",
  "status": 404,
  "detail": "Dataset was not found or is not visible to the current user.",
  "instance": "/api/v1/datasets/500863fe-03e6-4946-ae98-0ed78d9832f8",
  "code": "DATASET_NOT_FOUND",
  "traceId": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
}
```

---

### 14.2. Import Mapping Invalid

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/dataset-import-mapping-invalid",
  "title": "Dataset import mapping is invalid",
  "status": 400,
  "detail": "One or more required target columns are not mapped.",
  "instance": "/api/v1/dataset-import-jobs/e99ba006-c3b8-40a6-a352-f17a1a38a206/confirm",
  "code": "DATASET_IMPORT_MAPPING_INVALID",
  "traceId": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00",
  "errors": [
    {
      "field": "mapping[0].targetColumn",
      "message": "Required target column question must be mapped."
    }
  ]
}
```

---

### 14.3. Dataset Schema Incompatible

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json
```

```json
{
  "type": "https://vfqc.vinfast.vn/problems/dataset-schema-incompatible",
  "title": "Dataset schema is incompatible",
  "status": 409,
  "detail": "Source dataset version cannot be mapped to the current project schema.",
  "instance": "/api/v1/projects/0ddf0c99-c632-4ef8-9044-8e37c940d1b9/dataset-import-jobs",
  "code": "DATASET_SCHEMA_INCOMPATIBLE",
  "traceId": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00"
}
```

---

## 15. Dataset Export

```http
POST /api/v1/dataset-versions/{datasetVersionId}/exports
```

### Purpose

Create an Excel export job for a dataset version. This supports the QC workflow where Excel remains an input/output format even when the main review/editing work happens in the web app.

### Permission

Project EXPORT_DATASET permission through dataset ownership:

```text
OWNER
ADMIN
MEMBER
```

VIEWER can view datasets but cannot create export jobs by default.

### Request body

```json
{
  "format": "XLSX",
  "includeMetadata": true,
  "columns": [
    "rowIndex",
    "question",
    "expected",
    "category",
    "difficulty"
  ],
  "filters": {
    "q": null
  }
}
```

Rules:

```text
1. XLSX is the MVP format.
2. Backend must export the immutable dataset version selected by datasetVersionId.
3. Export must not mutate dataset rows.
4. Export should preserve schema column order by dataset_schema_columns.order_index.
5. Export should include rowIndex so QC can trace rows back to the web UI.
6. Export files must not expose server-local paths.
```

### Response - 202 Accepted

```json
{
  "id": "50b6276d-768c-4ef3-b7be-35eb308f24c9",
  "exportJobId": "50b6276d-768c-4ef3-b7be-35eb308f24c9",
  "jobType": "DATASET_VERSION",
  "status": "PENDING",
  "format": "XLSX",
  "source": {
    "testRunId": null,
    "datasetVersionId": "5e95be75-6db0-4a8f-a71e-458eec884c30"
  },
  "options": {
    "includeMetadata": true,
    "columns": [
      "rowIndex",
      "question",
      "expected",
      "category",
      "difficulty"
    ]
  },
  "file": {
    "fileName": null,
    "fileSizeBytes": null,
    "downloadUrl": null,
    "expiresAt": null
  },
  "error": {
    "code": null,
    "message": null
  },
  "createdBy": "a38f0878-1ebe-49db-abf2-f6a372b6c246",
  "createdAt": "2026-07-03T10:00:00+07:00",
  "startedAt": null,
  "finishedAt": null
}
```

### Export job status

Export job status/download endpoints return the shared `ExportJobResponse` defined in the common Export Job Convention in `01-api-conventions.md`.

---

## 16. Stable Error Codes

Dataset APIs may return the following domain-specific codes:

```text
DATASET_NOT_FOUND
DATASET_NAME_ALREADY_EXISTS
DATASET_IN_USE_BY_TEST_RUN
DATASET_VERSION_NOT_FOUND
DATASET_VERSION_NOT_ACTIVE
DATASET_SCHEMA_NOT_FOUND
DATASET_SCHEMA_INCOMPATIBLE
DATASET_SCHEMA_COLUMN_INVALID
DATASET_SCHEMA_VERSION_CONFLICT
DATASET_IMPORT_JOB_NOT_FOUND
DATASET_IMPORT_JOB_INVALID_STATE
DATASET_IMPORT_FILE_INVALID
DATASET_IMPORT_SHEET_NOT_FOUND
DATASET_IMPORT_MAPPING_INVALID
DATASET_IMPORT_FAILED
DATASET_DRAFT_NOT_FOUND
DATASET_DRAFT_INVALID_STATE
DATASET_DRAFT_ALREADY_EXISTS
DATASET_ROW_NOT_FOUND
DATASET_ROW_EDIT_NOT_ALLOWED
DATASET_CELL_VALUE_INVALID
DATASET_BULK_EDIT_LIMIT_EXCEEDED
DATASET_PUBLISH_FAILED
DATASET_EXPORT_NOT_ALLOWED
DATASET_EXPORT_FAILED
EXPORT_JOB_NOT_FOUND
EXPORT_JOB_INVALID_STATE
EXPORT_JOB_EXPIRED
EXPORT_JOB_DOWNLOAD_UNAVAILABLE
```

Common error codes from `01-api-conventions.md` also apply:

```text
VALIDATION_ERROR
AUTHENTICATION_REQUIRED
PERMISSION_DENIED
RESOURCE_NOT_FOUND
CONFLICT
PAYLOAD_TOO_LARGE
UNSUPPORTED_MEDIA_TYPE
INTERNAL_ERROR
DEPENDENCY_UNAVAILABLE
```

---

## 17. Implementation Notes

```text
1. Backend should not store full uploaded Excel file content in dataset_import_jobs.preview_json.
2. preview_json should contain only limited preview rows and column metadata.
3. Full uploaded file, if temporarily stored, should use backend file storage with retention cleanup.
4. Dataset import confirmation should persist rows in deterministic row_index order.
5. DatasetRow.data must contain only mapped columns.
6. DatasetRow.metadata may contain source sheet name, source row number and import metadata.
7. JSONB indexes should not be created for every dataset_rows.data_json field until real query patterns exist.
8. APIs should avoid returning very large row pages; max page size should be enforced.
9. Import job errors should be visible to user but must not include sensitive file paths or stack traces.
10. Dataset import from another project should clone rows, not reference source rows directly.
11. Inline cell edits should patch only changed cells and return updated row data.
12. Bulk paste should enforce max row/cell count to avoid browser/server overload.
13. Publishing a DRAFT version should be transactional: validate rows, update version status and current_version_id together.
14. Dataset table/list APIs should return compact previews; full long text can be retrieved through row detail if needed later.
```

---

## 18. MVP Acceptance Criteria

Dataset API is ready for MVP when:

```text
1. Project creation produces default dataset schema with question/expected.
2. UI can fetch current project schema and variables.
3. UI can list project datasets.
4. UI can preview dataset rows by version.
5. UI can upload Excel and receive sheet/column/row preview.
6. UI can map Excel columns to existing/new/ignored schema columns.
7. Confirming Excel import creates Dataset + DatasetVersion + DatasetRows.
8. UI can list importable source projects and source datasets.
9. Confirming project clone import clones all rows into target project.
10. Schema mismatch opens mapping flow instead of failing silently.
11. ACTIVE DatasetVersion is not mutated in place.
12. QC can create a DRAFT version from an existing dataset version.
13. QC can edit cells inline in a DRAFT version.
14. QC can insert/delete rows in a DRAFT version.
15. QC can bulk paste bounded rows/cells from Excel into a DRAFT version.
16. QC can publish a DRAFT version as the dataset current ACTIVE version.
17. DatasetVersionResponse includes schema_id, schema_version and row_count.
18. Dataset import job lifecycle is visible through API.
19. VIEWER can read dataset metadata/rows but cannot import or mutate.
20. All errors use RFC 9457 problem detail format from API conventions.
```
