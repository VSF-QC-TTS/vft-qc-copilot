# VinFast QC Copilot Frontend — MVP Flows and Routes

**Document:** `docs/frontend/01-mvp-flows-and-routes.md`  
**Status:** Reviewed and implementation-ready  
**Applies to:** `apps/web`  
**Last reviewed:** 2026-07-11

---

## 1. Purpose

This document defines:

- frontend MVP scope;
- route hierarchy;
- primary navigation;
- user flows;
- page responsibilities;
- loading, empty, error, and permission states;
- the first complete vertical slice.

It does not define low-level component implementation or API-client internals.

---

## 2. Product purpose

VinFast QC Copilot is an internal application for configuring and executing repeatable chatbot quality evaluations.

The frontend supports QC users in:

```text
organizing workspaces and projects
configuring secrets and LLM providers
configuring chatbot targets
building and importing datasets
defining evaluators
running single-target and comparison TestRuns
monitoring execution in realtime
reviewing machine results
overriding results through QC review
exporting datasets and TestRun results
```

The backend remains authoritative for:

```text
authentication
authorization
validation
versioning
snapshots
TestRun state transitions
result persistence
review persistence
export generation
```

---

## 3. MVP modules

The MVP includes all of the following modules.

### 3.1 Authentication

```text
Login
Logout
Current user
Refresh session
Forgot password
Reset password
Verify email
Registration when enabled
Platform-admin user invitation
```

### 3.2 Workspace

```text
List visible workspaces
Create team workspace
View workspace
Update workspace
Delete workspace
Manage workspace members
Update member roles
```

Workspace types currently exposed by the contract:

```text
PERSONAL
TEAM
```

### 3.3 Project

```text
List workspace projects
Create project
View project
Update project
Archive project
Delete project
View project setup readiness
```

### 3.4 Secrets

```text
Personal secrets
Workspace secrets
Create secret
Rename secret
Rotate secret
Disable secret
Delete secret
```

Plaintext secret values are write-only and are never displayed after submission.

### 3.5 LLM providers

```text
Personal providers
Workspace providers
Create provider
Update provider
Delete provider
Test provider
View sanitized provider test history
```

Provider types currently exposed:

```text
OPENAI_COMPATIBLE
GOOGLE_GENAI
CUSTOM_HTTP
```

### 3.6 Chatbot targets

```text
List project targets
Create target manually
Inspect pasted cURL
Bind secrets
Map dataset variables
Configure response extraction
Test target
View sanitized examples
View readiness
Update target
Disable/delete target
```

### 3.7 Datasets

```text
List project datasets
Create dataset
View metadata and versions
Create draft
Add and edit rows
Publish draft
Discard draft
Import Excel preview
Confirm Excel import
Export dataset version
```

### 3.8 Evaluators

```text
List project evaluators
Create evaluator
Update evaluator metadata
Create immutable evaluator version
View evaluator versions
Validate evaluator config
Load field sources
Load supported operators
```

Evaluator types:

```text
RULE_BASED
LLM_JUDGE
MIXED
```

### 3.9 TestRuns

```text
List project TestRuns
Configure single-target run
Configure comparison run
Run preflight
Create TestRun
View TestRun detail
Monitor progress through SSE
Request cancellation
```

TestRun statuses:

```text
CREATED
QUEUED
RUNNING
CANCEL_REQUESTED
COMPLETED
FAILED
CANCELLED
```

### 3.10 Results and QC review

```text
List TestRun results
Switch MACHINE and FINAL result modes
View testcase result detail
View comparison matrix
Create or update QC review
Apply optional status or score override
```

Result statuses:

```text
PASSED
FAILED
ERROR
JUDGE_ERROR
TIMEOUT
```

### 3.11 Export

```text
Create dataset export job
Create TestRun result export job
Poll export-job status
Download completed XLSX
Display failed, cancelled, and expired jobs
```

---

## 4. Scope principles

MVP means every listed module has a usable end-to-end workflow.

MVP does not require every module to have advanced productivity features.

Examples of acceptable MVP simplifications:

```text
basic search instead of advanced query builder
server-side pagination without virtualized infinite scrolling
simple JSON editor for verified dynamic provider options
single approved comparison matrix layout
basic review form without bulk review
polling export status instead of realtime export events
```

The frontend must not omit backend-supported critical workflows merely to reduce page count.

