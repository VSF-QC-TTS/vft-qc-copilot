# VinFast QC Copilot Frontend — Modern Library and Deprecation Rules

**Document:** `docs/frontend/09-modern-library-and-deprecation-rules.md`  
**Status:** Mandatory  
**Applies to:** all code, tests, and configuration under `apps/web`  
**Version snapshot verified:** 2026-07-11  
**Last reviewed:** 2026-07-11

---

## 1. Purpose

This document prevents implementation agents from generating APIs remembered
from old React, React Router, Zod, TanStack, Tailwind, Vite, testing, and linting
versions.

It distinguishes:

```text
UPSTREAM REMOVED:
  the installed major no longer provides the API

UPSTREAM DEPRECATED:
  the maintainer marks the API deprecated or scheduled for removal

PROJECT-FORBIDDEN LEGACY:
  the API may still compile, but this repository prohibits it because a
  current stack-specific API is clearer, safer, or less error-prone
```

An API does not become acceptable merely because TypeScript still resolves it.

---

## 2. Latest-version policy

Use the latest stable **compatible set**, not a collection of independently
resolved `latest` tags.

Rules:

1. Resolve versions only during the scaffold or an explicitly approved
   dependency-upgrade task.
2. Query the npm registry immediately before editing `package.json`.
3. Read official release, migration, and peer-dependency documentation for
   every major version selected.
4. Use stable releases only. Do not use `alpha`, `beta`, `rc`, `canary`,
   `nightly`, `next`, or experimental packages without explicit approval.
5. Pin exact versions in `package.json` and commit `package-lock.json`.
6. Run `npm install` or `npm ci`, then inspect the installed versions with
   `npm ls`; the lock file, not this snapshot table, is authoritative after
   scaffold.
7. If the latest stable packages have incompatible peer requirements, stop and
   report the conflict. Do not use `--force`, `--legacy-peer-deps`, `overrides`,
   or silent downgrades to hide it.
8. Do not copy version numbers from model memory, examples, blog posts, or old
   lock files.
9. A major-version upgrade is its own reviewed change. Do not mix it into a
   feature slice.

### 2.1 Verified stable snapshot

The following npm `latest` or latest stable versions were verified on
2026-07-11. Re-query them when Slice 0 actually pins dependencies.

| Package | Verified stable version | Role |
|---|---:|---|
| `react` | `19.2.7` | Runtime |
| `react-dom` | `19.2.7` | Runtime |
| `typescript` | `7.0.2` | Type-check CLI; see Section 2.2 |
| `@typescript/typescript6` | `6.0.2` compatibility line | Temporary compiler API for tools |
| `vite` | `8.1.4` | Build/dev server |
| `@vitejs/plugin-react` | `6.0.3` | React transform/refresh |
| `react-router` | `8.1.0` | Routing |
| `@tanstack/react-query` | `5.101.2` | Server state |
| `@tanstack/react-table` | `8.21.3` | Stable table adapter; v9 was prerelease |
| `react-hook-form` | `7.81.0` | Form state |
| `@hookform/resolvers` | `5.4.0` | Zod/RHF integration |
| `zod` | `4.4.3` | Runtime schemas |
| `tailwindcss` | `4.3.2` | Styling |
| `shadcn` | `4.13.0` | Component-source CLI |
| `lucide-react` | `1.24.0` | Icons |
| `date-fns` | `4.4.0` | Date utilities |
| `vitest` | `4.1.9` | Unit/component test runner |
| `@testing-library/react` | `16.3.2` | Component tests |
| `@testing-library/dom` | `10.4.1` | Required Testing Library peer |
| `@testing-library/user-event` | `14.6.1` | User interactions |
| `@testing-library/jest-dom` | `6.9.1` | DOM assertions for Vitest |
| `@playwright/test` | `1.61.1` | Dev-only browser E2E |
| `msw` | `2.15.0` | Dev-only HTTP mocks |
| `eslint` | `10.6.0` | Linting |
| `typescript-eslint` | `8.63.0` | TypeScript ESLint integration |
| `prettier` | `3.9.4` | Formatting |

`@types/react`, `@types/react-dom`, Vite/Tailwind integration packages, DOM
test environment, and ESLint React plugins must be resolved and pinned with
versions compatible with this set during scaffold. Do not guess their versions.

