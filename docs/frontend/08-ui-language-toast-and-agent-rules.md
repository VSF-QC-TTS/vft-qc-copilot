# VinFast QC Copilot Frontend — UI Language, Toast and Agent Compliance Rules

**Document:** `docs/frontend/08-ui-language-toast-and-agent-rules.md`  
**Status:** Mandatory  
**Applies to:** all work under `apps/web`  
**Last reviewed:** 2026-07-11

---

## 1. Purpose

This file contains short, mandatory rules that must be checked in every frontend slice.

It exists to prevent implementation agents from:

- mixing Vietnamese and English inconsistently;
- translating technical domain terms unnecessarily;
- adding Vietnamese explanations in parentheses;
- showing Toast for the wrong kind of feedback;
- duplicating Toast, inline errors, and persistent status UI;
- changing interaction patterns from slice to slice;
- skipping the frontend rule files before coding.

This file does not replace the detailed design, architecture, API, or testing documents.

---

## 2. Instruction priority

For frontend implementation, use this order:

```text
1. Verified backend source and actual runtime behavior
2. AGENTS.md
3. docs/frontend/00-project-standard.md
4. This file
5. docs/frontend/09-modern-library-and-deprecation-rules.md
6. The task-relevant documents in docs/frontend/01–07
7. OpenAPI when it does not conflict with verified implementation
8. Approved UI assets or Figma
9. Existing frontend implementation
```

When two frontend documents conflict, stop and report the conflict instead of choosing silently.

---

## 3. Primary UI language

The primary language of the user interface is:

```text
Vietnamese
```

Use Vietnamese for:

```text
page titles
navigation labels
button labels
form labels
help text
empty states
validation messages
dialogs
Toast messages
permission messages
loading messages
error messages
status labels shown to users
```

Code identifiers, API contracts, enums, file names, and test names remain in English.

---

## 4. Technical terms that remain in English

Keep established technical and domain terms in English when they are used as product concepts.

Canonical terms include:

```text
Workspace
Project
Dataset
Dataset version
Dataset schema
Dataset row
Chatbot Target
Evaluator
Rule-based Evaluator
LLM Judge
Mixed Evaluator
TestRun
Runner
Secret
Personal Secret
Workspace Secret
Provider
LLM Provider
QC review
Comparison
preflight
model
prompt
rubric
field source
operator
API
API key
endpoint
request
response
payload
header
field
mapping
token
access token
refresh token
cookie
CSRF
SSE
JSON
cURL
log
trace ID
Export
Import
Preview
MACHINE
FINAL
```

Use these terms naturally inside Vietnamese sentences.

Correct:

```text
Tạo Dataset
Cập nhật Chatbot Target
API key không hợp lệ.
Không thể kết nối tới endpoint.
TestRun đã được đưa vào hàng đợi.
Chọn Dataset version để chạy kiểm thử.
Export đang được xử lý.
```

Incorrect:

```text
Tạo bộ dữ liệu (Dataset)
Mục tiêu chatbot (Chatbot Target)
Khóa API (API key)
Điểm cuối (endpoint)
Yêu cầu (request)
Phản hồi (response)
```

Do not add Vietnamese translations in parentheses after a technical term.

Do not create a Vietnamese translation and then place the English term in parentheses.

---

## 5. Capitalization and terminology consistency

Use the canonical term consistently.

Preferred:

```text
Dataset
Chatbot Target
Evaluator
TestRun
API key
LLM Judge
SSE
cURL
```

Do not alternate between:

```text
Chatbot target
chatbot target
Bot Target
Target chatbot
```

unless normal sentence casing explicitly requires it.

For Vietnamese action labels, use sentence case:

```text
Tạo Dataset
Lưu thay đổi
Chạy preflight
Yêu cầu hủy TestRun
Export kết quả
```

Avoid title case on every word.

---

## 6. User-facing status labels

Backend enums remain unchanged in code.

UI labels use Vietnamese:

| Backend value | UI label |
|---|---|
| `CREATED` | Đã tạo |
| `QUEUED` | Đang chờ |
| `RUNNING` | Đang chạy |
| `CANCEL_REQUESTED` | Đã yêu cầu hủy |
| `COMPLETED` | Hoàn tất |
| `FAILED` for TestRun | TestRun thất bại |
| `CANCELLED` | Đã hủy |
| `PASSED` | Đạt |
| `FAILED` for testcase | Không đạt |
| `ERROR` | Lỗi thực thi |
| `JUDGE_ERROR` | Lỗi LLM Judge |
| `TIMEOUT` | Quá thời gian |