User-facing page names, actions, empty states, dialogs, and messages are Vietnamese. Established product and technical terms such as `Workspace`, `Project`, `Dataset`, `Chatbot Target`, `Evaluator`, `TestRun`, `API key`, `SSE`, `JSON`, and `cURL` remain in English without Vietnamese translations in parentheses. Route paths and code identifiers remain English.


---

## 5. Route strategy

Use nested routes with both workspace and project context in the URL.

Canonical pattern:

```text
/workspaces/:workspaceId/projects/:projectId/...
```

Reasons:

1. URLs are shareable.
2. Workspace and project context survive refresh.
3. Breadcrumbs are deterministic.
4. Cross-workspace resource mistakes are easier to detect.
5. Query caches can use explicit resource IDs.

Do not rely on a global “currently selected project” as the only source of navigation context.

---

## 6. Public routes

```text
/login
/register
/verify-email
/forgot-password
/reset-password
```

Behavior:

- Authenticated users navigating to `/login` should be redirected to their last valid workspace or `/workspaces`.
- Registration remains visible only when the environment supports self-registration.
- Verification and password-reset routes accept one-time tokens through query parameters.
- Tokens must be removed from long-lived navigation state after use where practical.

Example:

```text
/verify-email?token=...
/reset-password?token=...
```

---

## 7. Authenticated global routes

```text
/
/workspaces
/settings
/settings/profile
/settings/secrets
/settings/providers
/admin/users
```

Behavior:

### `/`

Resolve in this order:

```text
last accessible project
last accessible workspace
/workspaces
```

Do not redirect to a resource that the current user can no longer access.

### `/workspaces`

Displays visible personal and team workspaces.

### `/settings/secrets`

Personal secret management.

### `/settings/providers`

Personal LLM provider management.

### `/admin/users`

Visible only to users with system role `PLATFORM_ADMIN`.

Frontend visibility is not a substitute for backend permission enforcement.

---

## 8. Workspace routes

```text
/workspaces/:workspaceId
/workspaces/:workspaceId/projects
/workspaces/:workspaceId/settings
/workspaces/:workspaceId/settings/general
/workspaces/:workspaceId/settings/members
/workspaces/:workspaceId/settings/secrets
/workspaces/:workspaceId/settings/providers
```

### Workspace overview

Route:

```text
/workspaces/:workspaceId
```

Responsibilities:

```text
workspace identity
current user role
recent projects
setup shortcuts
recent activity summary when supported
```

### Workspace projects

Route:

```text
/workspaces/:workspaceId/projects
```

Responsibilities:

```text
list projects
create project
show archived state
open project
```

### Workspace settings

Routes:

```text
/workspaces/:workspaceId/settings/general
/workspaces/:workspaceId/settings/members
/workspaces/:workspaceId/settings/secrets
/workspaces/:workspaceId/settings/providers
```

Responsibilities:

```text
general:
  rename or delete workspace when allowed

members:
  list members
  add member
  change role

secrets:
  manage workspace-scoped secrets

providers:
  manage workspace-scoped providers
```

Personal workspaces may restrict member-management actions. Exact behavior must follow backend permission rules.

---

## 9. Project routes

Canonical project base:

```text
/workspaces/:workspaceId/projects/:projectId
```

Routes:

```text
/workspaces/:workspaceId/projects/:projectId
/workspaces/:workspaceId/projects/:projectId/setup
/workspaces/:workspaceId/projects/:projectId/targets
/workspaces/:workspaceId/projects/:projectId/targets/new
/workspaces/:workspaceId/projects/:projectId/targets/:targetId
/workspaces/:workspaceId/projects/:projectId/datasets
/workspaces/:workspaceId/projects/:projectId/datasets/new
/workspaces/:workspaceId/projects/:projectId/datasets/:datasetId
/workspaces/:workspaceId/projects/:projectId/datasets/:datasetId/versions/:versionId
/workspaces/:workspaceId/projects/:projectId/evaluators
/workspaces/:workspaceId/projects/:projectId/evaluators/new
/workspaces/:workspaceId/projects/:projectId/evaluators/:evaluatorId
/workspaces/:workspaceId/projects/:projectId/test-runs
/workspaces/:workspaceId/projects/:projectId/test-runs/new
/workspaces/:workspaceId/projects/:projectId/test-runs/:testRunId
/workspaces/:workspaceId/projects/:projectId/settings
```