### 2.2 TypeScript 7 compatibility exception

TypeScript 7.0 is the latest stable type-checker but does not ship a
programmatic compiler API. Tools such as `typescript-eslint` may still need the
TypeScript 6 API.

The scaffold must follow the current official TypeScript compatibility guidance
and prove both CLI type-checking and type-aware ESLint. The expected temporary
shape is:

```json
{
  "devDependencies": {
    "typescript": "npm:@typescript/typescript6@6.0.2",
    "typescript-7": "npm:typescript@7.0.2"
  }
}
```

The exact alias/bin arrangement must be tested in the generated scaffold. Stop
if `npm run typecheck`, editor diagnostics, Vite, Vitest, or type-aware ESLint
do not agree. Do not disable type-aware lint rules to claim compatibility.

Remove the compatibility package only after official tooling supports the
TypeScript 7 programmatic API or no longer requires it.

---

## 3. Mandatory API verification procedure

Before using an unfamiliar or version-sensitive library API:

```text
1. Inspect apps/web/package.json and package-lock.json.
2. Inspect the installed package version and its exported types.
3. Read the official documentation for that installed major.
4. Read its current migration/deprecation page.
5. Search apps/web for the old and replacement API.
6. Use the replacement consistently.
7. Add a test that exercises the behavior.
```

Stop if the installed version differs from the documented target or if official
documentation and installed types disagree.

Never solve a deprecated-API error by:

```text
using any
using @ts-ignore
using @ts-expect-error without a verified external typing defect
adding eslint-disable
copying an old Stack Overflow answer
switching to a legacy package
downgrading a dependency inside a feature slice
```

---

## 4. React 19.2 rules

### 4.1 Imports and JSX

The modern JSX transform is mandatory.

Forbidden:

```tsx
import React from 'react';
import * as React from 'react';

export function Page(): React.ReactElement {
  return <main />;
}
```

The namespace import is allowed only when an external API genuinely requires a
namespace value. JSX itself is not such a reason.

Preferred:

```tsx
import { useMemo, useState } from 'react';
import type { ComponentProps, ReactNode, Ref } from 'react';

interface PageProps {
  children: ReactNode;
}

export function Page({ children }: PageProps) {
  const [open, setOpen] = useState(false);
  return <main data-open={open}>{children}</main>;
}
```

Rules:

1. Use named runtime imports for hooks and runtime functions.
2. Use `import type` for React types.
3. Do not import React solely to use JSX or a `React.*` type namespace.
4. Do not use `React.FC` or `React.FunctionComponent`; declare props explicitly
   and use a named function component.
5. Do not add explicit component return types unless they protect a public
   boundary or resolve a real inference problem.

`import React` is not upstream-deprecated; it is PROJECT-FORBIDDEN LEGACY under
the automatic JSX transform.

### 4.2 Form submit typing

`FormEvent` is not upstream-deprecated. It is nevertheless PROJECT-FORBIDDEN
LEGACY for React Hook Form submission because RHF owns the submit lifecycle.

Forbidden in RHF forms:

```tsx
import type { FormEvent } from 'react';

function onSubmit(event: FormEvent<HTMLFormElement>) {
  event.preventDefault();
}
```

Required:

```tsx
import type { SubmitHandler } from 'react-hook-form';

type FormValues = z.input<typeof formSchema>;

const onSubmit: SubmitHandler<FormValues> = async (values) => {
  await mutation.mutateAsync(values);
};

<form onSubmit={form.handleSubmit(onSubmit)} noValidate>
```

For a rare native form not managed by RHF, prefer inferred event types. If a
named public handler type is genuinely needed, use a type-only import:

```tsx
import type { FormEventHandler } from 'react';

const handleNativeSubmit: FormEventHandler<HTMLFormElement> = (event) => {
  event.preventDefault();
};
```

Never import the React runtime solely to write `React.FormEvent`.

### 4.3 Removed and deprecated React APIs

