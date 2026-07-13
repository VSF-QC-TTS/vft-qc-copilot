# VinFast QC Copilot Frontend — Sliced Implementation Plan

**Document:** `docs/frontend/07-sliced-implementation-plan.md`  
**Status:** Partially implementation-ready; see Section 8 for blocked behavior
**Applies to:** `apps/web`  
**Last reviewed:** 2026-07-11

---

## 1. Purpose

This document breaks frontend implementation into bounded, reviewable slices.

Each slice defines:

- goal;
- scope;
- expected files;
- backend dependencies;
- acceptance criteria;
- required tests;
- stop conditions.

The plan is designed for implementation by developers or AI coding agents without allowing uncontrolled full-project generation.

---

## 2. Execution rules

For every slice:

```text
1. Follow `docs/frontend/SLICE-WORKFLOW.md`.
2. Read `AGENTS.md`, documents `00`, `08`, `09`, and the complete named slice.
3. Load only the additional documents routed by the workflow for this slice.
4. Inspect current `apps/web`, touched backend contracts, OpenAPI, and git diff.
5. Report the pre-edit gate and stop on unresolved blockers.
6. Implement only the approved scope and dependency set.
7. Add tests in the same slice.
8. Run typecheck, lint, tests, and build.
9. Report changed files and unresolved gaps, then stop at the slice boundary.
```

Do not start the next slice automatically before review.

---

## 3. Global stop conditions

Stop implementation when:

```text
backend contract differs from documentation
a required endpoint is missing
a response shape is ambiguous
a permission action is unclear
auth/CSRF behavior differs at runtime
an SSE payload differs from documented fields
a new dependency is required
a test requires production secrets
a backend gap would require invented frontend behavior
```

---

## 4. Definition of done for each slice

A slice is complete when:

```text
scope is implemented
loading/empty/error/success states exist
permissions are represented
tests pass
TypeScript strict passes
lint passes
build passes
no sensitive data is logged
documentation is updated when contract details changed
```

---

# Phase A — Foundation

## Slice 0 — Scaffold `apps/web`

### Goal

Create the frontend project shell without implementing product workflows.

### Scope

```text
React
Vite
TypeScript 7 strict
npm
Tailwind CSS
shadcn/ui foundation
React Router
TanStack Query
React Hook Form
Zod
Sonner
Vitest
Testing Library
Playwright
Oxlint with type-aware linting
Prettier
```

### Expected files

```text
apps/web/package.json
apps/web/package-lock.json
apps/web/tsconfig*.json
apps/web/vite.config.ts
apps/web/index.html
apps/web/.env.example
apps/web/.nvmrc
apps/web/.node-version
apps/web/src/main.tsx
apps/web/src/app/**
apps/web/src/shared/styles/**
apps/web/src/test/**
apps/web/e2e/**
```

### Acceptance criteria

```text
npm ci works
npm run dev starts
npm run typecheck passes
npm run lint passes
npm run test passes
npm run build passes
Playwright smoke test opens the app
latest stable compatible package set is verified and pinned exactly per document 09
deprecated, removed, and project-forbidden legacy audit in document 09 passes
```

### Tests

```text
root render smoke test
theme provider smoke test
router not-found smoke test
```

### Stop conditions

```text
package versions cannot be pinned
repository structure differs materially
Node/npm standard conflicts with root repository
```

---

## Slice 1 — Design tokens and application shell

### Goal

Create the visual shell and theme infrastructure.

### Scope

```text
Light/Dark/System
semantic tokens
global typography
sidebar shell
top context header
responsive tablet shell
VinFast logo integration
generic page layout
Toast viewport and visual primitives
Vietnamese navigation labels
```

### Expected components

```text
ThemeProvider
AppShell
GlobalSidebar
AppHeader
PageHeader
Breadcrumbs
UserMenu placeholder
WorkspaceSwitcher placeholder
NotificationToaster
```

### Acceptance criteria

```text
theme switches without reload
theme preference persists
desktop shell works
tablet shell works
keyboard navigation works
Toast variants follow documents 05 and 08
user-facing shell copy is Vietnamese
technical terms remain in English without parenthetical translation
no product API calls yet
```

### Tests

```text
theme behavior
sidebar toggle
accessible navigation labels
Toast pause, dismissal, stacking, and reduced motion
Vietnamese label mapping
```

---

## Slice 2 — Shared API infrastructure

### Goal

Implement the verified browser API boundary.

### Scope

```text
fetch wrapper
base URL
credentials
Bearer injection
CSRF initialization
X-XSRF-TOKEN
ProblemDetail parsing
empty 401 parsing
Blob support
X-Request-Id extraction
AbortSignal
```