Do not expose raw enum text as the main UI label unless it is shown in a developer-oriented JSON view.

---

## 7. General UI copy style

Use:

```text
short
direct
specific
operational
polite without being verbose
```

Preferred:

```text
Đã lưu thay đổi.
Không thể tải danh sách Dataset.
Bạn không có quyền chỉnh sửa Project này.
Chọn ít nhất hai Chatbot Target cho Comparison TestRun.
```

Avoid:

```text
Thao tác đã được thực hiện thành công!
Oops!
Có vẻ như đã xảy ra sự cố.
Vui lòng thử lại sau nhé!
```

Do not use emojis.

Do not use marketing language.

Do not end every success message with an exclamation mark.

---

## 8. Feedback-channel decision order

Before showing feedback, choose the correct channel:

```text
Field error
  for one invalid field

Form-level inline alert
  for submission errors related to the current form

Inline section error
  for a failed panel, table, or resource section

Persistent banner or status indicator
  for a condition that remains active

Dialog
  for a decision or confirmation required before proceeding

Toast
  for a transient result or background event that does not require the user to remain on the current component
```

Toast is not the default solution for every event.

---

## 9. When to use Toast

### 9.1 Success Toast

Use after a user-triggered mutation succeeds and the result is not already obvious from persistent UI.

Examples:

```text
Đã tạo Dataset.
Đã cập nhật Chatbot Target.
Đã lưu QC review.
Đã xoay vòng API key.
Đã gửi yêu cầu hủy TestRun.
Đã tạo Export job.
Đã sao chép trace ID.
```

Do not show a success Toast when:

```text
the dialog closes and the newly created resource is immediately visible;
the form already contains a persistent success state;
the action is a simple local toggle with immediate visual feedback;
the same success is already displayed in a banner or result panel.
```

A success Toast may still be used for destructive or security-sensitive mutations where explicit confirmation is useful:

```text
Đã xóa Secret.
Đã vô hiệu hóa API key.
Đã publish Dataset version.
```

### 9.2 Error Toast

Use when:

```text
a global action fails outside a form;
a background job fails;
a mutation fails and there is no stable inline area;
copy/download/browser action fails;
an unexpected network failure affects the current action;
the API returns a non-field error from a compact action menu.
```

Examples:

```text
Không thể tạo Export job.
Không thể tải file Export.
Không thể sao chép trace ID.
Mất kết nối tới server. Vui lòng thử lại.
```

Do not use an error Toast for:

```text
field validation;
form submission errors already shown in the form;
page-level 403 or 404;
a table panel that already shows an inline retry state;
persistent SSE disconnection state.
```

### 9.3 Warning Toast

Use rarely, for a non-blocking one-time condition that deserves attention.

Examples:

```text
TestRun có thể hoàn tất trước khi yêu cầu hủy được xử lý.
Kết nối realtime bị gián đoạn. Hệ thống đang kết nối lại.
Một số cột cần được mapping trước khi Import.
```

When the condition remains active, also show a persistent indicator or inline message. Toast alone is not enough.

### 9.4 Information Toast

Use for short background-state transitions that would otherwise be invisible.

Examples:

```text
Export job đã được tạo và đang xử lý.
Đang chuẩn bị file để tải xuống.
Đã khôi phục kết nối realtime.
```

Do not use information Toast for every page navigation or every background refetch.

---

## 10. When not to use Toast

Do not use Toast for:

```text
required-field validation;
incorrect email or password format;
backend fieldErrors;
page loading;
normal background refetch;
every successful GET request;
route changes;
selected tab changes;
selected filter changes;
open or close menu;
open or close dialog;
403 page;
404 page;
empty state;
long-running progress that needs continuous visibility;
persistent offline or reconnecting state;
preflight blockers that the user must review;
destructive confirmation before the action.
```

Use the correct persistent or inline UI instead.

---

## 11. Toast duplication rules

Never show the same message in two channels.

Forbidden combinations for the same event:

```text
field error + Toast
form alert + identical Toast
inline panel error + identical Toast
persistent banner + repeated Toast on every retry
```

Allowed:

```text
persistent connection indicator
+ one Toast when disconnection begins
+ one Toast when connection is restored
```

The Toast must not repeat on every reconnect attempt.

Deduplicate by:

```text
event type
resource ID
error code
short time window
```

Recommended deduplication window:

```text
3–5 seconds
```

---

## 12. Toast variants and durations