| Forbidden API/pattern | Status | Required replacement |
|---|---|---|
| `ReactDOM.render` | Removed | `createRoot` from `react-dom/client` |
| `ReactDOM.hydrate` | Removed | `hydrateRoot` from `react-dom/client` |
| `unmountComponentAtNode` | Removed | `root.unmount()` |
| `findDOMNode` | Removed | DOM ref |
| `act` from `react-dom/test-utils` | Removed/deprecated | `act` from `react`, normally Testing Library |
| `react-test-renderer` | Deprecated | Testing Library |
| `react-test-renderer/shallow` | Removed | Behavior tests with Testing Library |
| function-component `propTypes` | Removed/ignored | TypeScript props |
| function-component `defaultProps` | Removed | Default parameter values |
| string refs | Removed | Ref callback or ref object |
| `React.createFactory` | Removed | JSX |
| legacy context APIs | Removed | `createContext`/`useContext` |
| reading `element.ref` | Deprecated | `element.props.ref`, preferably avoid element introspection |
| `forwardRef` in new internal components | Deprecated direction | Receive `ref` as a prop in React 19 |
| `useFormState` from `react-dom` | Renamed | `useActionState` when React Actions are actually used |

Use `forwardRef` only when a verified third-party primitive still requires it.
Document that compatibility boundary. Do not reintroduce it into newly owned
shadcn primitives.

### 4.4 Root creation

Preferred:

```tsx
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

const container = document.getElementById('root');

if (!container) {
  throw new Error('Root element not found');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
```

Do not silence the nullable container with `!`.

---

## 5. Zod 4 rules

Use Zod 4 top-level string formats. Method equivalents remain available only
for migration and are deprecated.

### 5.1 String formats

Forbidden:

```ts
z.string().email()
z.string().url()
z.string().uuid()
z.string().datetime()
z.string().date()
z.string().time()
z.string().ip()
```

Required:

```ts
z.email()
z.url()
z.uuid()
z.iso.datetime()
z.iso.date()
z.iso.time()
z.ipv4()
z.ipv6()
```

Use `z.string()` only for an unconstrained string or for string transforms and
length/pattern constraints that are not a promoted string format.

### 5.2 Object, enum, error, and inference APIs

| Forbidden Zod 3/legacy API | Status in Zod 4 | Required replacement |
|---|---|---|
| `z.nativeEnum(MyEnum)` | Deprecated | `z.enum(MyEnum)` |
| `schemaA.merge(schemaB)` | Deprecated | `schemaA.extend(schemaB.shape)` or `safeExtend` |
| `.strict()` / `.passthrough()` for new schemas | Legacy | `z.strictObject()` / `z.looseObject()` |
| `ZodError.errors` | Removed | `ZodError.issues` |
| `error.format()` | Deprecated direction | `z.treeifyError(error)` |
| `error.flatten()` | Deprecated direction | `z.flattenError(error)` |
| `invalid_type_error` / `required_error` | Removed legacy customization | unified `error` parameter |
| schema-level `errorMap` | Legacy | unified `error` parameter |
| `z.promise()` | Deprecated | await input, then parse resolved value |
| `z.record(valueSchema)` | Old signature | provide key and value schemas explicitly |

Use `safeExtend` when the base object has refinements. Do not use object spread
or `extend` in a way that silently discards refinements.

### 5.3 Input and output types

Transforms can make input and output types different. Use them deliberately:

```ts
const schema = z.object({
  maxRows: z.coerce.number().int().positive(),
});

type FormInput = z.input<typeof schema>;
type ApiOutput = z.output<typeof schema>;
```

For RHF with a transforming schema, type input and output explicitly when
inference is ambiguous:

```ts
useForm<z.input<typeof schema>, unknown, z.output<typeof schema>>({
  resolver: zodResolver(schema),
});
```

Do not cast the resolver or parsed result to force incompatible types.

---

## 6. React Hook Form 7 rules

Required baseline:

```text
useForm with typed field values
zodResolver for the single form schema
handleSubmit for submission
formState.errors for errors
register spread for native inputs
Controller render prop only for controlled third-party components
useWatch for render-driven observation
subscribe for side-effect-only observation
```

Forbidden legacy patterns:

| Forbidden | Required replacement |
|---|---|
| Manual `FormEvent` submission for an RHF form | `handleSubmit(SubmitHandler<T>)` |
| `watch(callback)` | `subscribe` |
| `watch()` of the entire form in render without need | targeted `useWatch` |
| `errors` directly returned from `useForm` | `formState.errors` |
| `<input name="x" ref={register}>` | `<input {...register('x')}>` |
| Legacy `Controller as={...}` | `Controller render={({ field }) => ...}` |
| `useFieldArray({ keyName })` in new code | use generated `field.id` |
| `setValue('rows', wholeArray)` | `replace`, `update`, `append`, or `remove` |
| Duplicating Zod validation in `register` | one Zod schema plus UI-only hints |
| Reading private control internals | documented hooks/APIs |

Do not place the full `form` object in effect dependencies or pass it through
unrelated layers when destructured stable methods are sufficient.

Backend validation remains authoritative. Map backend `fieldErrors` with
`setError`; keep remaining non-field errors in the form-level alert defined by
document `08`.

---

## 7. React Router 8 rules

React Router 8 removed the `react-router-dom` compatibility package.

Required imports:

```tsx
import { createBrowserRouter, Link, useNavigate } from 'react-router';
import { RouterProvider } from 'react-router/dom';
```

Forbidden:

```tsx
import { ... } from 'react-router-dom';
import { ... } from '@remix-run/router';
import { Switch, Redirect, useHistory } from 'react-router-dom';
```

Rules:

1. Create the data router once outside the React tree.
2. Import `RouterProvider` and `HydratedRouter` from `react-router/dom`.
3. Import other router APIs from `react-router`.
4. Use route objects and current loader/action APIs; do not recreate v5/v6
   wrapper patterns.
5. Use `loaderData`, not removed `meta` `data` fields.
6. Use the platform `Response.json()` when a JSON response is required; the
   deprecated React Router `json()` utility is removed.
7. Do not add removed `future.v8_*` flags; their behavior is already the v8
   default.
8. Do not use `react-router-dom-v5-compat`.
9. Keep query-driven product data in TanStack Query as document `02` requires;
   route loaders remain limited routing boundaries.

---

## 8. TanStack Query 5 rules

Use object syntax exclusively:

```ts
useQuery({
  queryKey: testRunKeys.detail(testRunId),
  queryFn: ({ signal }) => getTestRun(testRunId, signal),
});
```

Forbidden v3/v4 patterns:

| Forbidden | Required replacement |
|---|---|
| `useQuery(key, fn, options)` | single object argument |
| `onSuccess/onError/onSettled` on queries | derive during render or explicit effect at orchestration boundary |
| `cacheTime` | `gcTime` |
| `keepPreviousData: true` | `placeholderData: keepPreviousData` |
| `isPreviousData` | `isPlaceholderData` |
| status `'loading'` | status `'pending'` |
| old `isInitialLoading` logic | current `isLoading`/`isPending` semantics |
| `useErrorBoundary` | `throwOnError` |
| `<Hydrate>` | `<HydrationBoundary>` |
| `query.remove()` | `queryClient.removeQueries(...)` |
| string query keys | array query keys |
| positional query-filter arguments | query-filter object |
| custom logger option | application observability boundary |

Mutation callbacks remain supported, but query invalidation must be explicit and
feature-owned. Do not use a mutation callback to duplicate page navigation,
Toast, and form error behavior across multiple layers.

---

## 9. TanStack Table 8 rules

Use stable `@tanstack/react-table` v8. Do not use the old `react-table` package
or the v9 prerelease.

Forbidden v7 patterns:

```text
useTable
useSortBy
usePagination plugin hooks
column.accessor string API
Header and Cell capitalized definition keys
disableSortBy and other disable* flags
manual rendering without flexRender
```

Required v8 direction:

```text
useReactTable
getCoreRowModel
getSortedRowModel when client sorting is actually used
manualPagination for backend pagination
accessorKey or accessorFn
header and cell
enableSorting and other enable* flags
flexRender
controlled state with explicit on*Change callbacks
```

Do not enable client row models for operations that documents `01–03` require
the backend to perform.

---

## 10. Tailwind CSS 4 and shadcn rules

### 10.1 Tailwind 4

Required CSS entry:

```css
@import "tailwindcss";
```

Forbidden Tailwind 3 setup:

```css
@tailwind base;
@tailwind components;
@tailwind utilities;
```

Rules:

1. Prefer the official `@tailwindcss/vite` integration for Vite when compatible
   with the selected Vite version.
