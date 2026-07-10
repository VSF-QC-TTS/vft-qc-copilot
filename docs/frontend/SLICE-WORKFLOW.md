# Frontend Slice Workflow and Prompt Templates

Use one complete cycle per slice:

```text
Analyze
-> approve plan
-> implement
-> independent review
-> fix
-> verify
-> commit
```

Do not combine multiple slices in one implementation prompt or commit.

## 0. How to run one slice with Gemini CLI

Run Gemini from the repository root so the root `GEMINI.md` is loaded.

At the beginning of a new Gemini session, run:

```text
/memory refresh
/memory show
```

Confirm that the loaded context includes:

```text
GEMINI.md
AGENTS.md
docs/frontend/05-design-system-and-ux-rules.md
docs/frontend/08-ui-language-toast-and-agent-rules.md
docs/frontend/09-modern-library-and-deprecation-rules.md
```

Use this sequence for exactly one slice:

```text
1. Paste the Analyze prompt.
2. Review the returned evidence and plan. Do not approve when a blocker or
   document conflict remains.
3. Paste the Implementation prompt only after approving the plan.
4. Start a fresh Gemini session and paste the Independent review prompt.
5. Paste the Fix prompt with the accepted review findings.
6. Start a fresh verification session when the slice is high-risk.
7. Paste the Commit prompt only after every required command passes.
8. Stop. Do not ask Gemini to continue to the next slice automatically.
```

Use a fresh session for independent review so the reviewer is not anchored to
the implementation report. Never use one prompt such as `implement the whole
frontend`, `complete all remaining slices`, or `continue until the app is
finished`.

---

## 1. Analyze prompt

```text
You are working on the VinFast QC Copilot frontend.

Slice:
  <SLICE_NUMBER> — <SLICE_NAME>

Before doing anything:

1. Confirm that root GEMINI.md is loaded. If it is not loaded, stop.
2. Read AGENTS.md.
3. List and read every Markdown file in docs/frontend.
4. Read the complete section for this slice in
   docs/frontend/07-sliced-implementation-plan.md.
5. Inspect the current source and tests under apps/web.
6. Inspect relevant backend controllers, DTOs, enums, permission checks,
   validators, state machines, and OpenAPI contracts.
7. Inspect git status and the current diff. Preserve unrelated user changes.
8. Do not modify files, install dependencies, or create a commit.

First report:

Frontend pre-edit gate
Task or slice:
Instruction files read:
Backend/OpenAPI sources checked:
Files allowed to change:
Required UI states:
Language and feedback decisions:
Known blockers or conflicts:

Then report:

- current implementation state;
- exact API, DTO, enum, permission, and state-transition evidence;
- acceptance criteria mapped to planned code changes;
- tests required for every acceptance criterion;
- commands to run;
- ordered file-by-file implementation plan.

Vietnamese is the primary UI language. Keep canonical technical terms in
English and do not add Vietnamese translations in parentheses.

Follow docs/frontend/08-ui-language-toast-and-agent-rules.md. Do not use Toast
for field validation, inline form errors, persistent status, page errors,
normal loading or preflight blockers.

Follow docs/frontend/09-modern-library-and-deprecation-rules.md. Verify the
installed major before using version-sensitive APIs and reject every removed,
deprecated, or project-forbidden legacy pattern listed there.

Do not add dependencies or invent contracts. If any material field in the
pre-edit gate is unknown, return `BLOCKED` with the exact missing evidence.

End with `PLAN READY FOR APPROVAL` or `BLOCKED`, then stop. Do not write code.
```

---

## 2. Implementation prompt

```text
Implement only Slice <SLICE_NUMBER> — <SLICE_NAME> using the approved plan.

Approved plan:
  <PASTE_OR_REFERENCE_THE_APPROVED_PLAN>

Mandatory rules:

- confirm root GEMINI.md is loaded before editing;
- follow AGENTS.md and all docs/frontend files;
- do not implement another slice;
- do not modify backend code;
- do not add a dependency without stopping;
- do not invent API, DTO, permission or business behavior;
- use TypeScript strict mode;
- do not use any, ts-ignore or disabled lint rules to bypass correctness;
- include loading, empty, error, success and permission states;
- use Vietnamese UI copy with canonical technical terms kept in English;
- follow the feedback and Toast hierarchy in document 08;
- follow current-library APIs and the legacy/deprecation bans in document 09;
- add tests in this slice.

Before the first edit, repeat the `Frontend pre-edit gate` from the approved
plan and confirm that no blocker or conflict remains. If repository state or a
contract changed after analysis, stop and return `PLAN INVALIDATED`.

While implementing:

- edit only the approved file set;
- preserve unrelated worktree changes;
- implement every applicable default, hover, focus-visible, pressed, selected,
  disabled, loading, empty, error, success, permission/read-only, reduced-motion,
  light-theme, dark-theme, and tablet state;
- use only the shared notification abstraction;
- do not show the same feedback inline and in Toast;
- do not commit yet.

Run:

npm run typecheck
npm run lint
npm run test
npm run build

Fix failures that belong to this slice. Do not skip tests or disable rules.

Report:

Frontend compliance report
Task or slice completed:
Changed files:
Document 05 compliance:
Document 08 compliance:
Document 09 modern/deprecation audit:
Language audit:
Toast/feedback decisions:
Accessibility and sensitive-data audit:
Tests added or updated:
Commands executed and results:
States intentionally not applicable:
Items intentionally left for later slices:
Remaining blockers or risks:

End with `READY FOR INDEPENDENT REVIEW` only when every required command passes.
Otherwise end with `NOT READY` and the exact failure. Then stop.
```

