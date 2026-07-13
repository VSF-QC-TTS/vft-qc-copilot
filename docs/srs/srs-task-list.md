# SRS Task List: MVP UX and Contract Hardening

Last updated: 2026-07-14

This task list is the implementation ledger for the MVP audit/fix effort. Each batch must be independently reviewable, verified, and committed before moving to the next batch.

## Rules for every batch

- Keep backend, frontend, runner, and docs in sync when a contract changes.
- Prefer tests before implementation when behavior changes.
- Preserve user changes in the worktree.
- Use Vietnamese UI copy with canonical English product terms.
- Do not log or render raw secrets.
- End each batch with relevant verification and one conventional commit.

## Batch 1 — Audit baseline + contract matrix

Status: completed

Acceptance:

- Add an audit report that names confirmed gaps and fix paths.
- Add this task list for future context.
- Verify markdown files are tracked and diff is clean.

Evidence:

- Report: `docs/frontend/MVP-UX-AUDIT-AND-FIX-REPORT.md`
- Validation: `rtk git diff --check`

## Batch 2 — Workspace + Project lifecycle

Status: completed

Acceptance:

- Workspace create is usable from empty and non-empty list states.
- Project create is usable from workspace overview and project list.
- Project metadata edit, archive, and delete are available with permission guards.
- Frontend uses backend DTOs exactly; no invented fields.
- API contract tests cover Workspace/Project lifecycle endpoints.
- Full login -> create Workspace -> create Project -> reload E2E remains in Batch 8.

Evidence:

- `rtk npm run typecheck` in `apps/web`
- `rtk npm run lint` in `apps/web`
- `rtk npm run format:check` in `apps/web`
- `rtk npm run test` in `apps/web` passed 58 files / 181 tests before formatting
- Targeted lifecycle tests: `rtk npm run test -- src/features/projects/api/projects.api.test.ts src/features/workspaces/api/workspaces.api.test.ts`
- `rtk npm run build` in `apps/web`

## Batch 3 — Secrets + Providers setup flow

Status: completed

Acceptance:

- Secret/provider setup is clear from workspace settings and setup checklist.
- Provider test works only with ACTIVE secrets.
- No raw secret is shown, logged, cached in URL, or exported.
- Error and empty states are stable inline where persistent context matters.

Evidence:

- Provider collection and test-log schemas now include backend `page` metadata.
- Provider test contract verifies frontend sends only model/prompt, not secret material.
- Project overview links directly to Workspace Secret and Workspace Provider setup.
- E2E provider mocks now return `CollectionResponse` shape.
- `rtk npm run typecheck` in `apps/web`
- `rtk npm run lint` in `apps/web`
- `rtk npm run format:check` in `apps/web`
- Targeted provider API test: `rtk npm run test -- src/features/providers/api/providers.api.test.ts`
- `rtk npm run build` in `apps/web`

## Batch 4 — Dataset import, rows, pagination

Status: completed

Acceptance:

- Dataset rows are paginated end to end.
- Import Preview/mapping/confirm handles dirty navigation and validation clearly.
- Dataset draft edit handles revision conflicts.
- Dataset export recovery remains in Batch 5.

Evidence:

- Backend dataset row listing now uses Spring `Pageable`.
- Frontend dataset detail renders row page controls instead of all rows.
- Import Preview/mapping now warns before tab unload or Quay lại navigation.
- Frontend dataset API pagination test added.
- Backend `Phase4DatasetIntegrationTest` covers row pagination metadata.
- `rtk npm run typecheck` in `apps/web`
- `rtk npm run lint` in `apps/web`
- `rtk npm run format:check` in `apps/web`
- Targeted dataset API test: `rtk npm run test -- src/features/datasets/api/datasets.api.test.ts`
- Backend dataset integration: `rtk bash mvnw -Dtest=Phase4DatasetIntegrationTest test`
- `rtk npm run build` in `apps/web`

## Batch 5 — Export job persistence

Status: pending

Acceptance:

- Dataset/TestRun export panels list recent source-scoped jobs.
- Reload/out-in resumes active job polling.
- Completed job can be downloaded after reload.
- Expired/failed/cancelled states are explicit.

## Batch 6 — TestRun, SSE, runner, Redis recovery

Status: pending

Acceptance:

- TestRun active tab is URL-persistent.
- SSE reconnect/fallback polling is visible and avoids duplicate UI state.
- Runner command/event flow is verified for retries, DLQ, heartbeat, cancellation, and idempotency.
- Offline/network errors degrade without corrupting UI state.

## Batch 7 — UX/UI professionalization

Status: pending

Acceptance:

- Pages follow the operational shell: sidebar, context topbar, cockpit/list/detail patterns.
- Feature code uses semantic tokens instead of scattered raw hex.
- Heavy default shadows are removed where docs ban them.
- Lucide remains the only icon library unless project docs are explicitly changed.

## Batch 8 — Full verification suite

Status: pending

Acceptance:

- Unit, integration, and E2E coverage matches the audit verification matrix.
- Frontend `npm run check`, Playwright E2E, backend Maven tests, runner check, and `git diff --check` pass or documented blockers are resolved.