2. Use CSS-first configuration with `@theme`, `@custom-variant`, and semantic
   CSS variables.
3. Do not create a JavaScript config by habit. A legacy config is not
   auto-detected and requires an explicit `@config` compatibility boundary.
4. Do not install `@tailwindcss/jit`; JIT is not a separate v4 package.
5. Do not use `tailwindcss` directly as a PostCSS plugin; use
   `@tailwindcss/postcss` when PostCSS integration is explicitly selected.
6. Do not install `autoprefixer` by habit; Tailwind 4 handles imports and vendor
   prefixing in its modern pipeline.
7. Do not use renamed v3 utility values without checking the v4 upgrade guide.
8. Browser support must meet the Tailwind 4 baseline. Stop if product browser
   requirements are older.

### 10.2 shadcn

shadcn is a source-code CLI, not a black-box component dependency. Generated
files become owned application code and must comply with documents `05`, `08`,
and this document.

Required CLI:

```text
npx shadcn@latest ...
```

Forbidden:

```text
npx shadcn-ui@latest ...
tailwindcss-animate
the deprecated shadcn toast component
the deprecated default style
new generated components using forwardRef
blind --overwrite of locally modified components
```

Use `tw-animate-css` only when its animations comply with document `05`.
Notifications must use the shared project abstraction; the underlying library
is an implementation detail.

Before adding or updating a component:

```text
npx shadcn@latest info
npx shadcn@latest docs <component>
npx shadcn@latest add <component> --dry-run
npx shadcn@latest add <component> --diff
```

Review the payload and merge intentionally. Never allow the CLI to overwrite
project tokens, Vietnamese copy, accessibility fixes, or shared behavior
without reviewing the diff.

---

## 11. Vite 8 rules

Vite 8 is ESM-first and uses Rolldown/Oxc.

Forbidden legacy APIs/configuration:

| Forbidden | Required replacement |
|---|---|
| `require('vite')` / CJS Vite config | ESM imports and ESM config |
| `process.env.*` in browser code | `import.meta.env.VITE_*` |
| `import.meta.globEager` | `import.meta.glob(pattern, { eager: true })` |
| default import from CSS file | side-effect import or `?inline` when text is required |
| `optimizeDeps.esbuildOptions` | `optimizeDeps.rolldownOptions` |
| `transformWithEsbuild` | `transformWithOxc` |
| `build.minify: 'esbuild'` | Oxc default or Rolldown output minify options |
| `build.rollupOptions` in new config | `build.rolldownOptions` |
| `worker.rollupOptions` | `worker.rolldownOptions` |
| object/function `manualChunks` habit | Rolldown `codeSplitting` when justified |
| `parseAst` / `parseAstAsync` | `parseSync` / `parse` |
| Sass legacy API | modern Sass API |

Do not add a plugin merely to restore removed CommonJS or Rollup behavior. Stop
and verify that the plugin itself declares Vite 8 compatibility.

---

## 12. Testing-library rules

### 12.1 Vitest 4

Forbidden removed configuration:

```text
poolOptions
poolMatchGlobs
environmentMatchGlobs
deps.inline / deps.external / deps.fallbackCJS
minWorkers
test or describe options as the third object argument
vitest/execute internal entrypoint
```

Use:

```text
top-level maxWorkers/isolate/vmMemoryLimit
projects for environment-specific groups
server.deps.inline/external/fallbackCJS
test(name, options, callback)
public vitest or vitest/node entrypoints
```

Do not use Jest globals, `jest.fn`, or Jest configuration in a Vitest project.

### 12.2 Testing Library

Use:

```ts
import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const user = userEvent.setup();
await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));
```

Forbidden or discouraged:

| Pattern | Replacement |
|---|---|
| `react-testing-library` package | `@testing-library/react` |
| `jest-dom` package | `@testing-library/jest-dom` |
| `@testing-library/jest-dom/extend-expect` | `/vitest` setup import |
| direct `userEvent.click(...)` in normal tests | `const user = userEvent.setup()` |
| `fireEvent` for normal user interaction | `user-event` |
| CSS-class or component-instance assertions | role/name/state assertions |
| side effects inside `waitFor` | perform action once, wait only for assertion |
| manual `act` around Testing Library interactions | await user interaction and async query |
| snapshots as primary behavioral assertions | explicit accessible behavior assertions |

