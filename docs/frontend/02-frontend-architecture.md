# VinFast QC Copilot Frontend — Architecture

**Document:** `docs/frontend/02-frontend-architecture.md`  
**Status:** Reviewed and implementation-ready  
**Applies to:** `apps/web`  
**Last reviewed:** 2026-07-11

---

## 1. Purpose

This document defines the frontend architecture for VinFast QC Copilot.

It covers:

- application boundaries;
- runtime composition;
- route and layout structure;
- feature ownership;
- API-client architecture;
- authentication lifecycle;
- server-state management;
- SSE integration;
- forms and validation;
- pagination;
- error propagation;
- security boundaries;
- testing boundaries;
- implementation stop conditions.

This document does not define final visual styling or detailed evaluator form schemas.

---

## 2. Architectural principles

The frontend follows these principles:

```text
Backend-authoritative
Feature-oriented
Typed at boundaries
URL-addressable
Server-state first
Progressively enhanced
Security-conscious
One vertical slice at a time
```

### 2.1 Backend-authoritative

The frontend must not duplicate authoritative backend logic for:

```text
authentication
authorization
resource ownership
workspace membership
dataset version state
target readiness
evaluator validation
TestRun state transitions
result finalization
review persistence
export generation
```

The frontend may provide immediate UX validation, but backend results remain authoritative.

### 2.2 Feature-oriented

Code is grouped by business feature rather than technical layer.

Approved feature boundaries:

```text
auth
workspaces
projects
secrets
providers
chatbot-targets
datasets
evaluators
test-runs
reviews
exports
admin-users
```

### 2.3 Typed at boundaries

All external input must enter through explicit boundaries:

```text
HTTP JSON response
ProblemDetail response
empty-body security response
Blob response
SSE event stream
URL parameters
form input
local storage preference
```

Do not allow raw transport data to flow directly into presentation components.

### 2.4 URL-addressable

Workspace, project, TestRun, dataset, evaluator, and target context must be represented in the URL.

Do not rely on a global “selected project” object as the only navigation source.

### 2.5 Server-state first

Use TanStack Query as the primary state model for backend resources.

Do not introduce a global client store until a verified cross-feature problem exists.

---

## 3. System boundary

The browser communicates only with the Spring Boot API:

```text
/api/v1/**
```

The browser must not communicate directly with:

```text
Redis
PostgreSQL
runner internal APIs
Promptfoo
runner service ports
/internal/runner/**
```

Realtime TestRun updates flow as:

```text
Runner
  -> Redis Streams
  -> Spring Boot event ingestion
  -> persisted RunnerEvent
  -> Spring transaction commit
  -> backend SSE broadcast
  -> browser
```

This means SSE events visible to the browser have already been persisted by the backend.

---

## 4. Runtime composition

Recommended application entry composition:

```text
main.tsx
  -> ErrorBoundary
  -> ThemeProvider
  -> QueryClientProvider
  -> AuthProvider
  -> RouterProvider
```

Recommended layout composition:

```text
RootLayout
  -> GlobalNavigation
  -> WorkspaceContextLayout
  -> ProjectContextLayout
  -> RoutePage
```

Do not place backend fetching directly in the root entrypoint.

---

## 5. Folder architecture

```text
apps/web/
  src/
    app/
      error-boundary/
      guards/
      layouts/
      providers/
      router/

    features/
      auth/
      workspaces/
      projects/
      secrets/
      providers/
      chatbot-targets/
      datasets/
      evaluators/
      test-runs/
      reviews/
      exports/
      admin-users/

    shared/
      api/
      components/
      constants/
      hooks/
      lib/
      schemas/
      styles/
      types/
      utils/

    test/
      fixtures/
      mocks/
      setup/
```

### 5.1 `app/`

Owns application composition:

```text
router
global providers
global guards
top-level layouts
global error boundary
```

It must not own feature-specific API calls.

### 5.2 `features/`

Each feature owns:

```text
API functions
query keys
queries and mutations
feature-specific schemas
feature-specific components
feature pages
feature tests
```

Example:

```text
features/test-runs/
  api/
    test-runs.api.ts
    test-run-results.api.ts
    test-run-sse.ts
  components/
  hooks/
  pages/
  schemas/
  types/
  utils/
```

### 5.3 `shared/`

Owns reusable infrastructure:

```text
HTTP client
ProblemDetail normalization
auth token memory
single-flight refresh helper
CSRF helper
Blob download helper
generic table primitives
generic form primitives
generic status primitives
date and formatting utilities
```

Shared code must not import from feature code.

---

## 6. Router architecture

Use React Router with nested route objects.

Recommended nesting:

```text
public routes
authenticated routes
workspace routes
project routes
resource detail routes
```

Route loaders may be used only for lightweight routing prerequisites.

Primary backend data fetching remains in TanStack Query hooks to avoid maintaining two competing cache systems.

Use route params as strings and validate UUID-shaped parameters before sending requests.

Invalid route parameters should produce a local not-found state instead of unnecessary backend requests.

---

## 7. Layout architecture

### 7.1 Public layout

Used for:

```text
login
register
verify email
forgot password
reset password
```

### 7.2 Authenticated layout

Owns:

```text
global sidebar
theme control
user menu
workspace switcher
global error surface
```

### 7.3 Workspace layout

Owns:

```text
workspace context
workspace breadcrumb
workspace navigation
workspace permission context
```

### 7.4 Project layout

Owns:

```text
project context
project navigation
project breadcrumb
project setup status shortcut
project-scoped action placement
```

Layouts may read query-cached resources but must not embed business workflows.

---

## 8. API-client architecture

The API layer uses a small wrapper around `fetch`.

Do not use Axios by default.

Recommended shape:

```ts
interface ApiRequestOptions {
  method?: string;
  headers?: HeadersInit;
  body?: BodyInit | null;
  signal?: AbortSignal;
  auth?: 'required' | 'optional' | 'none';
  csrf?: 'required' | 'auto' | 'none';
  response?: 'json' | 'blob' | 'void';
}
```

The API client must handle:

```text
base URL
Authorization header
credentials
CSRF header
single-flight refresh
empty response bodies
ProblemDetail parsing
Blob responses
request cancellation
X-Request-Id extraction
```

### 8.1 Base URL

Use one environment variable, for example:

```text
VITE_API_BASE_URL
```

Recommended local default:

```text
http://localhost:8080
```

When deployed behind a reverse proxy, prefer same-origin `/api` access where possible.

### 8.2 Credentials

Use:

```ts
credentials: 'include'
```

for requests that may need the refresh cookie or CSRF cookie.

Using it globally is acceptable because the backend CORS policy explicitly allows credentials for the configured frontend origin.

### 8.3 Access token

Keep the access token in memory.

Do not persist it in:

```text
localStorage
sessionStorage
IndexedDB
URL
cookies created by frontend JavaScript
```

### 8.4 Refresh

Refresh requests must be single-flight.

When multiple authenticated requests receive 401 concurrently:

```text
first request starts refresh
other requests wait for the same promise
refresh succeeds -> replay eligible requests once
refresh fails -> clear session and route to login
```

Never recursively retry indefinitely.

### 8.5 CSRF

The backend uses Spring Security SPA CSRF handling.

The browser receives:

```text
Cookie:
  XSRF-TOKEN

Header:
  X-XSRF-TOKEN
```

Before a state-changing request that requires CSRF, ensure the CSRF cookie has been initialized through:

```text
GET /api/v1/auth/csrf
```

The frontend reads the non-HttpOnly `XSRF-TOKEN` cookie and sends it as `X-XSRF-TOKEN`.

The refresh token cookie remains HttpOnly and is never read by frontend code.

---

## 9. API module structure

Each feature API module contains transport functions only.

Example:

```ts
export async function getTestRun(testRunId: string, signal?: AbortSignal) {
  return apiClient.request<TestRunDetailResponse>(
    `/api/v1/test-runs/${testRunId}`,
    { signal }
  );
}
```

Do not include:

```text
toast calls
navigation
query invalidation
UI state
form state
permission decisions
```

Those belong in hooks or page orchestration.

---

## 10. OpenAPI usage

Use generated OpenAPI types where reliable.

Do not trust generated output blindly for:

```text
public auth security overrides
ProblemDetail responses
empty security responses
SSE event data
required response fields
nullable fields
Spring Pageable query serialization
dynamic evaluator config
```

Recommended approach:

```text
generated API DTO types
+ handwritten transport adapters
+ handwritten normalized error type
+ handwritten SSE types
```

