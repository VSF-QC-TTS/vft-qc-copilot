# VinFast QC Copilot Frontend — Project Standard

**Document:** `docs/frontend/00-project-standard.md`  
**Status:** Implementation-ready except for the named blockers in Section 20
**Applies to:** `apps/web`  
**Product:** VinFast QC Copilot  
**Last reviewed:** 2026-07-11

---

## 1. Purpose

This document defines the engineering standard for the VinFast QC Copilot frontend.

Its purpose is to prevent developers and AI coding agents from making inconsistent choices about:

- project structure;
- framework and libraries;
- API usage;
- state management;
- styling and theming;
- security;
- testing;
- dependency changes;
- implementation workflow.

This document is normative. If an implementation conflicts with this document, stop and resolve the conflict before continuing.

---

## 2. Source of truth and instruction order

Use the following priority order:

```text
1. Verified backend Java source and actual runtime behavior.
2. Repository-level AGENTS.md instructions.
3. docs/frontend/00-project-standard.md.
4. docs/frontend/08-ui-language-toast-and-agent-rules.md.
5. docs/frontend/09-modern-library-and-deprecation-rules.md.
6. The task-relevant documents in docs/frontend/01–07.
7. Generated OpenAPI where it does not conflict with verified implementation.
8. Approved UI design assets or Figma.
9. Existing frontend implementation when it does not conflict with items 1–8.
```

Rules:

1. Do not invent endpoints, request fields, response fields, enums, permissions, or state transitions.
2. Do not call backend internal runner APIs.
3. Do not reproduce backend business rules in frontend code.
4. When OpenAPI differs from actual backend behavior, backend source and verified runtime behavior are authoritative.
5. If a required backend capability does not exist, document it as a backend gap instead of creating a frontend workaround that changes product semantics.
6. When two frontend documents conflict, stop and report the conflict before editing code.
7. Engineering documentation may use English technical wording; all user-facing UI copy follows the Vietnamese language rules in document `08`.

---

## 3. Application boundary

Frontend source location:

```text
apps/web
```

Repository shape:

```text
apps/
  api/       # Spring Boot backend
  runner/    # Node.js execution worker
  web/       # React frontend
```

The frontend communicates only with public backend APIs:

```text
/api/v1/**
```

The frontend must not communicate directly with:

```text
/internal/runner/**
Redis
PostgreSQL
Runner service ports
Promptfoo
```

Realtime TestRun updates are consumed through backend SSE:

```text
GET /api/v1/test-runs/{testRunId}/events
```

---

## 4. Runtime and package management

Use:

```text
Node.js 24 LTS
npm
package-lock.json
TypeScript strict mode
ES modules
```

Rules:

1. Use npm consistently for both frontend and runner applications.
2. Commit `package-lock.json`.
3. Use `npm ci` in CI and Docker.
4. Do not use pnpm, Yarn, or Bun unless the repository standard is deliberately changed.
5. Do not use floating dependency versions in committed manifests.
6. Pin dependency versions exactly during scaffold.
7. Dependency version resolution belongs to the scaffold/version-pinning step, not to feature implementation.
8. Do not run automated dependency fixes that silently change pinned versions.

Package version targets, compatibility rules, current API requirements, and
deprecated/removed API replacements are defined in document `09`. Re-query the
registry during Slice 0; do not treat a documentation snapshot as a lock file.

Recommended Node version files:

```text
apps/web/.nvmrc
apps/web/.node-version
```

Contents:

```text
24
```

---

## 5. Approved frontend stack

Use the following stack unless an explicit architecture decision changes it:

```text
Framework:        React + Vite
Language:         TypeScript 7 strict
Routing:          React Router
Server state:     TanStack Query
Forms:            React Hook Form
Runtime schemas:  Zod
Styling:          Tailwind CSS
UI foundation:    shadcn/ui
Icons:            Lucide
Data tables:      TanStack Table
Date utilities:   date-fns
Notifications:    Sonner
Unit tests:       Vitest
Component tests:  Testing Library
E2E tests:        Playwright
```

Charts may use Recharts only when a confirmed product flow needs a chart.

Do not add by default:

```text
Redux
MobX
Zustand
Axios
Material UI
Ant Design
Chakra UI
a second form library
a second schema-validation library
a second table library
```

A new library requires:

1. a concrete use case;
2. confirmation that the approved stack cannot reasonably handle it;
3. an explicit dependency decision;
4. tests for the new boundary where applicable.

---

## 6. Application composition

The frontend uses feature-based architecture.

Recommended layout:

