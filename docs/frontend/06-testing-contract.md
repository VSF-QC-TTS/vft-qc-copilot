# VinFast QC Copilot Frontend — Testing Contract

**Document:** `docs/frontend/06-testing-contract.md`  
**Status:** Reviewed and implementation-ready  
**Applies to:** `apps/web`  
**Last reviewed:** 2026-07-11

---

## 1. Purpose

This document defines the testing standard for the VinFast QC Copilot frontend.

It covers:

- test levels;
- ownership;
- required tooling;
- mocking rules;
- contract tests;
- authentication tests;
- permission tests;
- SSE tests;
- form tests;
- table and pagination tests;
- E2E scenarios;
- CI quality gates;
- flakiness rules;
- test-data rules.

---

## 2. Testing principles

```text
Test behavior, not implementation details.
Use real contracts at boundaries.
Keep tests deterministic.
Prefer small tests, but protect critical flows end to end.
Do not mock away the risk being tested.
```

The testing pyramid:

```text
many unit tests
many focused component tests
fewer integration tests
small set of high-value E2E tests
```

---

## 3. Approved tools

```text
Unit/integration runner:
  Vitest

Component testing:
  Testing Library

Browser E2E:
  Playwright

HTTP mocking:
  MSW or equivalent approved fetch-level mock

Type checking:
  TypeScript compiler

Static analysis:
  Oxlint with type-aware linting

Formatting:
  Prettier
```

Do not introduce a second overlapping test runner.

---

## 4. Required commands

The frontend must expose scripts equivalent to:

```json
{
  "typecheck": "tsc --noEmit",
  "lint": "oxlint --type-aware --deny-warnings --report-unused-disable-directives .",
  "test": "vitest run",
  "test:watch": "vitest",
  "test:e2e": "playwright test",
  "build": "tsc -b && vite build",
  "check": "npm run typecheck && npm run lint && npm run test && npm run build"
}
```

CI must use:

```text
npm ci
```

---

## 5. Test ownership

Tests live near the code they protect.

Examples:

```text
features/auth/api/auth.api.test.ts
features/auth/hooks/use-session.test.tsx
features/test-runs/api/sse-parser.test.ts
features/test-runs/hooks/use-test-run-sse.test.tsx
shared/api/error-normalizer.test.ts
```

E2E tests live under:

```text
apps/web/e2e/
```

Do not place all tests in one global directory.

---

## 6. Unit-test scope

Unit tests are required for pure logic and transport helpers.

Minimum required areas:

```text
query key factories
permission helpers
route builders
URL parameter parsing
ProblemDetail normalization
empty 401 normalization
CSRF cookie parsing
single-flight refresh
request retry rules
pagination parameter mapping
filename sanitization
status label mapping
SSE parser
SSE sequence reducer
terminal event detection
backoff calculation
form DTO mappers
```

---

## 7. Component-test scope

Component tests are required for reusable and feature-critical UI.

Minimum required areas:

```text
PageHeader actions
PermissionGuard behavior
ProblemDetailAlert
field error rendering
pagination controls
status badges
connection status
empty states
destructive confirmation
review form
preflight blocker display
result mode switch
comparison cell rendering
Toast viewport and variants
Toast pause/resume and dismissal
Vietnamese user-facing labels
technical-term preservation
```

Test through accessible roles and labels.

Avoid querying CSS classes or internal component state.

---

## 8. Integration-test scope

Integration tests exercise:

```text
component
query hooks
API client
mocked HTTP boundary
routing
```

Minimum required integration flows:

```text
session bootstrap
login success/failure
refresh success/failure
workspace list and open
project list and open
target create
dataset create and publish
evaluator create
TestRun preflight and create
result review
export job polling
```

---

## 9. Contract fixtures

Maintain fixtures matching current backend DTOs.

Recommended location:

```text
src/test/fixtures/
```

Fixture categories:

```text
auth
workspaces
projects
targets
datasets
evaluators
test-runs
results
reviews
exports
errors
sse
```

Rules:

1. Fixtures use realistic UUIDs and timestamps.
2. Fixtures include nullable and optional cases.
3. Fixtures include platform-admin role with `currentUserRole = null`.
4. Fixtures include empty 401 responses.
5. Fixtures include ProblemDetail with `fieldErrors`.
6. Fixtures do not contain real secrets.
7. Fixtures must be updated when backend DTOs change.

---

## 10. API client tests

Required cases:

### 10.1 Successful JSON

```text
parses JSON
returns typed data
captures request ID
```

### 10.2 204 response

```text
does not parse JSON
returns void
```

### 10.3 Blob response

```text
returns Blob
does not parse JSON
```

### 10.4 ProblemDetail

```text
parses code
parses detail
parses messageKey
parses traceId
parses fieldErrors
```

### 10.5 Empty 401

```text
does not throw JSON parse error
marks authenticationError=true
preserves status=401
```

### 10.6 Invalid JSON error body

```text
falls back safely
does not mask original status
```

### 10.7 Abort

```text
does not show global error
does not trigger refresh
```

---

## 11. Authentication tests

### 11.1 Login

Test:

```text
CSRF initialized
credentials included
access token stored in memory
current user cached
redirect applied
```

Failure cases:

```text
invalid credentials
email not verified
disabled user
validation error
network failure
```

### 11.2 Refresh

Test:

```text
refresh cookie is not read by frontend
credentials included
new access token replaces old token
waiting requests resume
```

### 11.3 Single-flight refresh

Required concurrency test:

```text
three requests receive 401
one refresh request occurs
all eligible requests retry once
```

Failure concurrency test:

```text
one failed refresh
all waiting requests fail consistently
session clears once
```

### 11.4 Logout

Test:

```text
active SSE streams abort
logout request sent
session cache cleared
access token cleared
login route shown
```

---

## 12. CSRF tests

Required:

```text
GET /auth/csrf initializes token
XSRF-TOKEN cookie is read
X-XSRF-TOKEN header is sent on mutation
credentials are included
token is refreshed once after CSRF failure
no infinite retry occurs
```

Do not test HttpOnly refresh-cookie access because frontend must never read it.

---

## 13. Permission tests

Test semantic capability helpers.

Verified matrix:

| Role | VIEW | EDIT | MANAGE |
|---|---:|---:|---:|
| OWNER | Yes | Yes | Yes |
| ADMIN | Yes | Yes | Yes |
| MEMBER | Yes | Yes | No |
| VIEWER | Yes | No | No |

Test platform admin separately.

Required feature cases:

```text
VIEWER can export
VIEWER can run preflight
VIEWER cannot create TestRun
VIEWER cannot review
MEMBER can review
MEMBER cannot manage members
ADMIN can manage members
OWNER can manage members
PLATFORM_ADMIN works with null workspace role
```

Frontend permission tests do not replace backend authorization tests.

---

## 14. Form tests

Each production form requires tests for:

```text
required fields
basic format validation
server field error mapping
non-field ProblemDetail
submission loading state
duplicate submission prevention
successful reset/redirect
preservation after server error
```

Sensitive form tests:

```text
secret plaintext clears after success
secret plaintext is not placed in URL
secret plaintext is not included in error logging
```

---

## 15. Dataset tests

### 15.1 Dataset creation

```text
creates initial dataset
opens draft
shows default schema columns
```

### 15.2 Draft lifecycle

```text
create draft
edit rows
publish
discard
active version is read-only
```

### 15.3 Import

Test:

```text
XLSX only
default preview count 10
maximum preview count 20
maximum rows 500
sheet selection
mapping every source column
ignore mapping
map existing column
add new column
schema-version warning
confirm import
```

Use small generated test spreadsheets only in E2E or integration tests where needed.

---

## 16. Target tests

Required:

```text
manual target configuration
cURL inspection
secret binding
dataset mapping
response extraction
test target
readiness blockers
disabled target behavior
sanitized example display
```

Security tests:

```text
raw Authorization value is not rendered
raw Cookie value is not rendered
secret values are not cached
```

---

## 17. Evaluator tests

Rule-based evaluator tests:

```text
field source selection
operator selection
expected value
weight
required
ordering
backend validation errors
immutable version display
```