---

## 10. Project navigation

Recommended project sidebar order and user-facing labels:

```text
Tổng quan
Thiết lập
Chatbot Target
Dataset
Evaluator
TestRun
Cài đặt
```

Workspace-level resources must remain outside project navigation:

```text
Members
Workspace Secrets
Workspace Providers
```

Personal resources must remain under global settings:

```text
Personal Secrets
Personal Providers
```

Do not duplicate the same secret/provider management page under both workspace and project contexts.

---

## 11. Project overview

Route:

```text
/workspaces/:workspaceId/projects/:projectId
```

Responsibilities:

```text
project name and description
setup readiness summary
recent TestRuns
dataset/target/evaluator counts
primary action to create a TestRun
links to unresolved setup blockers
```

The overview must not become a marketing dashboard.

It is an operational entry point.

---

## 12. Project setup

Route:

```text
/workspaces/:workspaceId/projects/:projectId/setup
```

Use backend setup status to display:

```text
dataset availability
active dataset version
active chatbot target
response extraction readiness
evaluator availability
judge provider availability
single-run readiness
comparison-run readiness
blockers
```

Rules:

1. Do not calculate authoritative readiness solely in frontend code.
2. Use backend blocker codes and messages.
3. Link each blocker to the relevant setup page.
4. Treat backend-provided `actionEndpoint` only as data; convert it to a known frontend route safely.
5. Unknown blocker codes must still be displayed.

---

## 13. Chatbot target flow

### Routes

```text
.../targets
.../targets/new
.../targets/:targetId
```

### Create flow

Recommended steps:

```text
1. Basic information
2. Request configuration
3. Secret bindings
4. Dataset mappings
5. Response extraction
6. Test and readiness
```

Support two entry methods:

```text
Paste cURL
Configure manually
```

cURL inspection flow:

```text
paste raw cURL
send to inspect endpoint
display sanitized preview
display detected secret-binding candidates
review parsed request
bind stored secrets
save target
```

Rules:

1. Never persist raw secret values in frontend state longer than necessary.
2. Never show raw Authorization or Cookie values after inspection.
3. A successful HTTP call does not necessarily mean the target is ready.
4. Readiness comes from the backend readiness endpoint.
5. Display sanitized examples only.

---

## 14. Dataset flow

### Routes

```text
.../datasets
.../datasets/new
.../datasets/:datasetId
.../datasets/:datasetId/versions/:versionId
```

### Dataset detail tabs

Recommended:

```text
Dataset row
Phiên bản
Import
Export
Cài đặt
```

### Manual dataset flow

```text
create dataset
open initial draft
add/edit rows
validate required columns
publish draft
```

### Existing dataset update flow

```text
create new draft
edit draft rows
publish or discard
```

Rules:

1. Published/active versions are immutable in the UI.
2. Editing an active dataset creates or uses a draft.
3. Display draft, active, archived, and discarded states distinctly.
4. Row editing must use the current dataset schema.
5. Manual Dataset schema CRUD is not supported in the current MVP and must not be invented.

### Excel import flow

```text
upload file
create preview job
review schema compatibility and preview
map source columns
choose schema mode
confirm import
open imported dataset
```

The MVP may use polling for import job status if the backend operation is asynchronous.

---

## 15. Evaluator flow

### Routes

```text
.../evaluators
.../evaluators/new
.../evaluators/:evaluatorId
```

### Create flow

```text
1. Name and evaluator type
2. Load available field sources
3. Configure rule-based, LLM judge, or mixed behavior
4. Validate with backend
5. Create evaluator and initial immutable version
```

### Version flow

```text
view current version
view version history
create new version
never edit an existing version in place
```

Rules:

1. Evaluator type is immutable after creation unless backend explicitly allows otherwise.
2. Evaluator versions are immutable.
3. Do not expose arbitrary JavaScript.
4. Load operators from the backend metadata endpoint.
5. Load field sources from the backend.
6. Required top-level `LLM_JUDGE` fields are known, but their fully typed values and allowed options remain pending a production form contract; `MIXED` composition rules remain pending.
7. Do not infer dynamic config fields from examples alone.

---

## 16. TestRun creation flow

### Route

```text
/workspaces/:workspaceId/projects/:projectId/test-runs/new
```

### Steps