Use `fireEvent` only when testing a low-level browser event that `user-event`
cannot express. Document why.

### 12.3 MSW 2

Forbidden MSW 1 syntax:

```ts
import { rest } from 'msw';

rest.get('/api/items', (req, res, ctx) => {
  return res(ctx.status(200), ctx.json([]));
});
```

Required MSW 2 syntax:

```ts
import { http, HttpResponse } from 'msw';

http.get('/api/items', () => HttpResponse.json([]));
```

Use the Web Fetch API request object:

```ts
http.post('/api/items', async ({ request, params, cookies }) => {
  const body = await request.json();
  return HttpResponse.json({ body, params, cookies }, { status: 201 });
});
```

Do not use `req.body`, `req.params`, `ctx.*`, `res.once`, or the `rest` namespace.

### 12.4 Playwright

Playwright is DEV-ONLY and reserved for cross-route browser E2E. Do not use it
for pure functions, hooks, isolated components, or cases covered by Vitest and
Testing Library.

Its purpose here is narrowly to prove that the compiled application, browser
routing, authentication boundary, network integration, and critical user flow
work together in a real browser. It must not ship in the production bundle.

Use locators and web-first assertions:

```ts
await page.getByLabel('Tên Dataset').fill('Regression');
await page.getByRole('button', { name: 'Tạo Dataset' }).click();
await expect(page.getByRole('heading', { name: 'Regression' })).toBeVisible();
```

Forbidden/deprecated or flaky patterns:

| Forbidden | Required replacement |
|---|---|
| `page.$`, `page.$$`, `$eval`, `$$eval` | Locator APIs |
| page-level selector actions such as `page.click` | role/label locator action |
| `page.type` / `locator.type` | `locator.fill`; `pressSequentially` only for key-by-key behavior |
| `page.waitForNavigation` | `page.waitForURL` or web assertion |
| `page.waitForSelector` | locator assertion or `locator.waitFor` |
| `page.waitForTimeout` | observable event/state assertion |
| `ElementHandle`-driven workflows | locators |
| `networkidle` as readiness proof | application-specific web assertion |
| arbitrary sleeps/retries | deterministic waits and assertions |

---

## 13. ESLint 10, typescript-eslint, and Prettier rules

Use flat config:

```text
eslint.config.js or eslint.config.mjs
```

Forbidden legacy configuration:

```text
.eslintrc
.eslintrc.json
.eslintrc.cjs
eslintConfig in package.json
.eslintignore as an ESLint input
extends: 'eslint:recommended' string syntax
ESLINT_USE_FLAT_CONFIG=false
--no-eslintrc
--rulesdir
--resolve-plugins-relative-to
```

Use imported flat configs, explicit `files`, `ignores`, `languageOptions`, and
`linterOptions`. For local custom rules, use current ESLint 10 context members:

```text
context.cwd
context.filename
context.physicalFilename
context.sourceCode
context.languageOptions
```

Do not use removed `context.getSourceCode()`, `context.getFilename()`,
`context.parserOptions`, or deprecated `SourceCode` token/comment helpers.

Required lint intent:

```text
type-aware TypeScript rules
React hooks rules
React refresh rules for Vite boundaries
no explicit any
no floating promises
consistent type imports
no deprecated APIs where supported by available plugins/types
no unused disable directives
```

Prettier formats code; ESLint enforces correctness. Do not run Prettier as an
ESLint rule, and do not add `eslint-plugin-prettier` by habit. Use
`eslint-config-prettier` only when needed to disable conflicting formatting
rules.

---

## 14. Lucide and date-fns rules

### 14.1 Lucide

Use direct named imports so unused icons are tree-shaken:

```tsx
import { CircleAlert, Search } from 'lucide-react';
```

Do not import the complete icon namespace, build a dynamic icon name map, deep
import internal package files, or mix another icon library into the product.

### 14.2 date-fns 4

Use named exports:

```ts
import { format, parseISO } from 'date-fns';
```

Do not use default deep imports copied from date-fns v2. Do not use Moment-style
format tokens such as `YYYY` and `DD`; use Unicode tokens such as `yyyy` and
`dd` with an explicit tested locale/time-zone decision.

