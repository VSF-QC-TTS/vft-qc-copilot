# Gemini Frontend Implementation Guardrail

This file is mandatory project context for Gemini CLI and any Gemini coding
agent working in this repository. It is an execution protocol, not optional
background information.

The imported files at the end of this document are part of this instruction.
Read them as normative requirements. Do not replace them with a summary, rely
on memory of them, or silently choose a different convention.

## 1. Scope

Apply this guardrail whenever a task:

- creates, edits, reviews, tests, or deletes anything under `apps/web`;
- changes frontend behavior, UI copy, styling, accessibility, notifications,
  routes, forms, tables, dialogs, loading states, or responsive behavior;
- changes a shared component or token consumed by `apps/web`;
- changes a frontend document under `docs/frontend`;
- produces code intended to be copied into `apps/web`.

For a frontend task, every generated line of code and every user-facing string
must comply with:

1. `docs/frontend/05-design-system-and-ux-rules.md`;
2. `docs/frontend/08-ui-language-toast-and-agent-rules.md`;
3. `docs/frontend/09-modern-library-and-deprecation-rules.md`.

There is no exemption for prototypes, placeholders, generated code, demo data,
small fixes, or "temporary" UI.

## 2. Instruction priority

Use this priority order exactly:

1. Verified backend Java source and observed runtime behavior.
2. Repository `AGENTS.md`.
3. `docs/frontend/00-project-standard.md`.
4. `docs/frontend/08-ui-language-toast-and-agent-rules.md`.
5. `docs/frontend/09-modern-library-and-deprecation-rules.md`.
6. Task-relevant documents in `docs/frontend/01` through `07`.
7. Generated OpenAPI only when it does not conflict with verified backend
   implementation.
8. Approved Figma or UI assets.
9. Existing frontend code only when it complies with items 1 through 8.

Document `08` is authoritative for UI language and feedback-channel decisions.
Document `05` is authoritative for visual and interaction behavior except where
document `08` explicitly defines a higher-priority Toast or language rule.
Document `09` is authoritative for package versions, current APIs, deprecated
APIs, removed APIs, and project-forbidden legacy patterns.

If two sources at the same priority conflict, or the required behavior cannot
be established from a higher-priority source, stop before editing and report
the exact conflict. Do not improvise a compromise.

## 3. Mandatory pre-edit gate

Before modifying frontend code:

1. Read `AGENTS.md` completely.
2. List and read every Markdown file in `docs/frontend` completely.
3. Read the complete target slice in
   `docs/frontend/07-sliced-implementation-plan.md` when the task belongs to a
   slice.
4. Inspect the current `apps/web` implementation and tests.
5. Inspect relevant backend controllers, DTOs, enums, permission checks,
   validators, state machines, and OpenAPI paths.
6. Inspect the current git diff so unrelated user changes are preserved.
7. Identify the allowed file set, acceptance criteria, required UI states, and
   tests before editing.

Before the first edit, output this exact evidence block:

```text
Frontend pre-edit gate
Task or slice:
Instruction files read:
Backend/OpenAPI sources checked:
Files allowed to change:
Required UI states:
Language and feedback decisions:
Known blockers or conflicts:
```

Do not claim that a file was read or a command passed unless it actually was.
Do not start editing when any field in this block is unknown and material to
the implementation.

If the user explicitly requests analysis or review only, remain read-only. Do
not interpret a review request as permission to implement fixes.

## 4. Fail-closed stop conditions

Stop and ask for resolution instead of writing speculative production code if:

- an endpoint, DTO field, enum, permission, state transition, validation rule,
  or business rule is missing, ambiguous, or conflicts with backend source;
- a documented backend-verification blocker applies to the requested behavior;
- the task requires a new dependency or a change outside the requested slice;
- a design requirement is not covered and no closest compliant shared pattern
  exists;
- approved UI assets conflict with documents `05`, `08`, or `09`;
- satisfying the request would require bypassing TypeScript, lint, test,
  accessibility, security, or sensitive-data rules;
- the available source is insufficient to decide between Toast, inline,
  persistent, dialog, or page-level feedback;
- unrelated worktree changes overlap the required edit.

Never hide a stop condition with hardcoded data, a raw JSON production form, a
frontend-only permission rule, an invented API adapter, a disabled lint rule,
`any`, `@ts-ignore`, a skipped test, or a TODO without an owner and ticket.

## 5. Scope and implementation discipline

- Implement exactly one requested slice or one explicitly scoped maintenance
  task. Do not opportunistically implement later slices.
- Make the smallest coherent change that satisfies the acceptance criteria.
- Preserve unrelated user changes and existing public contracts.
- Do not add or upgrade dependencies without explicit approval.
- Components must not construct endpoints, attach auth headers, parse API
  contracts ad hoc, or reproduce backend business logic.
- Feature modules must use shared design tokens, primitives, API adapters,
  permission helpers, and semantic notification functions.
- Feature modules must not call a third-party Toast package directly.
- Feature code must not introduce raw hex colors when a semantic token exists.
- Do not create a new visual pattern when the closest documented shared pattern
  covers the need.
- Do not use existing non-compliant UI as precedent. Fix only the non-compliant
  area within the authorized scope and report adjacent debt.

## 6. Non-negotiable design-system checks

For every changed UI element, explicitly verify all applicable states:

```text
default
hover
focus-visible
pressed
selected
disabled
loading
empty
error
success
permission/read-only
reduced motion
light theme
dark theme
tablet behavior
```

Omit a state only when it genuinely does not apply, and record why in the final
report.

Never introduce:

- gradients, glassmorphism, neon, decorative background effects, or large
  colored page backgrounds;
- marketing hero sections, bento marketing grids, marquees, parallax, scroll
  choreography, or decorative page-reveal animation;
- heavy shadows, hover zoom, bouncing controls, pulsing failures, or confetti;
- pill-shaped primary buttons, cards, dialogs, or navigation containers;
- emoji UI icons or a second icon library;
- `transition: all`;
- layout movement on hover;
- color-only status meaning;
- hidden essential information available only through a tooltip.

Use the exact tokens, geometry, interaction timing, density, responsive rules,
accessibility requirements, and component recipes from document `05`. Any
deviation requires a documented accessibility or component-specific reason.

## 7. Non-negotiable UI-language checks

- User-facing UI copy is Vietnamese.
- Code identifiers, API contracts, enums, file names, and test names remain in
  English.
- Keep only the canonical technical terms listed in document `08` in English.
- Do not translate a technical term and append its English form in parentheses.
- Do not expose raw backend enums as primary UI labels.
- Use short, direct, specific, operational copy.
- Do not use emoji, marketing language, vague failure copy, or unnecessary
  exclamation marks.
- Accessible names, `aria-label` values, validation text, empty states, loading
  labels, tooltip text, dialog copy, and Toast copy are user-facing UI copy and
  follow the same language rule.

Before completing the task, search all changed files for newly added
user-facing English strings. Classify every match as either an approved
technical term or a violation; do not assume it is harmless.

## 8. Feedback-channel decision procedure

For each mutation, error, background event, or status transition, choose one
primary channel in this order:

```text
One invalid field                 -> field error
Current form submission problem  -> form-level inline alert
Panel/table/resource failure      -> inline section error
Condition remains active          -> persistent banner or status indicator
User decision is required         -> dialog
Transient result/background event -> Toast
```

Toast is not the default. Never emit the same message through two channels.

Use Toast only through the shared semantic notification abstraction. Apply the
variant, duration, placement, geometry, motion, pause/resume, maximum-stack,
deduplication, accessibility, and sensitive-data rules from document `08`.

For each added or changed Toast, record:

```text
Trigger:
Why transient:
Why no stable inline/persistent channel is appropriate:
Deduplication key:
Sensitive-data review:
```

Do not use Toast for field validation, backend `fieldErrors`, a visible form
error, page-level 403/404, empty state, normal loading/refetch, preflight
blockers, destructive confirmation, persistent reconnect/offline state, or
continuous progress.

## 9. Accessibility and sensitive-data gate

Every changed interaction must support keyboard use, visible focus, semantic
labels, appropriate announcements, reduced motion, and color-independent
meaning. Dialogs must trap and restore focus. Async status must not flood live
regions.

Never render, persist, log, include in telemetry, copy after storage, or place
in a notification:

```text
access token
refresh token
plaintext Secret or API key
Cookie or Authorization header
raw sensitive cURL
raw sensitive request/response
stack trace
unmasked backend value
```

## 10. Test and verification gate

Add tests proportional to the changed behavior. At minimum, cover applicable:

- visible Vietnamese copy and canonical technical terms;
- feedback channel selection and absence of duplicate Toasts;
- notification deduplication and timing behavior;
- keyboard/focus and accessible names;
- loading, empty, error, success, permission, and read-only states;
- light/dark token use and reduced motion where behavior is testable;
- backend contract and permission handling;
- regression behavior for the reported issue.

Run the repository-approved commands from the applicable frontend docs. For a
normal completed slice, the minimum gate is:

```text
npm run typecheck
npm run lint
npm run test
npm run build
```

Run them from the correct package/workspace. Never report success based on a
different command, stale output, or a partial suite. If a command cannot run,
report the exact command, failure, and unverified risk.

## 11. Mandatory final self-review

Before reporting completion:

1. Re-read the imported documents `05`, `08`, and `09`.
2. Inspect the complete diff, not only individual edited files.
3. Map every acceptance criterion to code and a test or explicit verification.
4. Check for raw colors, direct Toast-library calls, non-Vietnamese UI copy,
   parenthetical translations, duplicate feedback, missing states, inaccessible
   controls, sensitive values, and scope creep.
5. Fix all in-scope violations before declaring completion.

Finish with this exact evidence block:

```text
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
```

Do not say "done", "complete", or "compliant" while a required check is
unverified or failing.

## 12. Anti-rationalization rules

The following are never valid reasons to violate this file:

- "The user did not mention the design system."
- "It is only a small change."
- "This is temporary or a prototype."
- "The library default looks acceptable."
- "The existing component already does it."
- "A Toast is simpler."
- "English is clearer here."
- "The model inferred the backend behavior."
- "The tests are expensive."
- "The deadline is short."

When uncertain, inspect evidence or stop. Do not guess.

## Imported mandatory project instructions

@./AGENTS.md

## Imported canonical design and UX contract

@./docs/frontend/05-design-system-and-ux-rules.md

## Imported canonical UI-language and feedback contract

@./docs/frontend/08-ui-language-toast-and-agent-rules.md

## Imported canonical modern-library and deprecation contract

@./docs/frontend/09-modern-library-and-deprecation-rules.md