### Expected files

```text
shared/api/api-client.ts
shared/api/api-error.ts
shared/api/csrf.ts
shared/api/token-store.ts
shared/api/refresh-coordinator.ts
shared/api/download.ts
```

### Acceptance criteria

```text
JSON, void, and Blob responses supported
empty 401 does not cause JSON parse error
ProblemDetail normalized
request ID captured
CSRF header sent on mutations
```

### Tests

```text
all API-client cases from testing contract
```

### Stop conditions

```text
runtime CSRF behavior differs
security responses change shape
CORS blocks configured frontend origin
```

---

# Phase B — Authentication and navigation context

## Slice 3 — Authentication session

### Goal

Implement login, refresh, logout, and current user.

### Scope

```text
/login
session bootstrap
single-flight refresh
/auth/me
logout
protected route guard
return-to route
```

### Excluded

```text
registration
forgot password
reset password
verify email
```

### Acceptance criteria

```text
login works
reload restores session through refresh
concurrent 401 causes one refresh
failed refresh clears session
logout aborts authenticated work
```

### Tests

```text
login
empty 401
refresh concurrency
logout
route guard
```

---

## Slice 4 — Remaining auth flows

### Goal

Implement registration and account recovery.

### Scope

```text
/register
/verify-email
/forgot-password
/reset-password
ADMIN_ONLY behavior
SELF_VERIFY behavior
```

### Acceptance criteria

```text
registration entry hidden in ADMIN_ONLY
verification token submitted safely
forgot-password response does not reveal account existence
reset handles admin invitation token flow
```

### Tests

```text
registration disabled
verification success/failure
forgot password generic message
reset success/failure
```

---

## Slice 5 — Workspace and project navigation

### Goal

Establish workspace/project context.

### Scope

```text
workspace list
workspace switcher
workspace overview
project list
project overview
nested routes
breadcrumbs
platform admin null workspace role handling
```

### Acceptance criteria

```text
URL contains workspace and project IDs
refresh preserves context
invalid resource shows safe error
platform admin can enter workspace with null currentUserRole
```

### Tests

```text
workspace list
project navigation
route parameters
not found
platform admin context
```

---

# Phase C — Permissions and settings resources

## Slice 6 — Permission framework and workspace members

### Goal

Implement semantic capability checks and member management.

### Scope

```text
VIEW/EDIT/MANAGE helpers
PermissionGuard
workspace general settings
member list
add member
change role
```

### Acceptance criteria

```text
VIEWER read-only
MEMBER no member management
ADMIN/OWNER manage members
PLATFORM_ADMIN bypass supported
backend 403 handled
```

### Tests

```text
permission matrix
member actions
hidden/disabled actions
403 fallback
```

### Known backend caution

Current backend does not show special protection for:

```text
last owner
self-demotion
personal workspace membership restrictions
```

Do not invent frontend-only enforcement.

---

## Slice 7 — Secrets

### Goal

Implement personal and workspace secret management.

### Scope

```text
personal secret list
workspace secret list
create
rename
rotate
disable
delete
```

### Acceptance criteria

```text
plaintext never returned
plaintext clears after success
VIEWER reads workspace metadata
MEMBER can edit workspace secrets
personal secret restricted to owner/platform admin
```

### Tests

```text
sensitive value lifecycle
permissions
duplicate name error
rotate flow
```

---

## Slice 8 — LLM providers

### Goal

Implement personal and workspace provider management.

### Scope

```text
list
create
update
delete
secret selection
provider type
base URL
default model
config
provider test when endpoint is available in contract
```

### Acceptance criteria

```text
scope shown
active secret required
dynamic config handled only through verified fields
no secret plaintext shown
```

### Tests

```text
personal/workspace permissions
secret selector
validation
provider test
```

### Stop condition

```text
provider config validator fields are not representable from verified API metadata
```

---

# Phase D — Project setup resources

## Slice 9 — Chatbot targets

### Goal

Implement target list, create, edit, test, and readiness.

### Scope

```text
target list
manual configuration
cURL inspection
secret bindings
dataset mappings
response extraction
test target
examples
readiness
status change
delete
```

### Acceptance criteria

```text
raw cURL sanitized
secret candidates bound explicitly
ACTIVE readiness required for TestRun
test output sanitized
VIEWER read-only
MEMBER can edit/test
```

### Tests

```text
manual create
cURL create
secret binding
dataset mapping
extraction
test
readiness blockers
```

---