```text
1. Choose dataset
2. Choose one or more targets/baselines
3. Choose evaluator versions
4. Configure run options
5. Run preflight
6. Review warnings and blockers
7. Create TestRun
8. Navigate to TestRun detail
```

Run-type behavior:

```text
one target:
  SINGLE_TARGET by default

multiple targets:
  COMPARISON by default
```

Backend remains authoritative for valid combinations.

Preflight behavior:

```text
blockers:
  prevent creation

warnings:
  require visibility but do not necessarily prevent creation

runnable=false:
  disable final create action
```

Do not create a TestRun first and then discover basic configuration blockers.

---

## 17. TestRun list

Route:

```text
/workspaces/:workspaceId/projects/:projectId/test-runs
```

Minimum columns:

```text
created time
run type
dataset
status
progress
started time
finished time
initiator when available
```

Minimum filters:

```text
status
run type
date range when supported
```

Pagination and sorting must be server-driven.

---

## 18. TestRun detail

Route:

```text
/workspaces/:workspaceId/projects/:projectId/test-runs/:testRunId
```

Recommended tabs:

```text
Tổng quan
Kết quả
Comparison
Cấu hình
Export
```

Tab rules:

```text
Overview:
  always available

Results:
  available for all run types

Comparison:
  visible for COMPARISON runs

Configuration:
  read-only locked snapshots

Exports:
  export job history for this run when supported
```

The canonical tab may be reflected in the URL:

```text
?tab=overview
?tab=results
?tab=comparison
?tab=configuration
?tab=exports
```

### Overview responsibilities

```text
status
progress
timestamps
targets
evaluators
summary metrics
cancel action
system failure information
realtime connection state
```

### Cancellation behavior

Allow cancel only when the backend state permits it.

After cancel request:

```text
RUNNING -> CANCEL_REQUESTED
```

Do not assume cancellation always wins.

A run may validly become:

```text
CANCEL_REQUESTED -> COMPLETED
```

if execution finished before cancellation took effect.

---

## 19. Results flow

### Result list

Use:

```text
MACHINE
FINAL
```

modes.

Rules:

1. MACHINE displays immutable machine output.
2. FINAL displays review overrides when they exist.
3. A testcase `FAILED` is a valid evaluation result.
4. A TestRun `FAILED` means run-level execution/system failure.
5. Keep those concepts visually distinct.

Minimum filters:

```text
status
reviewed/unreviewed when supported
target in comparison runs
result mode
```

### Result detail

Display:

```text
machine status and score
final status and score
request
response
extracted output
grading result
component results
token usage
latency
machine summary
error category
review
```

Potentially large or dynamic JSON should use an expandable structured viewer.

Do not render unsanitized HTML.

---

## 20. Comparison flow

Comparison is part of MVP.

Use the backend comparison-matrix endpoint.

Recommended layout:

```text
rows:
  dataset rows

columns:
  TestRun targets

cells:
  final or machine result summary
```

Cell minimum:

```text
status
score
reviewed indicator
link to result detail
```

Rules:

1. Use server-side pagination.
2. Do not load all TestCaseResults to build the matrix in the browser.
3. Preserve target order from backend.
4. Allow switching between MACHINE and FINAL modes.
5. Keep horizontal scrolling usable on tablet.

---

## 21. QC review flow

QC review is part of MVP.

Entry points:

```text
result detail
comparison matrix cell
result table row
```

Review fields:

```text
review status
optional override status
optional override score
note
```

Review statuses:

```text
APPROVED
REJECTED
NEEDS_FIX
OVERRIDDEN
```

Rules:

1. Machine result remains visible and immutable.
2. Override changes FINAL result only.
3. Do not overwrite machine status or score in frontend state.
4. After saving a review, invalidate result detail, result list, comparison matrix, and relevant summary queries.
5. Review requires the verified `EDIT` capability; `VIEWER` remains read-only.

Bulk review is not required for MVP unless explicitly added later.

---

## 22. Export flow

### Dataset export

Entry point:

```text
dataset version
```

### TestRun export

Entry point:

```text
TestRun detail
```

Flow:

```text
create export job
show pending/running state
poll job
show completed state
download XLSX
show failed/cancelled/expired state
```

Rules:

1. Download only after job status is `COMPLETED`.
2. Use Blob download handling.
3. Use backend-provided filename when safe.
4. Do not assume export generation is synchronous.
5. Stop polling terminal jobs.
6. Allow user retry by creating a new export job rather than mutating the old job.

