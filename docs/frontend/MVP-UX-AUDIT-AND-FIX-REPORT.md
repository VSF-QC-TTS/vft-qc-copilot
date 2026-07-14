# MVP UX, Contract, and Recovery Audit

Last updated: 2026-07-14

This report tracks the full audit and fix plan for the VinFast QC Copilot MVP. It is written from the perspective of an operational QC user who expects Jira-like project context, LangSmith-like run inspection, and Teams-like predictable workspace switching.

## Current user journey target

The MVP must support this complete journey without placeholder controls or hidden state loss:

```text
login
-> open or create Workspace
-> configure Workspace Secret / Provider when needed
-> open or create Project
-> configure Project metadata and readiness fields
-> configure Chatbot Target, Dataset, Evaluator
-> import or edit Dataset rows
-> publish Dataset version
-> run preflight
-> create TestRun
-> watch realtime progress
-> leave/reload/return safely
-> review MACHINE vs FINAL results
-> export Dataset or TestRun XLSX
```

## Confirmed implementation gaps

| Area | Current evidence | Risk | Fix direction |
| --- | --- | --- | --- |
| Workspace create | Backend has `POST /api/v1/workspaces`; frontend `Tạo Workspace` is a plain button. | QC cannot start from an empty account except personal workspace. | Add create dialog, mutation, invalidation, and navigate to created workspace. |
| Project create/update/archive/delete | Backend has lifecycle endpoints; frontend only lists/opens and shows placeholder create buttons. | Core flow is incomplete; project settings cannot be maintained from UI. | Add project lifecycle API/hooks/forms/settings route. |
| Dataset row pagination | Backend `GET /dataset-versions/{versionId}/rows` returns all rows through `CollectionResponse.of`; frontend renders all rows. | Large imports can degrade or appear to “lose” rows when UI becomes slow. | Add pageable backend service/controller and frontend page controls. |
| Export recovery | `ExportJobPanel` stores active job id in component state only. | Reload/out-in loses visibility of running or completed export jobs. | Add source-scoped export job listing and resume UI. |
| TestRun tab persistence | Result/matrix state is URL-based, but active tab is local state. | Deep links and reloads can hide selected result/matrix context behind overview. | Persist `tab` in URL and map result/matrix links to it. |
| UX shell density | Some pages use raw hex, wide editorial spacing, and inconsistent page structure. | App feels like mixed slices rather than a professional QC operations tool. | Standardize cockpit/list/detail layouts with semantic tokens. |
| Rule compliance | Project docs require Lucide-only; many raw hex/shadow patterns still exist. | UI may drift from docs 05/08/09 and become expensive to maintain. | Keep Lucide, remove heavy shadows/raw hex where feature code should use tokens. |

## Backend and frontend contract matrix

| Capability | Backend status | Frontend status | Action |
| --- | --- | --- | --- |
| Auth login/refresh/logout/me | Implemented under `/api/v1/auth/**`. | Implemented with guards and token refresh runtime. | Verify reload, expired refresh, no redirect loop. |
| Workspace list/get/update/delete/create | Backend implemented. | List/get/update partial; create/delete missing from user flow. | Add create/delete UI and tests; verify personal workspace delete rules. |
| Workspace members | Backend implemented. | UI exists for settings members. | Verify role restrictions and error copy. |
| Project list/get/create/update/archive/delete/setup-status | Backend implemented. | List/get/setup-status only. | Add lifecycle UI and settings. |
| Secrets | Backend implemented with masked responses. | Personal/workspace CRUD exists. | Verify no raw secret rendering/logging; add recovery/error states if missing. |
| Providers | Backend implemented with test logs. | CRUD/test exists. | Verify test history permissions, active-secret filtering, and no secret leak. |
| Chatbot Targets | Backend implemented. | CRUD/test UI exists. | Verify cURL inspect mapping, extraction config, and test failure UX. |
| Dataset import/export/edit | Backend import/export/draft lifecycle implemented; rows unpaginated in UI path. | Import/draft/publish/export UI exists; rows unpaginated. | Add row pagination, dirty-state guard, export resume. |
| Evaluators | Backend implemented. | CRUD/version UI exists. | Verify field sources/operators and version immutability UX. |
| TestRun/results/review/matrix | Backend implemented with pageable results/matrix and SSE. | Create/detail/results/review/matrix UI exists. | Persist tab, verify SSE recovery, offline banner, cancellation UX. |
| Runner internal APIs | Backend and runner implemented with Redis streams, heartbeats, idempotent events. | User sees only TestRun state. | Verify command recovery, DLQ, cancellation, network retries, and sanitized logs. |

## UX remediation model

Use one consistent operational application structure:

- Global shell: persistent left sidebar, compact top context bar, clear workspace/project switcher.
- Workspace cockpit: recent projects, active TestRuns, setup health, members/settings shortcuts.
- Project cockpit: readiness checklist, recent runs, primary setup cards, and quick actions.
- List pages: table-first for operational data; grid only when it improves scanning.
- Detail pages: metadata side panel + main inspection area.
- Long-running operations: stable inline status first, Toast only for immediate operation feedback.
- Sensitive setup: never put secrets in URL, logs, default form values, test traces, or exported artifacts.

## Verification matrix

| Scenario | Required checks |
| --- | --- |
| Login/session | Success/failure, reload, refresh, logout, expired refresh, public-only guard. |
| Workspace | Empty state, create, open, update settings, members, role restrictions, reload. |
| Project | Empty state, create, open, edit fields, archive/delete, setup-status mismatch handling. |
| Secrets/providers | CRUD, active filtering, provider test, sanitized test logs, disabled/deleted secret behavior. |
| Dataset import | File validation, Preview, mapping, confirm, duplicate name, required columns, dirty leave guard. |
| Dataset rows | Page previous/next, edit draft row, revision conflict, publish/discard, reload selected version. |
| Export | Create job, poll, reload/resume, failed/expired states, download XLSX filename. |
| TestRun | Preflight blockers/warnings, create single/comparison, realtime progress, cancel, terminal state. |
| Review/results | MACHINE vs FINAL, result detail, QC review, matrix update, pagination, deep-link reload. |
| Network/offline | Fetch failures, SSE disconnect/reconnect, fallback polling, retry copy, no duplicate rows/results. |
| Runner/Redis | Command ack after execution, stale pending recovery, DLQ, idempotent events, cancellation polling. |
| UI compliance | Vietnamese copy, canonical English terms, semantic tokens, no heavy shadows, Lucide-only icons. |

## Batch execution status

| Batch | Status | Commit target |
| --- | --- | --- |
| 1. Audit baseline + contract matrix | Completed | `docs(frontend): add mvp ux audit baseline` |
| 2. Workspace + Project lifecycle | Completed | `feat(projects): wire workspace and project lifecycle` |
| 3. Secrets + Providers setup flow | Completed | `fix(providers): harden setup flow and secret handling` |
| 4. Dataset import, rows, pagination | Completed | `feat(datasets): add row pagination and resilient import` |
| 5. Export job persistence | Completed | `feat(exports): persist export job recovery` |
| 6. TestRun, SSE, runner, Redis recovery | Completed | `fix(testruns): harden realtime run recovery` |
| 7. UX/UI professionalization | Completed | `refactor(ui): align mvp shell with design rules` |
| 8. Full verification suite | Pending | `test(mvp): cover end to end qc workflows` |