---

## 3. Independent review prompt

```text
Review Slice <SLICE_NUMBER> — <SLICE_NAME> independently.

Do not trust the previous implementation report.

Confirm root GEMINI.md is loaded. Read AGENTS.md, all docs/frontend files, the
complete slice plan, the current source, tests, relevant backend/OpenAPI
contracts, git status, and the complete slice diff.

Do not edit code yet.

Check:

- scope creep;
- missing acceptance criteria;
- architecture/import violations;
- wrong API or permission assumptions;
- auth, CSRF and empty-401 handling;
- query keys and invalidation;
- loading, empty, error, success and permission states;
- Vietnamese UI copy and technical-term consistency;
- incorrect or duplicate Toast usage;
- sensitive-data leakage;
- accessibility;
- race conditions;
- SSE sequence/reconnect behavior when relevant;
- weak or missing tests;
- unnecessary dependencies;
- dead code and file clutter.
- deprecated, removed, or project-forbidden library APIs from document 09.

For each changed interactive component, verify every applicable state required
by document 05. For each user-facing string, verify Vietnamese language and the
canonical technical-term list in document 08. For every notification, identify
its trigger, channel, deduplication behavior, and sensitive-data risk.
Audit every changed import and version-sensitive API against document 09 and
the installed package types.

Run typecheck, lint, tests and build.

Return findings as BLOCKER, HIGH, MEDIUM or LOW. Each finding must include file,
location, impact, concrete fix and required test.

Conclude with `APPROVE`, `APPROVE WITH MINOR FIXES`, or `REQUEST CHANGES`. Do not
edit code and do not create a commit.
```

---

## 4. Fix prompt

```text
Fix all valid BLOCKER, HIGH and MEDIUM findings from the independent review.

Slice:
  <SLICE_NUMBER> — <SLICE_NAME>

Accepted findings:
  <PASTE_ACCEPTED_FINDINGS>

Rules:

- remain inside the current slice;
- explain any rejected finding using code or a verified contract;
- add regression tests;
- do not disable rules, skip tests or use unsafe type assertions;
- preserve Vietnamese UI and Toast rules;
- remove deprecated, removed, and project-forbidden legacy APIs from document 09;
- run typecheck, lint, tests and build;
- report how each finding was resolved.

Re-run the complete required command set after all fixes. Return the full
`Frontend compliance report` from GEMINI.md. End with `READY TO COMMIT` only
when no accepted BLOCKER, HIGH, or MEDIUM finding remains and all commands pass.
Then stop without committing.
```

---

## 5. Commit prompt

```text
Finalize only Slice <SLICE_NUMBER> — <SLICE_NAME>.

Before committing:

1. Confirm root GEMINI.md is loaded.
2. Inspect git status and the complete diff.
3. Verify that only the approved slice files are included.
4. Confirm that all accepted review findings are resolved.
5. Confirm the document 09 legacy/deprecation audit has no unexplained match.
6. Run typecheck, lint, test, and build again.
7. Do not stage unrelated files.

If any check fails, do not commit. Return `NOT READY TO COMMIT` with the exact
failure.

If all checks pass, create exactly one commit for this slice using the project
commit convention. Report the commit hash, subject, included files, and command
results. Then stop. Do not begin the next slice.
```

---

## 6. Commit convention

Use one commit per slice.

Examples:

```text
feat(web): scaffold frontend application
feat(web): add application shell and themes
feat(web): add shared API infrastructure
test(web): cover SSE reconnect behavior
fix(web): prevent duplicate refresh requests
```

Before commit:

```bash
git status
git diff --stat
git diff
npm run typecheck
npm run lint
npm run test
npm run build
```