```text
apps/web/
  package.json
  package-lock.json
  tsconfig.json
  vite.config.ts
  index.html
  .env.example
  .nvmrc
  .node-version

  src/
    main.tsx

    app/
      router/
      providers/
      layouts/
      guards/
      error-boundary/

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
      hooks/
      lib/
      schemas/
      types/
      utils/
      constants/
      styles/

    test/
      fixtures/
      mocks/
      setup/

  e2e/
```

Recommended feature shape:

```text
features/test-runs/
  api/
  components/
  hooks/
  pages/
  schemas/
  types/
  utils/
```

Rules:

1. A component used by one feature stays inside that feature.
2. Move a component to `shared/components` only after it has a real cross-feature use.
3. Backend communication stays in feature `api` modules or shared API infrastructure.
4. Page components orchestrate data and layout; they must not contain low-level transport code.
5. UI components must not call `fetch` directly.
6. Do not create generic `services/`, `helpers/`, or `utils/` dumping grounds.
7. Avoid circular dependencies between features.
8. Shared code must not import from feature folders.

---

## 7. TypeScript standard

Required compiler direction:

```text
strict: true
noUncheckedIndexedAccess: true
exactOptionalPropertyTypes: true
useUnknownInCatchVariables: true
forceConsistentCasingInFileNames: true
```

Rules:

1. Do not use `any` to bypass unresolved contracts.
2. Prefer `unknown` at external boundaries and validate before use.
3. Use named exports by default.
4. Use default exports only where a framework convention clearly benefits from them.
5. Do not duplicate backend enum strings across unrelated files.
6. Do not use TypeScript enums unless there is a specific interoperability reason; prefer literal unions or generated types.
7. Distinguish optional fields from nullable fields.
8. Do not use non-null assertions as a substitute for state handling.
9. Parse untrusted external data at the API boundary, not inside presentation components.

Naming:

```text
Components:       PascalCase.tsx
Hooks:            useSomething.ts
Schemas:          something.schema.ts
API modules:      something.api.ts
Query keys:       something.query-keys.ts
Types:            something.types.ts
Utilities:        something.utils.ts
Tests:            *.test.ts / *.test.tsx
```

---

## 8. Import and module rules

Use path aliases for stable top-level boundaries, for example:

```text
@/app/*
@/features/*
@/shared/*
```

Rules:

1. Prefer direct imports from concrete modules.
2. Do not create deep chains of barrel files.
3. A feature may expose a small public entrypoint if it reduces cross-feature coupling.
4. Do not import private feature internals from another feature.
5. Keep import direction predictable:

```text
app -> features -> shared
```

6. `shared` must remain independent from `features` and `app`.

---

## 9. State management

### 9.1 Server state

Use TanStack Query for:

```text
current user
workspaces
workspace members
projects
secrets
providers
chatbot targets
datasets and versions
evaluators and versions
TestRuns
results
comparison matrices
reviews
export jobs
```

Rules:

1. Do not copy server responses into a global client store.
2. Define query keys centrally per feature.
3. Mutations must explicitly invalidate or update affected queries.
4. Keep pagination, sorting, and filters in query keys.
5. Prefer backend-authoritative refetch after state-changing operations.
6. Optimistic updates are allowed only when rollback behavior is simple and the backend contract is stable.

Example query-key direction:

```ts
['auth', 'me']
['workspaces']
['workspace', workspaceId]
['workspace-members', workspaceId, filters]
['projects', workspaceId]
['project', projectId]
['test-runs', projectId, filters]
['test-run', testRunId]
['test-run-results', testRunId, mode, filters]
['comparison-matrix', testRunId, mode, filters]
```

### 9.2 Form state

Use React Hook Form.

Rules:

1. Use Zod for frontend form schemas.
2. Frontend validation improves UX; backend validation remains authoritative.
3. Map backend field errors to form fields when the error contract supports it.
4. Prevent duplicate submissions.
5. Keep server objects out of form state unless the form is actively editing them.
6. Convert API DTOs into form defaults through explicit mappers when shapes differ.

### 9.3 URL state

Use the URL for state that should survive refresh or be shareable:

```text
selected tab
page
page size
sort
filter
result mode
search text when meaningful
```

Do not place sensitive values, access tokens, secret values, raw cURL, or large draft payloads in URLs.

### 9.4 Local UI state

Use local React state for:

```text
dialog open/closed
drawer open/closed
temporary selection
sidebar collapsed state
unsubmitted UI-only draft state
```

Do not add a global state library until a verified cross-application state problem exists.

---

## 10. API client rules

The frontend API layer must provide:

```text
base URL handling
Bearer access-token injection
credentials handling
CSRF integration when required
request cancellation
JSON and Blob responses
typed success responses
normalized error mapping
single-flight token refresh
```

Rules:

1. Components must not construct endpoint URLs.
2. Components must not manually attach authentication headers.
3. Use generated OpenAPI types where the contract is reliable.
4. Keep handwritten adapters for contract gaps such as SSE, errors, cookies, and weakly typed dynamic config.
5. Do not assume every successful response is JSON.
6. Download endpoints must be handled as Blob responses.
7. Use `application/problem+json` or the verified backend error shape for normalized errors.
8. Never send a runner service token from the browser.
9. Never expose secret values after creation or rotation.
10. Do not log request bodies that may contain passwords, tokens, raw cURL, cookies, provider secrets, or target responses.

Known OpenAPI deviations are documented in `03-api-auth-permission-and-error-contract.md` and `04-test-run-realtime-sse.md`.

Handwritten adapters remain mandatory for:

```text
public auth security overrides
HttpOnly refresh-cookie behavior
SPA CSRF behavior
ProblemDetail and empty-body security errors
SSE payloads and reconnect behavior
Spring Pageable serialization
weak or incorrect required/nullability metadata
dynamic evaluator config
```

Do not regenerate a client and remove these adapters without re-verifying runtime behavior.

---

## 11. Authentication baseline

Current product assumptions:

```text
Access token:
  returned in login/refresh response;
  held in frontend memory;
  sent as Bearer token.

Refresh token:
  stored in an HttpOnly cookie;
  rotated by backend refresh;
  revoked on logout.

System roles:
  PLATFORM_ADMIN
  USER
```

Verified browser rules:

1. Do not persist access tokens in `localStorage`, `sessionStorage`, or IndexedDB.
2. Do not read the HttpOnly refresh-token cookie from JavaScript.
3. Do not infer permissions only from whether a button is visible.
4. Treat backend 401 and 403 responses as authoritative.
5. Implement refresh as a single-flight operation so concurrent 401 responses do not trigger multiple refresh requests.
6. Initialize SPA CSRF through `GET /api/v1/auth/csrf`.
7. Send the `XSRF-TOKEN` cookie value in the `X-XSRF-TOKEN` header for applicable mutations.
8. Use `credentials: 'include'` for auth and cookie-aware API requests.

---

## 12. Authorization baseline

Current OpenAPI exposes workspace roles:

```text
OWNER
ADMIN
MEMBER
VIEWER
```

Verified authorization model:

```text
System roles:
  PLATFORM_ADMIN
  USER

Workspace roles:
  OWNER
  ADMIN
  MEMBER
  VIEWER

Permission actions:
  VIEW
  EDIT
  MANAGE

Project access:
  inherited from the containing Workspace
```

Verified action matrix:

```text
OWNER:
  VIEW, EDIT, MANAGE

ADMIN:
  VIEW, EDIT, MANAGE

MEMBER:
  VIEW, EDIT

VIEWER:
  VIEW

PLATFORM_ADMIN:
  bypasses Workspace permission checks;
  currentUserRole may be null.
```

Rules:

1. UI permission checks are for user experience only.
2. Backend authorization remains authoritative.
3. Do not invent a separate Project-role model.
4. Build feature actions from semantic capabilities rather than scattered role comparisons.
5. Review requires `EDIT`; Dataset and TestRun Export require `VIEW`.

---

## 13. Styling and theming

Use semantic design tokens.

Required themes:

```text
Light
Dark
System
```

Default:

```text
Light
```

Visual direction:

```text
ChatGPT/Notion-inspired neutral surfaces
Jira-inspired information architecture and dense operational UI
VinFast-approved logo asset
```

Rules:

1. Use CSS variables and semantic Tailwind tokens.
2. Do not hardcode theme-dependent colors such as `bg-white`, `text-black`, or fixed gray borders in product components.
3. Prefer tokens such as:

```text
background
foreground
card
muted
muted-foreground
border
input
primary
primary-foreground
destructive
```

4. Status colors must be semantic and consistent.
5. TestRun `FAILED` and testcase `FAILED` must not be presented as the same concept.
6. Use the approved VinFast logo asset without altering its proportions.
7. Support appropriate logo variants for light and dark backgrounds.
8. Do not turn the application into a marketing landing page.

---

## 14. Responsive standard

Use desktop-first responsive design with tablet support.

Target behavior:

```text
Desktop/laptop:
  full operational experience.

Tablet:
  usable navigation, forms, tables, and detail views;
  complex tables may scroll horizontally.

Mobile:
  authentication and basic read-only access may remain usable;
  full dataset editing, comparison, and operational workflows are not an MVP requirement.
```

Rules:

1. Do not hide critical actions only because the viewport is narrow.
2. Tables may use horizontal scrolling before destructive information truncation.
3. Use drawers or stacked layouts for tablet detail panels.
4. Do not create a separate mobile application layout during MVP.

