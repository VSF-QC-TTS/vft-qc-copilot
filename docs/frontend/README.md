# VinFast QC Copilot Frontend Documentation

**Location:** `docs/frontend`  
**Status:** Reviewed package  
**Last reviewed:** 2026-07-11

## 1. Mục đích

Bộ tài liệu này là nguồn hướng dẫn chính để thiết kế, triển khai, review và kiểm thử frontend trong `apps/web`.

Tài liệu kỹ thuật chủ yếu dùng tiếng Anh để giữ thuật ngữ code và contract nhất quán. UI hiển thị cho người dùng phải dùng tiếng Việt; các thuật ngữ chuyên ngành đã quy định vẫn giữ nguyên tiếng Anh.

## 2. Cấu trúc

```text
docs/frontend/
  README.md
  00-project-standard.md
  01-mvp-flows-and-routes.md
  02-frontend-architecture.md
  03-api-auth-permission-and-error-contract.md
  04-test-run-realtime-sse.md
  05-design-system-and-ux-rules.md
  06-testing-contract.md
  07-sliced-implementation-plan.md
  08-ui-language-toast-and-agent-rules.md
  09-modern-library-and-deprecation-rules.md
  SLICE-WORKFLOW.md
```

Repository root:

```text
AGENTS.md
```

## 3. Thứ tự đọc

Đọc lần đầu:

```text
00 -> 01 -> 02 -> 03 -> 04 -> 05 -> 06 -> 07 -> 08 -> 09
```

Khi làm một slice, dùng `SLICE-WORKFLOW.md`; không nạp toàn bộ 13.000+ dòng tài
liệu vào context nếu slice không sử dụng chúng:

```text
1. Đọc AGENTS.md.
2. Đọc `00`, `08`, `09` và đầy đủ slice tương ứng trong `07`.
3. Đọc thêm tài liệu được routing theo loại thay đổi.
4. Kiểm tra source hiện tại, backend/OpenAPI liên quan và git diff.
5. Chạy Analyze gate; chỉ code sau khi plan được duyệt.
6. Triển khai đúng một slice và thêm tests.
7. Review độc lập, sửa findings đã chấp nhận.
8. Chạy typecheck, lint, test và build.
9. Chỉ commit khi người dùng yêu cầu rõ ràng.
```

## 4. Vai trò của từng file

| File | Nội dung |
|---|---|
| `00` | Stack, tiêu chuẩn code, source of truth, bảo mật và quy tắc agent |
| `01` | MVP scope, route, navigation và luồng người dùng |
| `02` | Kiến trúc frontend, API client, Query, form, SSE và boundary |
| `03` | Auth, CSRF, cookie, permission, error, pagination và Export |
| `04` | SSE event, sequence, replay, reconnect và terminal handling |
| `05` | Màu sắc, component state, hover, input, button, skeleton, loader và motion |
| `06` | Unit, component, integration, SSE, permission, language, Toast và E2E tests |
| `07` | Kế hoạch Slice 0–24, acceptance criteria và stop condition |
| `08` | Tiếng Việt trong UI, thuật ngữ giữ tiếng Anh, Toast và feedback hierarchy |
| `09` | Phiên bản thư viện, API hiện hành và các mẫu deprecated/legacy bị cấm |
| `SLICE-WORKFLOW` | Prompt mẫu để phân tích, triển khai, review và sửa từng slice |

## 5. Source of truth

```text
Verified backend source/runtime
  -> AGENTS.md
  -> 00, 08 và 09
  -> tài liệu 01–07 liên quan
  -> OpenAPI nếu không xung đột
  -> UI assets/Figma
  -> frontend hiện tại
```

Không tự đoán endpoint, DTO, permission, trạng thái, evaluator config hoặc business rule.

## 6. Các contract đã chốt

```text
Bearer access token lưu trong memory
HttpOnly refresh cookie
SPA CSRF với XSRF-TOKEN / X-XSRF-TOKEN
single-flight refresh
ProblemDetail và empty-body 401
VIEW / EDIT / MANAGE
PLATFORM_ADMIN bypass
page/size/sort pagination không có total
fetch-based SSE với sequence replay
QC review cần EDIT
Dataset và TestRun Export cần VIEW
UI tiếng Việt với technical terms giữ tiếng Anh
Toast chỉ dùng cho transient feedback
```

## 7. Các phần còn bị chặn

```text
fully typed LLM_JUDGE field values và allowed options
fully typed MIXED form contract, aggregation và composition rules
Dataset draft-row conflict contract
```

Không được dùng raw JSON form đoán mò để thay thế các contract này trong production UX.

## 8. Definition of done

Một slice chỉ hoàn tất khi:

```text
đúng scope
đúng contract
đủ loading/empty/error/success/permission states
đúng UI language và Toast rules
có tests
typecheck pass
lint pass
test pass
build pass
không làm lộ dữ liệu nhạy cảm
đã report phần cố ý để lại cho slice sau
```