Do not hand-roll date arithmetic, parsing, or relative-time formatting when a
date-fns function covers the verified requirement. Do not add `moment`,
`dayjs`, or another date library.

---

## 15. Required automated enforcement

The scaffold must make deprecated and forbidden patterns fail before review.

Required:

```text
TypeScript strict diagnostics
ESLint type-aware rules
React hooks lint rules
no deprecated API warnings ignored
tests compiled with the same TypeScript contract
exact dependency pins and package-lock.json
```

Before completing a slice, search changed frontend files for at least:

```bash
rtk rg -n "from ['\"]react-router-dom['\"]|ReactDOM\.(render|hydrate|findDOMNode)|unmountComponentAtNode" apps/web
rtk rg -n "React\.(FC|FunctionComponent|FormEvent)|import React from|import \* as React" apps/web
rtk rg -n "z\.string\(\)\.(email|url|uuid|datetime|date|time|ip)\(" apps/web
rtk rg -n "\.merge\(|z\.nativeEnum\(|\.errors\b" apps/web
rtk rg -n "cacheTime|keepPreviousData:\s*true|useErrorBoundary|<Hydrate\b" apps/web
rtk rg -n "\buseTable\b|from ['\"]react-table['\"]" apps/web
rtk rg -n "@tailwind (base|components|utilities)|tailwindcss-animate|shadcn-ui" apps/web
rtk rg -n "\brest\.(get|post|put|patch|delete)\b|\bctx\.(json|status|body)\b" apps/web
rtk rg -n "page\.(waitForTimeout|waitForNavigation|waitForSelector|type|\$|\$\$)" apps/web
```

Classify every match. Remove violations. A false positive may remain only when
the final report names the exact file, line, reason, and verified exception.

---

## 16. Agent pre-edit and completion report

Before editing `package.json`, configuration, or version-sensitive code, add to
the normal frontend pre-edit gate:

```text
Installed/target package versions:
Official migration pages checked:
Deprecated APIs searched:
Compatibility conflicts:
```

Before completion, add to the frontend compliance report:

```text
Dependency pins changed:
Installed npm ls evidence:
Deprecated/removed API audit:
Project-forbidden legacy audit:
Compatibility exceptions:
```

Do not declare compliance while a deprecation warning, peer-dependency error,
type-aware lint failure, or unexplained legacy match remains.

---

## 17. Primary references

Version and migration decisions in this document were checked against primary
maintainer sources:

- [React 19 upgrade guide](https://react.dev/blog/2024/04/25/react-19-upgrade-guide)
- [React `forwardRef` reference](https://react.dev/reference/react/forwardRef)
- [Zod 4 release notes](https://zod.dev/v4)
- [Zod 4 migration guide](https://zod.dev/v4/changelog)
- [React Router v8 changelog](https://reactrouter.com/start/start/changelog)
- [React Router v8 installation](https://reactrouter.com/start/data/installation)
- [TanStack Query v5 migration guide](https://tanstack.com/query/v5/docs/framework/react/guides/migrating-to-v5)
- [Tailwind CSS v4 upgrade guide](https://tailwindcss.com/docs/upgrade-guide)
- [shadcn Tailwind v4 and React 19 guide](https://ui.shadcn.com/docs/tailwind-v4)
- [Vite 8 migration guide](https://vite.dev/guide/migration.html)
- [Vitest migration guide](https://vitest.dev/guide/migration.html)
- [Testing Library user-event setup](https://testing-library.com/docs/user-event/setup/)
- [MSW repository and current v2 examples](https://github.com/mswjs/msw)
- [Playwright Page API deprecations](https://playwright.dev/docs/api/class-page)
- [ESLint flat-config migration guide](https://eslint.org/docs/latest/use/configure/migration-guide)
- [ESLint 10 migration guide](https://eslint.org/docs/latest/use/migrate-to-10.0.0)
- [TypeScript 7 announcement and compatibility guidance](https://devblogs.microsoft.com/typescript/announcing-typescript-7-0/)
- [date-fns Unicode token guidance](https://blog.date-fns.org/v2-unicode-tokens/)

Npm versions must still be re-queried at implementation time because registry
state changes independently of this document.