## Slice 10 — Dataset base and version lifecycle

### Goal

Implement dataset list, create, rows, drafts, publish, and discard.

### Scope

```text
dataset list
create
detail
schema display
draft creation
row editor
versions
publish
discard
delete
```

### Acceptance criteria

```text
active version read-only
draft editable
default question/expected schema displayed
publish archives prior active version
schema management UI not invented
```

### Tests

```text
create
draft
row edit
publish
discard
read-only active version
```

### Stop condition

```text
row API revision/conflict behavior is unclear
```

---

## Slice 11 — Excel dataset import

### Goal

Implement the verified import workflow.

### Scope

```text
upload XLSX
sheet selection
preview
column mapping
ignore/map/add column
schema-mode review
confirm import
```

### Acceptance criteria

```text
10 default preview rows
20 maximum preview rows
500 maximum imported rows
every source column mapped or ignored
new schema version warning shown
```

### Tests

```text
compatible import
mapping-required import
new column
invalid mapping
row-limit error
```

---

## Slice 12 — Rule-based evaluators

### Goal

Implement evaluator identity, immutable versions, and RULE_BASED configuration.

### Scope

```text
evaluator list
create
metadata update
delete
version list
create version
field sources
operators
rules
backend validation
```

### Acceptance criteria

```text
existing versions read-only
new changes create version
custom JavaScript unavailable
human-readable rule summary
```

### Tests

```text
rule creation
field source
operator
expected value
weight
required
version immutability
validation error
```

### Stop condition

```text
EvaluatorConfigValidator or TestRunEvaluatorValidator contradicts current form model
```

---

## Slice 13 — LLM Judge and Mixed evaluator

### Goal

Implement `LLM_JUDGE` and `MIXED`.

### Prerequisite

Before implementation, verify:

```text
EvaluatorConfigValidator
TestRunEvaluatorValidator
provider/model fields
prompt/rubric fields
threshold/score fields
mixed composition rules
```

### Scope

```text
typed evaluator schemas for RULE_BASED, LLM_JUDGE, MIXED
LLM_JUDGE form fields: rubric, inputTemplate, scoreScale, threshold, outputSchema, passCondition, optional default provider/model
MIXED form fields: reusable rule builder plus LLM_JUDGE form fields
version creation preserves immutable evaluator type
frontend utility tests for LLM_JUDGE and MIXED payloads
backend/docs enum contract aligned to EvaluatorConfigValidator
```

### Stop condition

Do not implement generic guessed JSON forms as production UX.

---

# Phase E — TestRun execution

## Slice 14 — TestRun preflight and creation

### Goal

Implement TestRun authoring.

### Scope

```text
dataset selection
targets
evaluators
run type
maxRows
preflight
blockers
warnings
create
```

### Acceptance criteria

```text
VIEWER can preflight
VIEWER cannot create
single run exactly one target
comparison at least two targets
creation navigates to detail
QUEUED comes from creation response
```

### Tests

```text
preflight
blockers
warnings
permissions
single/comparison validation
create
```

---

## Slice 15 — TestRun detail without SSE

### Goal

Create the REST-first TestRun detail experience.

### Scope

```text
overview
status
progress
snapshots
targets
evaluators
summary metrics
cancel action
tabs
```

### Acceptance criteria

```text
terminal and non-terminal states render
cancel permission enforced
CANCEL_REQUESTED explanation shown
MACHINE/FINAL entry points available
```

### Tests

```text
status rendering
cancel
VIEWER read-only
run-level failure distinction
```

---

## Slice 16 — SSE parser and transport

### Goal

Implement fetch-based SSE infrastructure independently from page UI.

### Scope

```text
stream parser
Authorization header
cursor headers
heartbeat
sequence handling
AbortController
reconnect backoff
auth refresh
terminal close
```

### Acceptance criteria

```text
all parser/reconnect contract tests pass
no native EventSource
no duplicate event application
gap triggers recovery
```

### Tests

All SSE parser, sequence, and reconnect tests from `06-testing-contract.md`.

---

## Slice 17 — TestRun realtime integration

### Goal

Connect SSE transport to TestRun detail and query cache.

### Scope

```text
connection indicator
RUN_STARTED
RUN_PROGRESS
testcase event invalidation
terminal event refetch
visibility regain recovery
fallback REST polling while disconnected
```

### Acceptance criteria

```text
SSE state separate from run state
high-volume events debounced
terminal stream closes
REST remains authoritative
```

### Tests

```text
event-to-query behavior
high-volume invalidation
disconnect/reconnect
terminal finalization
```

### Deployment note

