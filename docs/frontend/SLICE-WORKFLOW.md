# Frontend Slice Workflow

Goal: implement one frontend slice with verified contracts, bounded context, independent review, and reproducible checks.
## 1. Select and route

1. Select one slice from `07-sliced-implementation-plan.md`.
2. Always read `AGENTS.md`, `00`, `08`, `09`, and the complete selected slice.
3. Load additional documents by scope:

| Scope | Additional document |
|---|---|
| routes, navigation, product flow | `01` |
| architecture, Query, forms, shared state | `02` |
| API, auth, CSRF, permissions, errors, Export | `03` |
| TestRun realtime or SSE | `04` |
| components, interaction, responsive UI | relevant sections of `05` |
| tests and verification | relevant sections of `06` |

Follow linked sections that define a required contract. Do not load unrelated documents merely to fill context.
## 2. Analyze without editing

Give the coding agent:
```text
Analyze only Slice <NUMBER> — <NAME>. Follow AGENTS.md and
docs/frontend/SLICE-WORKFLOW.md. Do not edit, install, or commit.
Return the pre-edit gate, verified contracts, AC-to-test mapping,
allowed files, commands, blockers, and an ordered plan. Stop for approval.
```

Approve only when every material contract and allowed file is known.
## 3. Implement the approved plan

```text
Implement only the approved plan for Slice <NUMBER> — <NAME>.
Preserve unrelated changes. Do not add dependencies or invent contracts.
Use TypeScript 7 strict, type-aware Oxlint, and Sonner only through the
shared notification abstraction. Add tests and do not commit.
```

Require loading, empty, error, success, permission/read-only, accessibility, Vietnamese copy, sensitive-data, and responsive states when applicable.

## 4. Verify

Run from `apps/web`:
```text
npm run typecheck
npm run lint
npm run test
npm run build
```

Do not bypass failures with `any`, ignored diagnostics, disabled rules, skipped tests, or forced dependency resolution.

## 5. Review independently

Use a fresh agent/session:
```text
Review Slice <NUMBER> — <NAME> without editing. Read the approved plan,
contracts, source, tests, git diff, and command output. Report BLOCKER,
HIGH, MEDIUM, and LOW findings with file, impact, fix, and required test.
Conclude APPROVE or REQUEST CHANGES.
```

Fix accepted findings, add regression tests, and rerun all four commands.

## 6. Handoff

Do not commit unless the user explicitly requests it. Return:
```text
Slice:
Verdict:
Changed files:
Contracts verified:
AC and tests:
Commands and results:
Review findings resolved:
Remaining blockers or risks:
```
