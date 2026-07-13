# Frontend Implementation Context

Last updated: 2026-07-14

This file is a compact handoff for a fresh coding agent that has no conversation history.
It does not replace `AGENTS.md` or `docs/frontend/SLICE-WORKFLOW.md`; read those first.

## Required workflow

For each frontend slice:

1. Read `AGENTS.md`, `docs/frontend/SLICE-WORKFLOW.md`, `docs/frontend/00`, `08`, `09`, and the full selected slice in `docs/frontend/07-sliced-implementation-plan.md`.
2. Load additional docs routed by `SLICE-WORKFLOW.md`.
3. Verify backend DTOs/controllers/services/tests before writing UI.
4. Do not invent backend contracts, permissions, SSE events, evaluator configs, or generic JSON editor UX.
5. Implement one bounded slice unless the user explicitly asks for a batch.
6. Run checks from `apps/web`.
7. Commit each completed slice separately using `type(scope): short summary`.

Use `rtk` for verbose commands.

## Current frontend progress

Completed commits:

- `8011c84 feat(web): implement workspace and project navigation`
- `7877274 feat(web): add auth workspace project flow`
- `77285c7 feat(web): add workspace permissions and members`
- `dc627d5 feat(web): add secret management`
- `ca802c2 feat(web): add llm provider management`
- `ddb366a feat(web): add chatbot target management`
- `7ce59d1 fix(api): enforce dataset row revisions`
- `858b882 feat(web): add dataset version lifecycle`

Current implemented user journey:

```text
login/register/reset/verify
→ workspace list/detail/settings/members
→ project list/detail overview
→ workspace/personal secrets
→ workspace/personal LLM providers
→ project chatbot targets
→ project datasets with draft/version/row lifecycle
```

## Important established contracts

### Notifications

Use `notifySuccess`, `notifyError`, `notifyInfo`, or `notifyWarning` from `shared/lib/notifications`.
Do not call Sonner directly in feature code.

### Permissions

Use `PermissionGuard` and current workspace role.
VIEWER should see read-only state where possible; EDIT actions should be disabled or hidden according to existing page patterns.

### Dataset lifecycle

Frontend Slice 10 maps backend dataset APIs:

- `GET /api/v1/projects/{projectId}/dataset-schema`
- `GET /api/v1/projects/{projectId}/datasets`
- `POST /api/v1/projects/{projectId}/datasets`
- `GET /api/v1/datasets/{datasetId}`
- `DELETE /api/v1/datasets/{datasetId}`
- `GET /api/v1/datasets/{datasetId}/versions`
- `POST /api/v1/datasets/{datasetId}/drafts`
- `GET /api/v1/dataset-versions/{versionId}/rows`
- `POST /api/v1/dataset-versions/{versionId}/rows`
- `PATCH /api/v1/dataset-rows/{rowId}`
- `POST /api/v1/dataset-versions/{versionId}/publish`
- `POST /api/v1/dataset-versions/{versionId}/discard`

Row patch requests must send:

```json
{
  "cells": {},
  "expectedRevision": 0
}
```

Backend returns `409` when the row revision is stale.

Dataset schema UI is display-only. Do not add manual schema CRUD unless backend and docs explicitly add it.

## Next recommended batches

### Batch 1 — Slice 11

Slice 11 is `Excel dataset import`.
Implement it separately because import preview/mapping fields are stored as backend `Map<String,Object>` and must be verified from service/tests.

Expected backend APIs:

- `POST /api/v1/projects/{projectId}/dataset-import-jobs`
- `GET /api/v1/dataset-import-jobs/{importJobId}`
- `POST /api/v1/dataset-import-jobs/{importJobId}/confirm`

Verify before editing:

- `DatasetImportJobResponse`
- `ConfirmDatasetImportJobRequest`
- `DatasetImportMappingDto`
- `DatasetImportStatus`
- `DatasetImportTargetSchemaMode`
- `DatasetImportMappingAction`
- exact shapes of `preview`, `schemaCompatibility`, and `mapping`

Slice 11 acceptance criteria:

- 10 default preview rows
- 20 maximum preview rows
- 500 maximum imported rows
- every source column mapped or ignored
- new schema version warning shown

### Batch 2 — Slice 12

Rule-based evaluators.
Do not begin until `EvaluatorConfigValidator` and `TestRunEvaluatorValidator` are verified.

### Batch 3 — Slice 13

LLM Judge and Mixed evaluator.
Do not implement a generic guessed JSON form.

### Later batches

- Slice 14 + 15: TestRun preflight/creation and REST-first detail can be considered together if evaluator contracts are stable.
- Slice 16: SSE parser/transport should be isolated and heavily tested.
- Slice 17: integrate SSE into TestRun detail after Slice 16 passes.
- Slice 18–20: results, QC review, comparison matrix.
- Slice 21: export jobs/downloads.
- Slice 22–24: admin, hardening, MVP E2E.

## Standard frontend verification

Run from `apps/web`:

```bash
rtk npm run format
rtk npm run check
rtk git diff --check
```

Targeted tests are useful before the full check, for example:

```bash
rtk npm run test -- dataset
```

Known build note: Vite may report a chunk-size warning. Prior slices accepted this warning when typecheck, lint, tests, and build pass.