Do not generate a monolithic client that hides retry, CSRF, and empty-body behavior.

---

## 11. Server-state architecture

Use TanStack Query.

### 11.1 Query keys

Define feature-owned query key factories.

Example:

```ts
export const testRunKeys = {
  all: ['test-runs'] as const,
  list: (projectId: string, params: TestRunListParams) =>
    [...testRunKeys.all, 'list', projectId, params] as const,
  detail: (testRunId: string) =>
    [...testRunKeys.all, 'detail', testRunId] as const,
  results: (testRunId: string, mode: ResultMode, params: ResultListParams) =>
    [...testRunKeys.all, 'results', testRunId, mode, params] as const,
};
```

### 11.2 Cache rules

Use conservative defaults:

```text
current user:
  short stale time

workspace/project metadata:
  moderate stale time

TestRun detail while running:
  short stale time

immutable snapshots:
  long stale time

completed result detail:
  moderate or long stale time

export job while pending/running:
  polling

export job terminal:
  no polling
```

### 11.3 Mutation invalidation

Examples:

```text
save review:
  invalidate result detail
  invalidate result list
  invalidate comparison matrix
  invalidate TestRun detail summary

publish dataset draft:
  invalidate dataset
  invalidate versions
  invalidate rows
  invalidate project setup status

create TestRun:
  invalidate TestRun list
  navigate to detail
```

---

## 12. Forms architecture

Use React Hook Form and Zod.

Form flow:

```text
API DTO -> form default mapper
form input -> Zod validation
submit mapper -> API request
ProblemDetail.fieldErrors -> form errors
success -> query invalidation and navigation
```

Do not bind API response objects directly to editable form state when backend and UI shapes differ.

### 12.1 Dynamic JSON

Use structured forms for known fields.

Use JSON editors only when:

```text
the backend contract is intentionally dynamic
the user is expected to understand raw JSON
the data is non-secret
the backend performs authoritative validation
```

Do not expose arbitrary JSON editing for evaluator config until the validator contract is documented.

---

## 13. Permission architecture

Create one permission helper that maps resource role and action.

Verified workspace action model:

```text
OWNER  -> VIEW, EDIT, MANAGE
ADMIN  -> VIEW, EDIT, MANAGE
MEMBER -> VIEW, EDIT
VIEWER -> VIEW
```

`PLATFORM_ADMIN` bypasses workspace permission checks.

Project permissions are inherited from the containing workspace.

Frontend permission helpers are UX helpers only.

Example:

```ts
can(role, 'EDIT')
can(role, 'MANAGE')
```

Do not encode business operations directly as scattered role checks such as:

```ts
role === 'OWNER' || role === 'ADMIN'
```

Use semantic capabilities:

```text
canView
canEdit
canManage
```

Feature-specific capabilities are then derived:

```text
canReviewResult = canEdit
canCreateTestRun = canEdit
canExport = canView
canManageMembers = canManage
```

---

## 14. Pagination architecture

The backend uses Spring Pageable for TestRun, result, and comparison endpoints.

Request:

```text
page
size
sort
```

Default backend page size:

```text
50
```

Response page metadata currently contains:

```ts
interface PageInfo {
  limit: number;
  nextCursor: string | null;
  hasMore: boolean;
}
```

For these endpoints:

```text
limit = requested page size
nextCursor = null
hasMore = backend page.hasNext()
```

The frontend must retain the current page index itself.

Approved MVP pagination:

```text
Trước
Sau
Trang N
Bộ chọn kích thước trang
```

Do not render total page counts because the backend does not return total elements or total pages.

Use URL query params:

```text
?page=0&size=50&sort=createdAt,desc
```

---

## 15. SSE architecture

SSE is implemented with fetch-based streaming, not native EventSource.

Reason:

```text
the endpoint requires Bearer Authorization header
```

The SSE client must support:

```text
Authorization header
Last-Event-ID
afterSequenceNo fallback
AbortController
manual reconnect
heartbeat comments
event name parsing
JSON data parsing
sequence deduplication
terminal shutdown
```

High-level flow:

```text
1. Fetch REST TestRun detail.
2. Store latest applied sequence number.
3. Open SSE stream.
4. Apply events in increasing sequence order.
5. Refetch authoritative REST data when needed.
6. Close on terminal event.
7. Refetch final detail/results.
```