Current backend live fanout is in-memory per API instance.

Do not claim distributed realtime support.

---

# Phase F — Results, review, and export

## Slice 18 — Results list and detail

### Goal

Implement MACHINE and FINAL result views.

### Scope

```text
result list
pagination
mode switch
result detail
JSON viewers
error categories
latency
token usage
```

### Acceptance criteria

```text
machine/final distinction clear
page state in URL
no total-page UI
unsafe HTML not rendered
```

### Tests

```text
modes
pagination
detail
missing optional fields
large JSON
```

---

## Slice 19 — QC review

### Goal

Implement review and override.

### Scope

```text
review status
override status
override score
note
save
invalidate result/list/matrix
```

### Acceptance criteria

```text
machine value preserved
FINAL changes after override
MEMBER can review
VIEWER cannot review
```

### Tests

```text
permissions
save
invalidation
machine/final display
```

---

## Slice 20 — Comparison matrix

### Goal

Implement server-driven comparison.

### Scope

```text
matrix endpoint
target columns
dataset rows
MACHINE/FINAL
cell details
pagination
horizontal scroll
```

### Acceptance criteria

```text
target order preserved
no client-side full matrix construction
review updates reflected
tablet usable
```

### Tests

```text
ordering
missing cells
mode switch
pagination
review indicator
```

---

## Slice 21 — Export jobs and downloads

### Goal

Implement dataset and TestRun exports.

### Scope

```text
create job
poll status
terminal handling
download Blob
safe filename
expired/failed/cancelled states
```

### Acceptance criteria

```text
VIEWER can export
default XLSX
default FINAL for TestRun
polling stops on terminal
object URL revoked
```

### Tests

```text
job lifecycle
permissions
Blob
filename
terminal states
```

---

# Phase G — Admin and hardening

## Slice 22 — Admin users

### Goal

Implement platform-admin user invitation and management supported by current API.

### Scope

Derived from current admin API contract.

### Acceptance criteria

```text
only PLATFORM_ADMIN sees route
403 handled
invitation/reset flow integrates with auth routes
```

---

## Slice 23 — Cross-feature hardening

### Goal

Improve consistency after primary flows work.

### Scope

```text
global error boundary
route error pages
query retry policy
accessibility review
tablet review
theme review
sensitive logging review
performance review
```

### Acceptance criteria

```text
all primary pages have four states
keyboard navigation works
no sensitive data in logs/cache/URL
bundle reviewed
```

---

## Slice 24 — MVP E2E completion

### Goal

Automate the complete MVP workflows.

### Required scenarios

```text
authentication
workspace/project
single TestRun vertical slice
comparison
permission roles
SSE recovery
export
```

### Acceptance criteria

```text
deterministic
no arbitrary sleeps
no production secrets
CI-compatible
cleanup implemented
```

---

## 5. Recommended review checkpoints

Human review after:

```text
Slice 0
Slice 2
Slice 5
Slice 9
Slice 12
Slice 14
Slice 17
Slice 21
Slice 24
```

These checkpoints protect architecture and contract boundaries.

---

## 6. Dependency policy during implementation

A new dependency requires a written note containing:

```text
problem
existing-stack limitation
candidate dependency
bundle/runtime impact
maintenance status
security considerations
alternatives
```

Do not add dependencies opportunistically.

---

## 7. Documentation update policy

Update frontend docs when:

```text
backend endpoint changes
permission action changes
error contract changes
SSE event changes
pagination changes
evaluator config changes
route model changes
```

Do not let implementation silently become the only source of truth.

---

## 8. Remaining backend verification

Pre-Slice 13 verification checklist:

```text
EvaluatorConfigValidator
TestRunEvaluatorValidator
fully typed LLM_JUDGE field values and allowed options
fully typed MIXED field values, aggregation, and composition rules
```

Resolved in Slice 13:

```text
EvaluatorConfigValidator validates typed ruleBased, llmJudge and mixed config enums.
LLM_JUDGE uses rubric, inputTemplate, scoreScale, threshold, SCORE_AT_LEAST_THRESHOLD, VFQC_JUDGE_V1, optional default provider/model.
MIXED uses ruleBased aggregation/passCondition plus llmJudge config.
```

Before finalizing Dataset row conflict UX:

```text
Dataset draft-row revision/conflict implementation
```

These items do not block foundation, auth, Workspace, Project, Secret, Provider, Chatbot Target, Dataset list/import/versioning, TestRun transport, results, QC review, Comparison, or Export slices. Slice 10 must stop only if row editing requires an unverified conflict contract.