| Variant | Default duration | `aria-live` |
|---|---:|---|
| Success | 4 seconds | polite |
| Information | 5 seconds | polite |
| Warning | 7 seconds | polite |
| Error | 8 seconds | assertive only when immediate attention is required |

When a Toast contains an action such as `Thử lại`, use:

```text
8–10 seconds
```

or keep it until dismissed when losing it would prevent recovery.

Hovering or keyboard-focusing a Toast pauses auto-dismiss.

Moving the pointer away resumes the remaining time; it does not restart the full duration.

Maximum visible Toast count:

```text
3
```

New Toasts enter at the top of the stack.

---

## 13. Toast placement and geometry

Desktop:

```text
top-right
24 px from viewport edges
```

Tablet and narrow screens:

```text
top-center
16 px side margin
```

Geometry:

```text
width:
  320–400 px desktop

maximum mobile width:
  calc(100vw - 32px)

radius:
  8 px

border:
  1 px solid var(--border)

background:
  var(--surface)

shadow:
  approved popover shadow
```

Use:

```text
semantic icon
title or one-line message
optional short detail
optional action
close button
```

Do not fill the full Toast background with saturated green, red, yellow, or blue.

Use a neutral surface with semantic icon and subtle accent.

---

## 14. Toast motion

Enter:

```text
opacity 0 -> 1
translateY(-6 px) -> 0
160–200 ms
```

Exit:

```text
opacity 1 -> 0
translateX(8 px) or translateY(-4 px)
140–180 ms
```

Hover-in and hover-out affect only interactive controls inside the Toast.

The Toast container must not scale or bounce.

Under `prefers-reduced-motion`:

```text
use opacity transition only
or render immediately
```

---

## 15. Toast content rules

A Toast message should normally fit in one or two short lines.

Use the resource name only when it helps identify the result.

Preferred:

```text
Đã cập nhật Dataset “VFVA Regression”.
Không thể publish Dataset version.
```

Avoid:

```text
Quá trình cập nhật bộ dữ liệu (Dataset) của bạn đã được hoàn thành thành công.
```

Do not include:

```text
access token
refresh token
API key value
Cookie header
Authorization header
raw request
raw response
sensitive cURL
stack trace
```

A trace ID may be shown only when useful for support:

```text
Không thể tạo TestRun.
Mã tham chiếu: 46f9…
```

---

## 16. Form feedback rules

Field validation:

```text
show below the affected field
focus the first invalid field after submit
do not show a Toast
```

Backend `fieldErrors`:

```text
map to fields
show remaining non-field detail in a form-level alert
do not duplicate as Toast
```

Unexpected submit error without field errors:

```text
show a form-level inline alert
Toast is optional only when the form is inside a compact dialog and the inline area is not visible after close
```

Successful form submit:

```text
close or update the form
invalidate relevant queries
show a success Toast only when the result is not already unmistakable
```

---

## 17. Long-running and background operations

Use persistent status for:

```text
Import
Export
TestRun
SSE reconnect
file processing
provider test
Chatbot Target test
```

Toast may announce state transitions:

```text
job created
job completed while user is elsewhere
job failed
connection restored
```

Toast must not replace:

```text
progress bar
job status
connection indicator
result panel
```

---

## 18. Required implementation behavior

Use one shared Toast abstraction.

Do not call a Toast library directly from random components.

Recommended boundary:

```text
shared/components/toast
shared/lib/notifications
```

Feature code calls semantic functions such as:

```ts
notifySuccess(...)
notifyError(...)
notifyWarning(...)
notifyInfo(...)
```

Do not hardcode colors inside feature modules.

Do not put navigation, API parsing, or permission logic inside the Toast component.

---

## 19. Mandatory checklist before completing a frontend slice

The implementation agent must verify:

```text
UI copy is primarily Vietnamese
technical terms remain in English
no translated technical term appears in parentheses
status labels are user-facing Vietnamese labels
Toast is used only for transient feedback
field errors remain inline
persistent conditions use persistent UI
duplicate notifications are prevented
Toast contains no sensitive data
all Toast variants support keyboard dismissal
all copy is tested where behavior is important
```

---

## 20. Mandatory agent response

Before editing frontend code, the agent must state:

```text
Slice:
Instruction files read:
Relevant backend/OpenAPI sources checked:
Known blockers:
```

After implementation, the agent must state:

```text
Language-rule compliance:
Toast/feedback decisions:
Tests added:
Commands executed:
Items intentionally left for later slices:
```