SSE is an acceleration channel, not the sole source of truth.

---

## 16. Realtime state model

Track connection state separately from TestRun state.

```ts
type SseConnectionState =
  | 'idle'
  | 'connecting'
  | 'open'
  | 'reconnecting'
  | 'closed'
  | 'error';
```

Do not confuse:

```text
SSE disconnected
with
TestRun failed
```

A TestRun may continue successfully while the client is disconnected.

---

## 17. Error architecture

Normalize errors into one frontend type:

```ts
interface ApiError {
  status: number;
  code?: string;
  title?: string;
  detail?: string;
  messageKey?: string;
  traceId?: string;
  fieldErrors?: Array<{
    field: string;
    message: string;
  }>;
  authenticationError: boolean;
  networkError: boolean;
}
```

The normalizer must support:

```text
ProblemDetail JSON
empty 401/403 response
invalid JSON response
network failure
aborted request
```

Do not assume all errors have JSON bodies.

---

## 18. Download architecture

Export download endpoint returns binary content.

Use a dedicated helper:

```text
request Blob
read Content-Disposition when available
sanitize filename
create temporary object URL
trigger user download
revoke object URL
```

Do not put binary responses in TanStack Query cache unless a specific UX requires it.

Export job metadata may be cached; file bytes should not be.

---

## 19. Security architecture

Never place sensitive values in:

```text
query cache
URL
logs
analytics
error reports
local storage
session storage
SSE payload logs
```

Sensitive frontend forms:

```text
login password
secret create
secret rotate
raw cURL
provider secret selection
target request configuration
```

Clear plaintext fields after successful submission.

Do not display or log:

```text
refresh token
access token
Authorization header
Cookie header
raw secret values
runner token
unsanitized request/response bodies
```

---

## 20. UI language and feedback boundary

User-facing copy is Vietnamese. Established product and technical terms remain in English as defined in `08-ui-language-toast-and-agent-rules.md`.

Use one shared notification boundary:

```text
field error:
  one invalid field

form-level inline alert:
  submit failure tied to the current form

inline section error:
  table, panel, or resource failure

persistent status:
  long-running or continuing condition

dialog:
  confirmation or required decision

Toast:
  transient mutation result or background event
```

Feature modules must not call a third-party Toast package directly. Use the shared semantic notification abstraction and prevent duplicate inline/Toast feedback.

---

## 21. Testing boundaries

### Unit tests

Cover:

```text
permission helpers
error normalization
pagination parameter mapping
CSRF cookie parsing
single-flight refresh
SSE line parser
SSE event reducer
terminal event detection
filename sanitization
```

### Component tests

Cover:

```text
permission-aware actions
form field error mapping
empty states
review form
pagination controls
SSE connection indicator
```

### Integration tests

Use MSW or equivalent test transport mocks for:

```text
login
refresh success/failure
ProblemDetail
empty 401
workspace/project flow
TestRun creation
review mutation
export polling
```

### E2E

Use real backend contracts for the primary vertical slice.

---

## 22. Observability

For failed requests:

```text
prefer response ProblemDetail.traceId
fallback to response X-Request-Id
```

The frontend may show:

```text
Reference ID: <traceId>
```

Do not expose stack traces.

Current backend security-generated 401 responses may not include `X-Request-Id`, so the reference ID is optional.

---

## 23. Known architecture constraints

### 22.1 In-memory SSE fanout

Live SSE emitters are stored in memory per API instance.

Therefore, multi-instance API deployment does not yet provide distributed live fanout.

Replay from persisted events helps reconnect, but it does not guarantee immediate live delivery across instances.

### 22.2 Dynamic evaluator config

`LLM_JUDGE` and `MIXED` config remain partially dynamic.

The validator exposes required top-level keys, but the production forms remain blocked until field value types, allowed options, and `MIXED` composition rules are documented as a typed contract.

### 22.3 Pagination totals

The backend does not return total counts for current Pageable responses.

Do not design numbered total-page pagination.

---

## 24. Implementation stop conditions

Stop and document the gap when:

```text
an endpoint differs from OpenAPI
a response field changes meaning
a required field is missing
an SSE event payload differs from documented contract
a permission action is unclear
a dynamic evaluator field is undocumented
a new shared dependency is required
a backend gap would require frontend business logic invention
```