`LLM_JUDGE` and `MIXED` production-form tests must verify the typed evaluator
contract:

```text
LLM_JUDGE rules=[]
LLM_JUDGE config includes rubric, inputTemplate, scoreScale, threshold, passCondition=SCORE_AT_LEAST_THRESHOLD, outputSchema=VFQC_JUDGE_V1
MIXED includes at least one rule plus llmJudge config
MIXED weighted rule aggregation uses ALL_REQUIRED_AND_WEIGHTED_THRESHOLD with SCORE_AT_LEAST_THRESHOLD
optional default judge provider/model are omitted when blank
```

---

## 18. TestRun creation tests

Required:

```text
dataset selection
target selection
evaluator version selection
maxRows option
run-type default
comparison requires at least two targets
single target requires exactly one target
preflight blockers prevent create
warnings remain visible
VIEWER sees preflight but cannot create
successful create navigates to detail
creation response shows QUEUED
```

---

## 19. SSE parser tests

The parser must be tested independently from React.

Required byte/chunk cases:

```text
one complete event
event split across chunks
multiple events in one chunk
multi-line data
comment heartbeat
CRLF and LF
trailing partial line
UTF-8 characters split across chunks
empty data
invalid JSON
```

Required field cases:

```text
id
event
data
comment
unknown field
```

---

## 20. SSE sequence tests

Required:

```text
sequence 1 after 0 applies
duplicate sequence ignores
older sequence ignores
sequence gap triggers recovery
wrong TestRun ID ignores
terminal event closes
heartbeat does not change sequence
```

Test event name and JSON `eventType` mismatch handling.

Recommended behavior:

```text
record diagnostic
prefer validated payload only
refetch REST
```

---

## 21. SSE reconnect tests

Required:

```text
network close reconnects
30-minute-style close reconnects when non-terminal
401 triggers one refresh
refresh success reconnects from last sequence
refresh failure stops and clears session
403 stops
404 stops
5xx backs off
intentional abort does not reconnect
route leave aborts
logout aborts
visibility regain refetches
```

Backoff tests must use fake timers.

---

## 22. SSE event-to-query tests

Required:

```text
RUN_STARTED refetches detail
RUN_PROGRESS updates progress
TESTCASE_COMPLETED invalidates results with debounce
TESTCASE_ERROR invalidates results with debounce
RUN_COMPLETED closes and refetches final data
RUN_FAILED closes and shows run-level failure
RUN_CANCELLED closes and refetches partial results
RUN_LOG does not trigger expensive refetch
```

High-volume event test:

```text
100 testcase events
bounded number of query invalidations
no unbounded event history
```

---

## 23. Result and review tests

Required:

```text
MACHINE mode shows machine values
FINAL mode applies override
machine values remain visible
reviewed indicator appears
VIEWER cannot edit
MEMBER can edit
save invalidates detail/list/matrix
note is sanitized by backend contract
```

Do not fake final status by mutating machine result.

---

## 24. Comparison tests

Required:

```text
target order preserved
dataset row order preserved
MACHINE/FINAL mode
missing result cell
reviewed cell
horizontal layout
page navigation
no client-side all-result matrix construction
```

---

## 25. Export tests

Required:

```text
VIEWER can create export
default XLSX
default TestRun mode FINAL
job polling
completed download
failed job
cancelled job
expired job
Blob download
safe filename
object URL revoked
```

Do not store file bytes in long-lived query cache.

---

## 26. Route tests

Required:

```text
unauthenticated route redirect
post-login return route
workspace route
project route
invalid UUID
not found
permission denied
platform admin with null workspace role
tab query parameter
pagination query parameter
```

---

## 27. Accessibility tests

Automated checks should include:

```text
labels
roles
focus
dialog accessibility
button names
table headers
status text
form error associations
```

Run an accessibility checker where practical, but manual keyboard review remains required.

---

## 28. Theme tests

Required:

```text
light
dark
system
preference persistence
no reload
status contrast
logo variant
```

Visual snapshots may be used selectively.

Do not make large snapshot files the primary assertion method.

---