---

## 15. Security and sensitive data

The browser must never persist or log:

```text
passwords
refresh tokens
access tokens
secret values
raw Authorization headers
cookies
private keys
unsanitized raw cURL
unsanitized target request/response payloads
internal runner tokens
```

Rules:

1. Secret create/rotate forms clear plaintext values after successful submission.
2. Secret values must never be placed in query caches.
3. Do not render raw HTML from backend data.
4. Sanitize or escape user-controlled rich text.
5. Do not use `dangerouslySetInnerHTML` without an approved sanitization boundary.
6. Avoid storing sensitive form drafts outside component memory.
7. Frontend error reporting must redact tokens, cookies, raw cURL, and request bodies where necessary.
8. Downloaded exports are user-initiated and must respect backend authorization.

---

## 16. Accessibility baseline

Required:

```text
keyboard navigation
visible focus state
semantic labels
form error associations
button accessible names
dialog focus management
sufficient contrast
non-color status indicators
```

Rules:

1. Icon-only buttons require accessible labels.
2. Status must use text or icons in addition to color.
3. Form errors must be connected to their inputs.
4. Tables must retain meaningful headers.
5. Loading states must not trap keyboard focus.
6. Respect reduced-motion preferences.

---

## 17. Required package scripts

The scaffold must provide commands equivalent to:

```json
{
  "dev": "vite",
  "build": "tsc -b && vite build",
  "preview": "vite preview",
  "typecheck": "tsc --noEmit",
  "lint": "oxlint --type-aware --deny-warnings --report-unused-disable-directives .",
  "format": "prettier --write .",
  "test": "vitest run",
  "test:watch": "vitest",
  "test:e2e": "playwright test",
  "check": "npm run typecheck && npm run lint && npm run test && npm run build"
}
```

The exact Oxlint configuration will be pinned during scaffold. Type-aware
linting is mandatory; syntax-only linting is insufficient.

A feature slice is not complete unless the applicable checks pass.

---

## 18. AI coding rules

Every AI implementation task must follow these rules:

```text
1. Read AGENTS.md.
2. Follow document `SLICE-WORKFLOW` to load `00`, `08`, `09`, the complete
   named slice, and only the additional task-relevant documents.
3. Follow referenced sections when they define a required contract.
4. Inspect current apps/web source.
5. Inspect the current OpenAPI and relevant backend implementation.
6. Before editing, report the slice, files read, contracts checked, planned files, and blockers.
7. Implement one bounded slice at a time.
8. Follow Vietnamese UI-language and Toast rules in document 08.
9. Follow modern-library and deprecation rules in document 09.
10. Do not modify backend code unless explicitly requested.
11. Do not invent contracts.
12. Do not add dependencies without approval.
13. Add tests in the same slice.
14. Run typecheck, lint, tests, and build.
15. Show changed files and summarize decisions.
16. Stop when a documented stop condition occurs.
```

Stop conditions:

```text
Backend endpoint or field does not exist.
OpenAPI conflicts with backend code.
A new dependency is required.
A shared architecture rule must change.
Auth, permission, pagination, SSE, or error behavior is unclear.
Tests or build fail and the failure cannot be fixed inside the slice.
A feature requires missing backend support.
```

Do not bypass stop conditions with mock production behavior or hardcoded data.

---

## 19. Scaffold boundary

The initial scaffold may create:

```text
apps/web package metadata
Vite + React + TypeScript setup
Tailwind and shadcn/ui foundation
routing shell
application providers
theme provider
API client interfaces
test setup
empty feature boundaries
```

The initial scaffold must not implement:

```text
full authentication flow
production token refresh
workspace CRUD
dataset editor
evaluator editor
TestRun SSE
results comparison
QC review
export polling
```

Scaffold is followed by a human review checkpoint before feature implementation.

---

## 20. Remaining backend verification

The following items remain intentionally blocked:

```text
Fully typed production form contract for LLM_JUDGE field values and allowed options.
Fully typed production form contract and composition rules for MIXED.
Dataset row revision/conflict behavior required by the draft editor.
```

Resolved and no longer pending:

```text
refresh cookie path and attributes
SPA CSRF browser flow
ProblemDetail and empty-body 401 behavior
Workspace permission matrix
PLATFORM_ADMIN bypass behavior
page-number pagination behavior
SSE event names, envelope, heartbeat, replay, and reconnect behavior
manual Dataset schema CRUD, which is not supported in the current MVP
evaluator validation-only endpoint permission, which requires EDIT
TestRun transition matrix from TestRunStateMachine
```

Do not implement blocked behavior by guessing. All other slices may proceed according to document `07`.