---

## 23. Primary navigation shell

Recommended desktop shell:

```text
left sidebar
top context header
main content
optional right detail panel
```

### Global sidebar

```text
Chuyển Workspace
Project
Personal Secret
Personal LLM Provider
Quản trị, khi được phép
Menu người dùng
```

### Project sidebar

When inside a project:

```text
Tổng quan
Thiết lập
Chatbot Target
Dataset
Evaluator
TestRun
Cài đặt
```

### Header

Recommended:

```text
breadcrumbs
page title
context-aware primary action
theme control
user menu
```

Do not place every feature action in the global header.

---

## 24. Loading, empty, and error states

Every route must define all four states:

```text
loading
success
empty
error
```

### Loading

Use skeletons for page structure and tables.

Do not use a full-screen spinner for normal route data loading after the application shell is visible.

### Empty

Empty states must include:

```text
what is missing
why it matters
the next valid action
```

Examples:

```text
Chưa có Project -> Tạo Project
Chưa có Dataset -> Tạo hoặc Import Dataset
Chưa có Chatbot Target -> Thêm Chatbot Target
Chưa có Evaluator -> Tạo Evaluator
Chưa có TestRun -> Tạo TestRun đầu tiên
```

### Error

Error states must distinguish:

```text
authentication failure
permission denial
not found
validation error
conflict
network failure
server failure
```

Use inline retry for recoverable fetch failures.

### Partial failure

If one dashboard panel fails, do not discard all successfully loaded content unless the page cannot function safely.

---

## 25. Route guards

Required guard categories:

```text
authentication guard
system-role guard
workspace-access guard
project-context guard
resource-state guard
```

Rules:

1. Guards improve navigation UX.
2. Backend responses remain authoritative.
3. A hidden button is not an authorization control.
4. On 403, keep the user in a safe context and show a permission page or inline message.
5. On 404 for a nested resource, return to the closest valid parent only after clearly communicating the missing resource.
6. Do not redirect repeatedly between login and refresh states.

Use the verified `VIEW / EDIT / MANAGE` capability matrix in document `03`. `PLATFORM_ADMIN` bypasses Workspace permission checks and may have a null Workspace role.

---

## 26. First complete vertical slice

The first production-quality vertical slice must prove:

```text
Login
  -> list workspaces
  -> open workspace
  -> create/open project
  -> create chatbot target
  -> create manual dataset and publish version
  -> create RULE_BASED evaluator
  -> run preflight
  -> create SINGLE_TARGET TestRun
  -> observe realtime status
  -> open result
  -> create QC review
  -> create and download TestRun export
```

Comparison is implemented immediately after the single-target vertical slice using the same TestRun foundation.

The first vertical slice must not depend on:

```text
real customer data
real production secrets
VinFast internal network
external LLM billing
manual database edits
direct Redis commands
```

Use deterministic local fixtures where backend support exists.

---

## 27. Recommended implementation order

```text
1. App shell and routing
2. Authentication
3. Workspace and project context
4. Personal/workspace secret and provider selectors
5. Chatbot target configuration
6. Dataset creation, draft rows, publish, and import
7. Evaluator creation and validation
8. TestRun preflight and creation
9. TestRun detail and SSE
10. Results and QC review
11. Comparison matrix
12. Export jobs and download
13. Admin user invitation
14. Hardening and E2E
```

Each implementation stage must be split further in the sliced implementation plan.

---

## 28. Explicit non-goals for MVP

The following are not required unless separately approved:

```text
mobile-first operational editing
offline mode
direct runner monitoring
direct Redis inspection
arbitrary evaluator JavaScript
custom dashboard builder
bulk QC review
collaborative realtime dataset editing
advanced analytics warehouse
cross-project comparison
artifact-browser UI beyond supported exports
manual mutation of immutable TestRun snapshots
```

---

## 29. Remaining verification before later implementation

The route map, browser auth flow, permission matrix, pagination, error handling, Dataset schema behavior, and SSE contract are verified for implementation.

The following remain blocked:

```text
fully typed LLM_JUDGE production form values and allowed options
fully typed MIXED production form contract and composition rules
Dataset draft-row revision/conflict behavior
```

Do not implement blocked behavior by assumption. The current MVP must not introduce a manual Dataset schema-management route.