## 29. UI language and Toast tests

Required UI-language tests:

```text
primary page labels and actions are Vietnamese
canonical technical terms remain in English
no translated technical term is appended in parentheses
backend enum values are mapped to Vietnamese user-facing status labels
raw enum values remain available only in developer-oriented views where required
```

Required Toast tests:

```text
success duration
information duration
warning duration
error duration
hover pauses auto-dismiss
keyboard focus pauses auto-dismiss
hover-out resumes remaining time
maximum three visible Toasts
deduplication within the configured window
close button keyboard access
Vietnamese close-button and notification-region accessible names
polite live-region behavior without nested competing live regions
top-right desktop and top-center narrow-screen placement
reduced-motion behavior
no sensitive values rendered
```

Feedback-channel tests:

```text
fieldErrors render inline and do not create Toast
form-level errors render inline and do not create duplicate Toast
persistent SSE reconnect state remains visible
disconnect warning appears at most once
recovery information appears at most once
normal background refetch does not show Toast
```

---

## 30. E2E environment

Preferred E2E environment:

```text
frontend
real Spring Boot API
real PostgreSQL
real Redis
controlled runner or deterministic runner stub
```

The frontend must never directly manipulate the database as part of normal test flow.

Test setup may use approved backend test-support endpoints only if explicitly designed for tests.

---

## 31. Required MVP E2E scenarios

### E2E-01 Authentication

```text
login
refresh
reload session
logout
```

### E2E-02 Workspace and project

```text
create/open workspace
create/open project
verify navigation context
```

### E2E-03 Single TestRun vertical slice

```text
create workspace secret
create provider when needed
create target
test target
create manual dataset
publish dataset
create rule-based evaluator
run preflight
create TestRun
observe QUEUED
observe RUNNING through SSE
observe progress
observe testcase result
observe COMPLETED
open result
create review
export FINAL XLSX
```

### E2E-04 Comparison

```text
configure two targets
create comparison TestRun
open comparison matrix
switch MACHINE/FINAL
review one result
verify matrix updates
```

### E2E-05 Permission

```text
VIEWER reads and exports
VIEWER cannot edit or review
MEMBER edits and reviews
MEMBER cannot manage members
ADMIN manages members
```

### E2E-06 SSE recovery

```text
disconnect stream
reconnect with cursor
replay missed events
avoid duplicates
finish terminal state
```

---

## 32. Test-data rules

Use deterministic names:

```text
e2e-workspace-<run-id>
e2e-project-<run-id>
e2e-dataset-<run-id>
```

Rules:

1. Never use production data.
2. Never use real internal secrets.
3. Clean up test-created resources when possible.
4. Tests must not depend on execution order.
5. Tests must be parallel-safe or explicitly serialized.
6. Use stable clocks or broad timestamp assertions.

---

## 33. Flakiness policy

A flaky test is a defect.

Do not solve flakiness with arbitrary sleeps.

Use:

```text
event-based waits
locator assertions
network response waits
fake timers
backend status polling with timeout
```

Retries may exist in CI only as a diagnostic safety net, not as the normal passing mechanism.

---

## 34. Coverage expectations

Coverage percentage is not the primary quality target.

Mandatory logic must be covered:

```text
auth refresh
CSRF
error normalization
permissions
SSE parser
SSE sequence/reconnect
review finalization
export download
```

Uncovered critical logic blocks merge even when global percentage passes.

---

## 35. CI quality gate

Every pull request must pass:

```text
npm ci
npm run typecheck
npm run lint
npm run test
npm run build
```

Run Playwright for:

```text
main branch
release branch
frontend-critical pull requests
or every pull request when infrastructure is stable
```

No merge with:

```text
failing tests
skipped critical tests
TypeScript errors
lint errors
build errors
```

---

## 36. Test review checklist

A feature slice is test-complete when:

```text
happy path tested
permission path tested
loading state tested
empty state tested
error state tested
backend validation tested
query invalidation tested
accessibility basics tested
Vietnamese UI copy follows document 08
technical terms are not translated in parentheses
Toast/inline feedback is not duplicated
no sensitive fixture data exists
```
