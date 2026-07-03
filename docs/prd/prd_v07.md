# VinFast QC Copilot — PRD v0.7

Version: `v0.7`  
Date: `2026-07-03`  
Status: MVP Product Requirement Draft  
DB Contract: PostgreSQL / ERD v0.5 aligned  
Execution Engine: Promptfoo Node API via adapter contract  
Queue: Redis Streams with safe trimming/recovery policy

---

## 1. Tổng quan sản phẩm

**VinFast QC Copilot** là platform hỗ trợ đội **QC/QA chuyên test chatbot hoặc AI nội bộ của công ty**.

Platform giúp QC/QA:

- Cấu hình chatbot nội bộ cần test bằng cURL.
- Import và quản lý dataset test.
- Tái sử dụng dataset từ project khác.
- Mapping dataset vào request chatbot.
- Cấu hình rule-based evaluator.
- Cấu hình LLM Judge.
- Chạy test tự động bằng Promptfoo Runner.
- Theo dõi tiến độ chạy realtime từng testcase.
- Xem kết quả từng testcase.
- Xem dashboard tổng quan.
- So sánh nhiều chatbot target với nhau và/hoặc với các LLM/model baseline khác.
- Debug lại request, response, extraction, evaluator reason và artifact của từng run.
- Làm việc với dataset theo dạng bảng/card/library thân thiện hơn Excel nhưng vẫn import/export Excel tốt.
- Review kết quả sau khi máy chấm, sửa final pass/fail/score khi LLM Judge chấm sai và thêm QC note.
- Export kết quả cuối cùng đã được QC review ra Excel để báo cáo/nộp.

**Promptfoo là execution engine chính**. VinFast QC Copilot không tự build toàn bộ evaluation engine từ đầu. Backend lưu domain config, runner chuyển domain config thành Promptfoo-compatible config và chạy bằng Promptfoo Node API.

Source of truth của hệ thống là **domain config trong backend database**, không phải Promptfoo config. Promptfoo config là generated artifact tại thời điểm chạy.

---

## 2. Điểm mới của PRD v0.7

So với PRD v0.5, bản v0.7 không đổi hướng kiến trúc Promptfoo/Redis/Runner, nhưng bổ sung các quyết định sản phẩm quan trọng để tool dùng được trong quy trình QC thật thay vì chỉ là một execution dashboard.

```text
1. Bổ sung nguyên tắc sản phẩm: Excel là input/output quen thuộc, web app là QC workspace thông minh ở giữa.
2. Chuyển Dataset từ uploaded file tĩnh thành Excel-compatible Dataset Workspace.
3. Dataset ACTIVE version vẫn immutable, nhưng QC có thể tạo DRAFT version để sửa cell/row inline như Excel.
4. Bổ sung bulk paste/upsert từ Excel vào DRAFT DatasetVersion.
5. Bổ sung publish/discard DRAFT DatasetVersion.
6. Bổ sung dataset export Excel trong MVP.
7. Bổ sung nguyên tắc: Machine result is advisory; QC reviewed result is authoritative for report/export.
8. Không sửa trực tiếp test_case_results khi QC override; lưu review riêng ở test_case_result_reviews.
9. Bổ sung final result semantics: machine result + QC review -> final result.
10. Bổ sung result review table/card/matrix workflow cho QC.
11. Bổ sung single-result review và bulk review selected testcase results.
12. Bổ sung TestRun result export Excel; default resultMode = FINAL.
13. Bổ sung ExportJob entity để quản lý export async cho dataset/result.
14. Bổ sung AuthRefreshToken entity nếu triển khai refresh token rotation/revoke.
15. Bổ sung dataset row revision để hỗ trợ inline edit/optimistic concurrency.
16. Cập nhật MVP scope, domain entities, database tables, invariants và success criteria theo ERD v0.5.
17. Bổ sung Project Setup Status để frontend setup wizard biết project đã ready chưa trước khi preflight.
18. Bổ sung Comparison Matrix contract để group result theo datasetRowId -> targets.
19. Bổ sung ProviderTestLog để trace lịch sử test provider/key/model đã được mask.
20. Chuẩn hóa ExportJobResponse chung cho dataset export và result export.
```

Các quyết định v0.5 về Promptfoo version pin, PromptfooAdapterContract, Redis Streams recovery, safe trimming, secret masking, sanitized artifact và cache disabled by default vẫn giữ nguyên.

## 2.1. External Research Snapshot

Research nền từ PRD v0.5, vẫn giữ cho PRD v0.7:

```text
Research date: 2026-07-02
Promptfoo latest observed package version: 0.121.17
Promptfoo latest observed GitHub release: 0.121.17, release date 2026-06-16
Promptfoo install/runtime note: npm/npx require Node.js ^20.20.0 or >=22.22.0; Promptfoo docs recommend Node.js 24 for CI/custom Docker image.
Redis Streams recovery baseline: XAUTOCLAIM is available since Redis 6.2.0.
Redis Streams trimming note: XTRIM supports KEEPREF / DELREF / ACKED from Redis 8.2.
```

Source URLs checked:

```text
https://www.npmjs.com/package/promptfoo
https://github.com/promptfoo/promptfoo/releases
https://www.promptfoo.dev/docs/installation/
https://www.promptfoo.dev/docs/usage/node-api-reference/
https://www.promptfoo.dev/docs/configuration/caching/
https://redis.io/docs/latest/commands/xautoclaim/
https://redis.io/docs/latest/commands/xtrim/
```

Important rule:

```text
PRD không phụ thuộc trực tiếp vào latest Promptfoo version.
Runner phải pin exact version trong package-lock.json.
Upgrade Promptfoo chỉ được phép đi qua PromptfooAdapterContract regression suite.
```

---

## 3. Mục tiêu MVP

MVP cần đủ để QC hoàn thành flow làm việc thật từ setup, chạy test đến review/export báo cáo:

```text
1. Vào workspace.
2. Tạo project test chatbot.
3. Cấu hình secret/provider/model và xem lịch sử test provider đã được mask.
4. Paste cURL chatbot nội bộ.
5. Frontend parse cURL thành method, URL, headers, query params, body.
6. Mapping dataset fields vào request.
7. Import dataset từ Excel hoặc project khác.
8. Xem dataset dạng table/card/library.
9. Sửa dataset trên DRAFT version bằng inline cell edit, add/delete row và bulk paste từ Excel.
10. Publish DRAFT dataset thành ACTIVE version mới.
11. Export dataset ra Excel khi cần.
12. Xem Project Setup Status để biết project đã đủ điều kiện chạy SINGLE/COMPARISON chưa.
13. Cấu hình evaluator.
14. Chạy test bằng Promptfoo Runner.
15. Theo dõi tiến độ realtime từng testcase.
16. Xem machine result từng testcase.
17. QC review result, sửa final pass/fail/score nếu máy chấm sai và thêm note.
18. Chạy comparison giữa nhiều chatbot target và/hoặc baseline model.
19. Xem dashboard machine metrics và final reviewed metrics.
20. Debug testcase bằng request/response/extraction/evaluator reason/artifact.
21. Export final reviewed result ra Excel để báo cáo/nộp.
22. Cancel run khi cần.
```
---

## 4. Primary User

MVP chỉ tập trung vào nhóm user chính:

```text
QC / QA team chuyên test chatbot hoặc AI nội bộ của công ty
```

MVP chưa ưu tiên:

```text
- External customer
- Public SaaS user
- Business user không thuộc đội QC
- Developer platform user bên ngoài
```

---

# 5. Core Architecture

## 5.1. Kiến trúc tổng quan

```text
Frontend
  ├── cURL Parser UI
  ├── Dataset Import UI
  ├── Dataset Mapping UI
  ├── Evaluator Builder UI
  ├── Test Run Dashboard
  └── SSE Client for realtime progress

Backend Java
  ├── Auth / Permission
  ├── Workspace / Project Management
  ├── Secret / Provider Management
  ├── Dataset / Schema / Version Management
  ├── Chatbot Target Config Storage
  ├── Evaluator Config / Version Storage
  ├── Test Run Orchestration
  ├── Redis Stream Producer
  ├── Redis Stream Consumer
  ├── Redis Pending Recovery Worker
  ├── SSE Event Publisher
  ├── Result Persistence
  └── Artifact Metadata Storage

Redis Streams
  ├── vfqc:runs:commands
  ├── vfqc:runs:events
  ├── vfqc:runs:commands:dlq
  └── vfqc:runs:events:dlq

Runner Node.js
  ├── Redis Stream Consumer
  ├── Redis Pending Recovery Support
  ├── Promptfoo Config Builder
  ├── Promptfoo Node API Executor
  ├── Result Normalizer
  ├── Sensitive Data Masker
  ├── Local Artifact Writer
  └── Redis Stream Event Publisher

Local Artifact Storage
  └── ./storage/artifacts/test-runs/{testRunId}/
```

---

## 5.2. Source of Truth

Source of truth của hệ thống là **domain config trong backend database**:

```text
Backend Domain Config
  ├── Workspace
  ├── Project
  ├── Dataset / DatasetVersion
  ├── DatasetSchema / DatasetSchemaColumn
  ├── Chatbot Target
  ├── Evaluator / EvaluatorVersion
  ├── Provider / Secret Reference
  └── Test Run
```

Mỗi test run phải lưu snapshot/audit metadata:

```text
- domain_config_snapshot_json              # sanitized compact snapshot
- domain_config_hash                       # SHA-256 của canonical domain snapshot
- promptfoo_config_snapshot_json           # sanitized compact generated config snapshot
- promptfoo_config_hash                    # SHA-256 của canonical promptfoo config snapshot
- promptfoo_version
- promptfoo_adapter_schema_version
- dataset_id
- dataset_version_id
- dataset_schema_id
- dataset_schema_version
- evaluator versions
- judge_provider_id
- judge_model
- test_run_targets
- artifact paths
- config_sha256 / result_sha256 ở artifact metadata
- normalized result trong database
```

Quyết định security:

```text
1. DB không lưu plain text secret.
2. DB không lưu raw_curl nguyên bản nếu cURL có Authorization/API key/token/cookie.
3. promptfoo_config_snapshot_json trong DB chỉ là sanitized compact snapshot.
4. Full Promptfoo config/result/report/log artifact phải qua VfqcSensitiveDataMasker trước khi ghi file.
5. Full generated config file để artifact storage, DB chỉ giữ compact snapshot + hash.
```

Lý do:

```text
1. Debug được run cũ.
2. Result cũ không bị đổi nghĩa khi config mới thay đổi.
3. Audit chính xác runner đã chạy gì mà không lộ secret.
4. Có thể đổi execution engine hoặc upgrade Promptfoo mà không phá domain model.
5. Có thể verify artifact integrity bằng hash.
```

---

# 6. Workspace Design

## 6.1. Workspace Type

MVP hỗ trợ:

```text
PERSONAL
TEAM
```

### Personal Workspace

Được tạo tự động khi user đăng ký.

```text
User mới → Personal Workspace → user là OWNER
```

### Team Workspace

Dùng cho team QC làm việc chung.

Team Workspace chứa:

```text
- Members
- Projects
- Workspace Secrets
- Workspace Providers
- Workspace Datasets
- Shared test configs
```

---

## 6.2. Role Model

Hệ thống có 2 tầng role:

```text
1. System role:
   users.system_role = PLATFORM_ADMIN | USER

2. Workspace role:
   workspace_members.role = OWNER | ADMIN | MEMBER | VIEWER
```

Rule tổng quát:

```text
1. Nếu action là xem lại plain text secret:
   DENY cho mọi role, kể cả PLATFORM_ADMIN.

2. Nếu users.system_role = PLATFORM_ADMIN:
   ALLOW gần như mọi action còn lại.

3. Nếu users.system_role = USER:
   Check workspace_members.role.

4. Nếu user không phải member workspace:
   DENY.
```

---

## 6.3. Permission Matrix MVP

| Action                  | Owner | Admin | Member | Viewer |
| ----------------------- | ----: | ----: | -----: | -----: |
| Xem workspace           | Có    | Có    | Có     | Có     |
| Sửa workspace           | Có    | Có    | Không  | Không  |
| Xóa/archive workspace   | Có    | Không | Không  | Không  |
| Mời member              | Có    | Có    | Không  | Không  |
| Đổi role member         | Có    | Có    | Không  | Không  |
| Tạo project             | Có    | Có    | Có     | Không  |
| Sửa project             | Có    | Có    | Có     | Không  |
| Xem project             | Có    | Có    | Có     | Có     |
| Import dataset          | Có    | Có    | Có     | Không  |
| Reuse dataset           | Có    | Có    | Có     | Không  |
| Cấu hình chatbot target | Có    | Có    | Có     | Không  |
| Cấu hình evaluator      | Có    | Có    | Có     | Không  |
| Chạy test               | Có    | Có    | Có     | Không  |
| Cancel test run         | Có    | Có    | Có     | Không  |
| Xem dashboard           | Có    | Có    | Có     | Có     |
| Tạo Workspace Secret    | Có    | Có    | Không  | Không  |
| Tạo Workspace Provider  | Có    | Có    | Không  | Không  |
| Tạo Personal Secret     | Có    | Có    | Có     | Không  |
| Tạo Personal Provider   | Có    | Có    | Có     | Không  |
| Xem plain text secret   | Không | Không | Không  | Không  |

---

# 7. Secret & Provider Design

## 7.1. Secret Scope

MVP hỗ trợ:

```text
PERSONAL
WORKSPACE
```

### Personal Secret

Do user tạo.

Dùng khi:

```text
- QC có API key riêng.
- Workspace key hết quota.
- Workspace key invalid.
- QC muốn fallback sang provider cá nhân.
```

### Workspace Secret

Do Owner/Admin tạo.

Dùng khi:

```text
- Admin setup sẵn key cho team QC.
- Team dùng chung provider/model.
- QC không cần tự nhập API key.
```

Không role nào được xem lại plain text secret sau khi secret đã lưu.

---

## 7.2. Provider Scope

MVP hỗ trợ:

```text
PERSONAL_PROVIDER
WORKSPACE_PROVIDER
```

Provider có thể dùng cho:

```text
- LLM Judge
- Baseline model trong comparison run
- Dataset generation ở phase sau
```

Provider type MVP:

```text
OPENAI_COMPATIBLE
GEMINI
CLAUDE
CUSTOM_HTTP
```

Provider entity cần lưu:

```text
id
scope
owner_user_id
workspace_id
name
provider_type
secret_id
base_url
default_model
available_models_json
config_json
status
created_by
created_at
updated_at
deleted_at
```

---

# 8. Project Design

Project là nơi QC cấu hình test cho một chatbot hoặc một nhóm chatbot liên quan.

Một project thuộc một workspace.

Project chứa:

```text
- Dataset Schema
- Datasets
- Chatbot Targets
- Evaluators
- Test Runs
- Comparison Runs
- Reports / Dashboard
```

Project entity:

```text
id
workspace_id
name
description
created_by
created_at
updated_at
archived_at
deleted_at
```

MVP chưa có project-level permission riêng.

---

# 8.1. Project Setup Status / Readiness

Frontend cần một readiness endpoint để làm setup wizard mượt hơn thay vì chỉ đợi đến TestRun preflight mới báo thiếu config.

Endpoint contract chính:

```http
GET /api/v1/projects/{projectId}/setup-status
```

Project Setup Status aggregate các tín hiệu:

```text
- Có dataset chưa
- Có ACTIVE DatasetVersion có row chưa
- Có ACTIVE chatbot target chưa
- Chatbot target đã có response extraction chưa
- Có evaluator version chưa
- Có usable judge provider/baseline provider chưa
- Có đủ comparison candidates chưa
```

Rule sản phẩm:

```text
1. setup-status dùng cho UI wizard và dashboard readiness.
2. setup-status không thay thế preflight.
3. preflight vẫn là bước validate cuối cho một run request cụ thể.
4. setup-status không expose secret, raw cURL, raw response/request hay artifact.
5. setup-status phải phân biệt canRunSingle và canRunComparison.
```

---

# 8.2. Provider Test History

MVP lưu lịch sử test provider ở mức sanitized để QC/admin debug key/provider/model.

Entity đề xuất:

```text
llm_provider_test_logs
```

Lưu:

```text
- provider_id
- status SUCCESS/FAILED/ERROR
- model
- request_json_sanitized
- response_preview masked
- error_code/error_message sanitized
- latency_ms
- token_usage_json
- tested_by
- tested_at
```

Rule:

```text
1. Mỗi lần provider test tạo một log row.
2. Không lưu plaintext secret/API key/token/cookie.
3. Không dùng provider test log làm bằng chứng cho TestRun cũ; TestRun vẫn dựa trên locked snapshot/artifact riêng.
```

---

# 8.3. Comparison Matrix Result View

Comparison run cần view group theo dataset row để QC so sánh nhiều target dễ hơn.

Endpoint contract chính:

```http
GET /api/v1/test-runs/{testRunId}/comparison-matrix
```

Shape chính:

```text
TestRun
  targets[]                    # cột so sánh ổn định
  rows[]
    datasetRowId
    datasetRowIndex
    inputPreview
    cells[testRunTargetId]     # mỗi cell là kết quả target cho row đó
    rowSummary
```

Rule:

```text
1. Matrix endpoint dùng cho UI so sánh lớn, tránh frontend tự group từ flat pagination.
2. Cell chỉ trả preview đã mask; detail debug vẫn gọi testcase result detail.
3. Final result trong cell phải áp dụng QC override khi có.
```

---

# 9. Dataset Schema Design

## 9.1. Schema Scope v0.5

v0.5 sửa dataset schema sang **scope-aware model** để khớp với dataset scope.

MVP hỗ trợ schema scope:

```text
PROJECT
WORKSPACE
PERSONAL
```

Scope rule:

```text
PROJECT:
  project_id NOT NULL
  workspace_id NULL
  owner_user_id NULL

WORKSPACE:
  workspace_id NOT NULL
  project_id NULL
  owner_user_id NULL

PERSONAL:
  owner_user_id NOT NULL
  workspace_id NULL
  project_id NULL
```

Lý do đổi so với v0.4:

```text
1. v0.4 để dataset_schemas.project_id bắt buộc, trong khi datasets có scope PROJECT / WORKSPACE / PERSONAL.
2. Nếu workspace/personal dataset vẫn dùng schema thuộc project thì domain bị lệch.
3. Schema scope-aware giúp dataset dùng chung trong workspace/personal có schema độc lập.
4. Khi import dataset vào project, hệ thống có thể clone/mapping schema rõ ràng.
```

---

## 9.2. Default Schema

Khi tạo project, hệ thống tạo schema mặc định scope PROJECT:

```text
question
expected
```

Khi tạo workspace/personal dataset độc lập, hệ thống cũng tạo schema tương ứng với scope của dataset.

User có thể:

```text
- Đổi tên cột
- Thêm cột
- Sửa cột
- Xóa cột
- Đổi mô tả
- Set required / optional
```

Ví dụ schema mở rộng:

```text
question
expected
context
category
difficulty
expected_intent
expected_sources
```

---

## 9.3. Schema Versioning

Schema cần có version.

Lý do:

```text
1. Import Excel có thể thêm cột mới.
2. Import dataset từ project/workspace/personal dataset khác có thể khác schema.
3. Evaluator mapping phụ thuộc vào tên cột.
4. Test run cũ cần biết chính xác schema lúc chạy.
```

Test run phải lock:

```text
dataset_schema_id
dataset_schema_version
```

---

## 9.4. Schema Compatibility Rule

Khi reuse/import dataset:

```text
1. Nếu source schema giống target schema → clone rows trực tiếp.
2. Nếu source schema khác target schema → bắt buộc mapping step.
3. Nếu user thêm cột mới khi import → tạo schema version mới ở target scope.
4. TestRun luôn dùng schema đã lock, không phụ thuộc current schema sau này.
```

---

# 10. Dataset Design

## 10.1. Dataset Scope

MVP hỗ trợ:

```text
PROJECT
WORKSPACE
PERSONAL
```

### Project Dataset

Dataset thuộc project hiện tại.

### Workspace Dataset

Dataset dùng chung trong workspace.

### Personal Dataset

Dataset thuộc user, có thể reuse ở workspace khác nếu user có quyền trong project đích.

---

## 10.2. Dataset Versioning

Dataset có nhiều version:

```text
Dataset
  ├── DatasetVersion v1
  ├── DatasetVersion v2
  └── DatasetVersion v3
```

Rule:

```text
1. DatasetVersion ACTIVE là immutable.
2. QC không sửa trực tiếp row/cell của ACTIVE version.
3. Muốn sửa dataset, hệ thống tạo DatasetVersion DRAFT từ version hiện tại.
4. QC sửa rows/cells trên DRAFT version.
5. Publish DRAFT tạo version ACTIVE mới và update datasets.current_version_id.
6. TestRun lock dataset_version_id.
7. Dataset rows thuộc DatasetVersion, không thuộc trực tiếp Dataset.
8. current_version_id trên datasets trỏ tới version đang active/current.
```

Lý do:

```text
- Đảm bảo run cũ không đổi kết quả khi dataset được sửa.
- Multi-target comparison run công bằng vì mọi target dùng chung dataset_version_id.
- Có thể trace dataset clone/import từ version nguồn.
```

---

## 10.3. Dataset Editable Workspace

Dataset không chỉ là file Excel upload lên rồi chạy test. Dataset là workspace để QC làm việc với testcase theo cách quen thuộc với Excel nhưng có audit/versioning tốt hơn.

UI MVP cần hỗ trợ tối thiểu:

```text
1. Table view giống Excel để scan nhiều testcase.
2. Card/library view để đọc testcase dài dễ hơn.
3. Inline cell edit trên DRAFT version.
4. Add/delete row trên DRAFT version.
5. Bulk paste từ Excel vào DRAFT version.
6. Filter/sort/search/pagination cho dataset rows.
7. Export dataset version ra Excel.
```

Lifecycle:

```text
ACTIVE DatasetVersion
  -> Create DRAFT version
  -> Edit rows/cells in DRAFT
  -> Bulk paste from Excel if needed
  -> Validate required columns/schema
  -> Publish DRAFT as new ACTIVE version
  -> Old ACTIVE versions remain immutable and readable
```

Draft rule:

```text
1. Chỉ DRAFT DatasetVersion được mutate rows/cells.
2. ACTIVE/ARCHIVED DatasetVersion chỉ read/export được.
3. DRAFT phải lưu base_version_id để biết được tạo từ version nào.
4. MVP có thể chọn policy 1 open DRAFT per dataset hoặc 1 open DRAFT per user per dataset; backend spec phải chốt rõ.
5. Bulk paste phải giới hạn số row/cell mỗi request để tránh treo frontend/backend.
6. DatasetRow nên có revision để hỗ trợ optimistic concurrency cho inline edit.
```

Lý do:

```text
1. QC quen thao tác với Excel: click cell, sửa nhanh, paste nhiều dòng.
2. Web app phải giảm bottleneck chứ không ép QC bỏ toàn bộ workflow quen thuộc.
3. Versioning giữ audit cho run cũ và comparison fairness.
```

---

## 10.4. Mapping Dataset sang Promptfoo

Mỗi dataset row sẽ trở thành một Promptfoo test case.

Ví dụ dataset row:

```json
{
  "question": "VF 8 có mấy phiên bản?",
  "expected": "VF 8 có các phiên bản Eco và Plus.",
  "context": "Thông tin sản phẩm VF 8",
  "category": "product_info",
  "difficulty": "easy"
}
```

Runner convert thành Promptfoo test case:

```yaml
tests:
  - vars:
      question: "VF 8 có mấy phiên bản?"
      expected: "VF 8 có các phiên bản Eco và Plus."
      context: "Thông tin sản phẩm VF 8"
    metadata:
      category: "product_info"
      difficulty: "easy"
```

---

## 10.5. UI Variable Syntax

Trong UI, QC nhìn thấy biến theo dạng dễ hiểu:

```text
$dataset.question
$dataset.expected
$dataset.context
```

Runner convert sang Promptfoo template syntax:

```text
{{question}}
{{expected}}
{{context}}
```

Quyết định này giúp:

```text
1. UI thân thiện với QC.
2. Backend domain không bị phụ thuộc trực tiếp vào Promptfoo syntax.
3. Runner chịu trách nhiệm mapping sang Promptfoo.
```

---

# 11. Import Dataset

## 11.1. Import từ Excel

Flow:

```text
1. QC upload Excel.
2. Hệ thống đọc sheets/cột.
3. QC chọn sheet.
4. UI preview 10-20 dòng đầu.
5. QC mapping Excel columns với project schema.
6. Với từng cột Excel, QC chọn:
   - Map vào cột schema hiện có
   - Ignore
   - Add as new schema column
7. Nếu add new column, hệ thống tạo schema version mới.
8. QC xác nhận import.
9. Backend lưu dataset + dataset version + dataset rows.
```

---

## 11.2. Import Dataset từ Project khác

Entry point:

```text
Project Detail → Datasets tab → Import Dataset
```

Modal import có options:

```text
- From Excel
- From another project
- From workspace dataset
- From my datasets
```

MVP ưu tiên flow **From another project**.

---

## 11.3. Flow: From another project

```text
1. QC bấm Import Dataset.
2. Chọn From another project.
3. Modal hiển thị search bar + list project user có quyền xem.
4. QC search/chọn project nguồn.
5. Modal hiển thị list dataset trong project nguồn.
6. QC chọn dataset và dataset version nguồn.
7. UI hiển thị preview dataset.
8. Hệ thống check schema compatibility.
9. Nếu schema giống, cho clone luôn.
10. Nếu schema khác, mở mapping step.
11. QC mapping cột source dataset sang schema project hiện tại.
12. QC xác nhận clone.
13. Backend clone toàn bộ dataset rows vào dataset version mới trong project hiện tại.
14. Dataset mới lưu source metadata để trace.
```

MVP clone toàn bộ rows.

Chưa hỗ trợ:

```text
- Clone selected rows
- Live reference dataset
- Auto sync dataset từ source
```

Dataset clone cần lưu:

```text
source_dataset_id
source_dataset_version_id
source_project_id
source_workspace_id
source_type
import_mapping_json
created_by
created_at
```

---

---

## 11.4. Dataset Export Excel

Dataset export là in-scope trong MVP vì Excel vẫn là input/output quen thuộc của QC.

Flow:

```text
1. QC mở DatasetVersion.
2. QC bấm Export Excel.
3. Backend tạo ExportJob type DATASET_VERSION.
4. Export file giữ thứ tự cột theo dataset_schema_columns.order_index.
5. Export file có row_index để trace về web UI.
6. Export không expose server-local path; UI tải qua download URL/API.
```

Rule:

```text
1. Export không mutate dataset.
2. Export chỉ được thực hiện nếu user có quyền xem dataset.
3. VIEWER có thể được xem dataset nhưng MVP không cho export nếu policy muốn export là write-like action.
4. Exported files phải có retention/expiry policy.
```


# 12. Chatbot Target Design

## 12.1. MVP chỉ hỗ trợ cURL

Flow:

```text
1. QC paste raw cURL ở frontend.
2. Frontend parse thành:
   - method
   - url
   - headers
   - query params
   - body
3. Frontend hiển thị preview.
4. QC chỉnh sửa nếu cần.
5. QC mapping dataset fields vào request.
6. Frontend gửi backend:
   - sanitizedRawCurl
   - parsedRequestConfig
   - datasetMapping
   - extractedSecretCandidates
7. Backend validate và tách secret thật sang Secret entity nếu có.
8. Backend lưu sanitized request config.
9. Backend/Runner test connection.
10. Hệ thống lưu example request và example response đã mask.
```

Backend không parse lại raw cURL bằng shell.

Backend không shell-execute raw cURL.

Backend lưu sanitized cURL để trace/audit, còn khi chạy thật dùng parsed request config + secret reference.

---

## 12.2. cURL & Secret Security Policy v0.5

Quyết định bắt buộc:

```text
1. Không lưu raw_curl nguyên bản nếu có Authorization/API key/token/cookie/credential.
2. raw_curl lưu trong DB chỉ là bản sanitized.
3. Secret thật được tách thành Secret entity.
4. Header/body/query chứa secret phải được thay bằng secret reference hoặc env placeholder.
5. promptfoo_config_snapshot_json trong DB là sanitized compact snapshot.
6. Full artifact cũng phải đi qua VfqcSensitiveDataMasker trước khi ghi file.
7. Lưu config_hash/result_hash để audit.
```

Ví dụ sanitized cURL:

```bash
curl -X POST "https://internal-chatbot.example.com/api/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ****MASKED****" \
  -d '{"question":"$dataset.question"}'
```

Khi runner build request thật:

```text
1. Runner lấy run config đã lock từ Backend API.
2. Runner lấy secret thông qua secure secret resolution flow.
3. Runner inject secret vào request runtime only.
4. Runner không ghi secret vào DB/log/artifact/event.
```

---

## 12.3. Chatbot Target Entity

```text
id
project_id
name
description
raw_curl_sanitized
raw_curl_hash
method
url
headers_json              # sanitized
query_params_json         # sanitized
body_json                 # sanitized
auth_config_json          # secret references only
dataset_mapping_json
latest_example_id
response_extraction_config_json
status
created_by
created_at
updated_at
deleted_at
```

---

## 12.4. Chatbot Target Example History

Mỗi lần test connection tạo một row mới trong `chatbot_target_examples`.

Lý do:

```text
1. Lưu lịch sử request/response khi QC test connection.
2. Debug được việc target từng hợp lệ/invalid theo thời gian.
3. latest_example_id trên chatbot_targets chỉ trỏ tới example mới nhất.
4. Sensitive fields phải được mask trước khi lưu.
```

Example entity:

```text
id
chatbot_target_id
request_json              # sanitized
response_status_code
response_headers_json     # sanitized
response_body_json        # sanitized
extracted_output_json
latency_ms
error_message
tested_by
created_at
```

---

## 12.5. Response Extraction

Khi test connection, hệ thống lưu example response đã mask.

Ví dụ:

```json
{
  "status": "success",
  "data": {
    "answer": "VF 8 có hai phiên bản Eco và Plus.",
    "intent": "product_info",
    "confidence": 0.92
  },
  "metadata": {
    "latency": 1200
  }
}
```

QC chọn field chính:

```text
Main answer path: $.data.answer
Intent path: $.data.intent
Confidence path: $.data.confidence
```

Runner convert main answer path thành Promptfoo `transformResponse`.

---

# 13. Evaluator Design

## 13.1. Evaluator Types

MVP hỗ trợ:

```text
RULE_BASED
LLM_JUDGE
MIXED
```

---

## 13.2. Rule-based Evaluator

Rule gồm:

```text
field_source
operator
expected_value
weight
required
order_index
config_json
```

Ví dụ:

```text
response.status equals "success"
response.data.intent equals $dataset.expected_intent
response.data.answer contains $dataset.expected
response.data.confidence greater_than_or_equal 0.7
latency_ms less_than 3000
```

Operator MVP:

```text
equals
not_equals
contains
not_contains
regex
exists
not_exists
is_empty
is_not_empty
greater_than
greater_than_or_equal
less_than
less_than_or_equal
is_json
contains_json
```

Không expose `custom_javascript` trong MVP UI.

Mapping sang Promptfoo:

| UI Operator              | Promptfoo Mapping / Adapter Strategy |
| ------------------------ | ------------------------------------ |
| equals                   | `equals`                             |
| contains                 | `contains`                           |
| regex                    | `regex`                              |
| is_json                  | `is-json`                            |
| contains_json            | `contains-json`                      |
| greater_than / less_than | Adapter-generated safe JS assertion  |
| complex JSON path        | Adapter-generated safe JS assertion  |

Ghi chú:

```text
Adapter-generated JS assertion là code do hệ thống generate từ operator an toàn.
User/QC không nhập arbitrary JavaScript trong MVP.
```

---

## 13.3. LLM Judge Evaluator

LLM Judge config gồm:

```text
name
judge_provider_id
judge_model
rubric
threshold
score_scale
pass_condition
input_template
output_schema
```

Ví dụ rubric:

```text
Chấm câu trả lời theo thang điểm 0-10.
Pass nếu score >= 7.

Tiêu chí:
1. Đúng ý chính so với expected.
2. Không hallucinate.
3. Không bỏ sót thông tin quan trọng.
4. Trả lời rõ ràng, phù hợp ngữ cảnh.
```

Runner map sang Promptfoo model-graded assertion, ví dụ `llm-rubric`.

---

## 13.4. LLM Judge Output Normalization

LLM Judge Output Normalization là bắt buộc trong MVP.

Rule:

```text
1. Judge prompt yêu cầu output JSON schema cố định.
2. Runner parse tolerant JSON.
3. Nếu provider có reasoning/thinking field riêng, bỏ reasoning khỏi verdict parser.
4. Nếu parse fail, testcase status = ERROR hoặc JUDGE_ERROR, không coi là business fail.
5. Lưu raw judge response đã mask vào artifact để debug.
```

Canonical judge output schema:

```json
{
  "score": 0.86,
  "passed": true,
  "reason": "Answer matches the expected product information and does not hallucinate.",
  "errors": [],
  "criteria": [
    {
      "name": "correctness",
      "score": 0.9,
      "reason": "Main facts are correct."
    }
  ]
}
```

Parse rule:

```text
1. Nếu output là valid JSON → parse trực tiếp.
2. Nếu output có text trước/sau JSON → extract JSON object đầu tiên hợp lệ.
3. Nếu provider trả reasoning/thinking riêng → ignore field đó khi parse verdict.
4. Nếu không parse được → component result = JUDGE_ERROR.
5. JUDGE_ERROR làm testcase status = ERROR, không phải FAILED business-level.
```

---

## 13.5. Custom JavaScript Decision

MVP chọn:

```text
Option A: bỏ custom_javascript khỏi MVP UI.
```

Lý do:

```text
1. User-provided JavaScript là arbitrary code execution.
2. Runner có thể truy cập network/filesystem/secret runtime nếu sandbox không chặt.
3. MVP ưu tiên operator an toàn và adapter-generated JS nội bộ.
4. Nếu cần custom JS ở phase sau, phải có sandbox/container isolation, timeout, allowlist API và role restriction.
```

Out of scope MVP:

```text
- QC nhập JavaScript trực tiếp.
- Upload JS assertion file.
- External JS provider do user nhập.
```

---

## 13.6. Evaluator Versioning

Evaluator cần versioning.

```text
Evaluator
  ├── EvaluatorVersion v1
  └── EvaluatorVersion v2
```

Rule:

```text
1. Đổi rule/rubric tạo EvaluatorVersion mới.
2. EvaluatorVersion đã được TestRun dùng thì không update.
3. Rule-based rules thuộc evaluator_versions qua bảng evaluator_rules.
4. TestRun lock evaluator versions qua test_run_evaluator_versions.
```

Lý do:

```text
1. Rubric đổi sẽ làm result đổi.
2. Rule đổi sẽ làm pass/fail đổi.
3. Comparison chỉ công bằng nếu dùng cùng evaluator version.
```

---

# 14. Multi-target Comparison Design

## 14.1. Mục tiêu

Comparison giúp QC so sánh **hai hoặc nhiều target** trong cùng một điều kiện test.

Comparison không chỉ là chatbot nội bộ so với LLM baseline. MVP phải support cả các case:

```text
1. Chatbot nội bộ A vs chatbot nội bộ B.
2. Chatbot nội bộ hiện tại vs chatbot nội bộ phiên bản mới.
3. Chatbot môi trường dev/staging/prod nếu được cấu hình thành các Chatbot Target riêng.
4. Chatbot nội bộ vs một hoặc nhiều LLM baseline.
5. Nhiều chatbot nội bộ + nhiều LLM baseline trong cùng một run.
```

Target hợp lệ trong comparison:

```text
CHATBOT_TARGET
LLM_BASELINE
```

Valid MVP compositions:

```text
CHATBOT_TARGET + CHATBOT_TARGET
CHATBOT_TARGET + LLM_BASELINE
CHATBOT_TARGET + CHATBOT_TARGET + LLM_BASELINE
LLM_BASELINE + LLM_BASELINE nếu QC cần model-only baseline evaluation
```

Rule quan trọng:

```text
Backend không được hard-code comparison = đúng 1 CHATBOT_TARGET + baseline.
Comparison chỉ yêu cầu có ít nhất 2 test_run_targets hợp lệ.
```

Promptfoo phù hợp với mô hình này vì runner có thể build một Promptfoo config có nhiều providers/targets và sinh result matrix cho cùng một bộ tests.

---

## 14.2. Điều kiện comparison công bằng

Các target trong cùng comparison run phải dùng chung:

```text
- Dataset
- Dataset version
- Schema version
- Evaluator versions
- Judge provider
- Judge model
- Run config snapshot
```

Chỉ khác:

```text
- Target chatbot/model/provider/config được lock vào test_run_targets
```

Mỗi dataset row sẽ sinh một result riêng cho từng target:

```text
unique(test_run_id, test_run_target_id, dataset_row_id)
```

---

## 14.3. Comparison Dashboard MVP

Dashboard hiển thị:

```text
- Target list theo thứ tự user chọn
- Machine pass rate theo target
- Final QC-reviewed pass rate theo target
- Average machine/final score theo target
- Average latency theo target
- Failed count theo target
- Error count theo target
- Cost/token nếu có
- Testcase matrix theo target
- Judge reason từng target
- QC review/final verdict từng target nếu có
```

MVP chưa cần auto winner bằng LLM.

Có thể hiển thị đơn giản:

```text
- Highest final score
- Lowest latency
- Passed targets
- Target có nhiều QC override nhất
```

---

# 15. Execution Architecture v0.5

## 15.1. Queue MVP

MVP dùng **Redis Streams** để giao tiếp giữa Backend và Runner.

Redis Streams phù hợp vì:

```text
1. Có append-only stream cho command/event.
2. Có consumer groups để nhiều runner chia việc.
3. Có pending entries để recover khi consumer crash.
4. Có ACK để kiểm soát xử lý at-least-once.
5. Có thể trim stream để kiểm soát dung lượng.
```

MVP dùng 4 streams:

```text
vfqc:runs:commands
vfqc:runs:events
vfqc:runs:commands:dlq
vfqc:runs:events:dlq
```

Consumer groups:

```text
vfqc:runs:commands
  group: vfqc-runners
  consumers: runner instance ids

vfqc:runs:events
  group: vfqc-backend-events
  consumers: backend instance ids
```

---

## 15.2. `vfqc:runs:commands`

Backend publish command.

Runner consume command.

Command chỉ chứa reference, không chứa full dataset/config/secret.

Ví dụ START_RUN:

```json
{
  "commandId": "cmd_123",
  "commandType": "START_RUN",
  "testRunId": "run_123",
  "projectId": "project_456",
  "requestedBy": "user_789",
  "createdAt": "2026-07-01T10:00:00+07:00"
}
```

Ví dụ CANCEL_RUN:

```json
{
  "commandId": "cmd_124",
  "commandType": "CANCEL_RUN",
  "testRunId": "run_123",
  "requestedBy": "user_789",
  "createdAt": "2026-07-01T10:02:00+07:00"
}
```

---

## 15.3. `vfqc:runs:events`

Runner publish event.

Backend consume event.

Redis event chứa **normalized result compact**, không chứa artifact lớn.

Ví dụ TESTCASE_COMPLETED:

```json
{
  "eventId": "evt_001",
  "eventType": "TESTCASE_COMPLETED",
  "testRunId": "run_123",
  "runnerId": "runner_01",
  "datasetRowId": "row_001",
  "targetId": "target_internal_bot",
  "passed": true,
  "score": 0.86,
  "latencyMs": 1200,
  "completed": 1,
  "total": 100,
  "createdAt": "2026-07-01T10:00:15+07:00"
}
```

Full artifact lưu local hoặc object storage abstraction.

Không nhét full report hoặc file lớn vào Redis Stream.

---

## 15.4. Communication Flow

```text
1. User click Run.
2. Backend validate permission.
3. Backend validate max dataset rows per run.
4. Backend lock dataset/schema/evaluator versions.
5. Backend tạo test_run status = PENDING.
6. Backend tạo runner_commands row với command_id unique.
7. Backend XADD START_RUN vào vfqc:runs:commands.
8. Runner consume START_RUN bằng XREADGROUP.
9. Runner check idempotency và test_run.status.
10. Runner lấy full run config từ Backend API.
11. Runner publish RUN_STARTED.
12. Runner build Promptfoo config.
13. Runner write promptfoo config artifact.
14. Runner chạy Promptfoo bằng Node API.
15. Runner publish TESTCASE_STARTED/TESTCASE_COMPLETED/RUN_PROGRESS events.
16. Backend consume events bằng XREADGROUP.
17. Backend persist result vào DB.
18. Backend ACK event sau khi persist thành công.
19. Backend đẩy realtime progress xuống frontend qua SSE.
20. Khi run xong, runner publish RUN_COMPLETED/RUN_FAILED/RUN_CANCELLED.
21. Backend persist final state.
22. Runner ACK command sau khi final event publish thành công.
```

---

## 15.5. Backend ↔ Runner Data Contract

Runner gọi Backend API để lấy full config:

```text
GET /internal/runner/test-runs/{testRunId}/config
```

Lý do command chỉ chứa reference:

```text
1. Redis message nhỏ.
2. Không expose secret/dataset lớn trong queue.
3. Backend vẫn là source of truth.
4. Runner luôn lấy config đã lock theo test run.
5. Dễ retry/recover vì config lấy lại được từ DB.
```

Control API cho cancellation:

```text
GET /internal/runner/test-runs/{testRunId}/control-state
```

Response:

```json
{
  "testRunId": "run_123",
  "status": "CANCELLING",
  "shouldCancel": true
}
```

---

## 15.6. ACK Rule

### Runner ACK command

Runner chỉ ACK START_RUN khi:

```text
1. Run đã COMPLETED/FAILED/CANCELLED; và
2. Runner đã publish final event thành công; và
3. Artifact final đã được write xong hoặc lỗi artifact đã được ghi nhận thành RUN_FAILED.
```

Runner ACK CANCEL_RUN khi:

```text
1. Runner đã ghi nhận cancel; hoặc
2. Run đã ở terminal state nên command được SKIPPED idempotently.
```

### Backend ACK event

Backend chỉ ACK event khi:

```text
1. Event đã được persist vào DB; và
2. Side effect tương ứng đã hoàn tất:
   - update test_runs progress/status
   - insert/update test_case_results
   - update runner_events.processed_at
3. Nếu persist fail thì không ACK để message nằm pending và recover sau.
```

---

## 15.7. Pending Recovery

v0.5 chốt **có pending recovery trong MVP**.

Cơ chế:

```text
1. Runner recovery worker inspect pending command bằng XPENDING.
2. Nếu message idle quá threshold, runner dùng XAUTOCLAIM để claim về consumer khỏe.
3. Backend event recovery worker inspect pending event bằng XPENDING.
4. Nếu event idle quá threshold, backend dùng XAUTOCLAIM để claim lại.
5. Mỗi message tăng attempt_count trong DB hoặc payload metadata.
6. Nếu attempt_count > maxAttempts thì chuyển sang DLQ.
```

Default:

```text
pendingIdleThresholdMs = 60_000
recoveryIntervalMs = 30_000
maxAttempts = 3
```

Rule:

```text
- Pending command chưa ACK có thể được runner khác claim lại.
- Pending event chưa ACK có thể được backend instance khác claim lại.
- Handler phải idempotent vì Redis Streams delivery là at-least-once.
```

---

## 15.8. Dead-letter Streams

MVP dùng 2 dead-letter streams:

```text
vfqc:runs:commands:dlq
vfqc:runs:events:dlq
```

Khi message vượt quá `maxAttempts`, consumer publish DLQ event:

```json
{
  "originalStream": "vfqc:runs:commands",
  "originalMessageId": "1690000000000-0",
  "messageType": "START_RUN",
  "testRunId": "run_123",
  "attemptCount": 4,
  "lastError": "Backend config API timeout",
  "movedAt": "2026-07-01T10:05:00+07:00"
}
```

DLQ rule:

```text
1. DLQ không tự retry trong MVP.
2. Platform Admin có thể xem DLQ qua internal admin/debug page ở phase sau.
3. MVP cần log DLQ rõ ràng để backend/runner operator xử lý thủ công.
4. Nếu START_RUN vào DLQ, test_run chuyển FAILED với error_message system-level.
5. Nếu event final vào DLQ, backend recovery phải reconcile test_run bằng snapshot/results artifact nếu có.
```

---

## 15.9. Stream Retention / Trimming

Redis stream không được tăng vô hạn, nhưng trimming không được làm mất pending message chưa ACK.

MVP policy:

```text
vfqc:runs:commands:
  retention: keep last 100_000 messages hoặc 7 ngày, tùy config triển khai

vfqc:runs:events:
  retention: keep last 1_000_000 messages hoặc 7 ngày, tùy config triển khai

DLQ streams:
  retention: keep last 100_000 messages hoặc 30 ngày
```

Implementation option:

```text
- XADD ... MAXLEN ~ <limit> cho approximate trimming theo số lượng.
- Hoặc scheduled XTRIM MINID theo thời gian nếu cần retention theo ngày.
```

Safe trimming rule:

```text
1. Không trim command/event stream thấp hơn oldest pending message id.
2. Nếu dùng Redis >= 8.2, ưu tiên XTRIM ... ACKED cho stream có consumer group.
3. Nếu dùng Redis < 8.2, scheduled trimming phải check XPENDING trước.
4. Nếu XAUTOCLAIM trả về deleted/trimmed message ids, recovery worker không retry như message bình thường.
5. Deleted/trimmed pending message phải ghi reconcile log và chuyển test_run/event sang trạng thái recoverable hoặc DLQ tùy context.
```

Redis compatibility:

```text
Redis >= 6.2:
  - XAUTOCLAIM available.
  - Có thể dùng XPENDING + XAUTOCLAIM cho pending recovery.

Redis >= 8.2:
  - XTRIM hỗ trợ KEEPREF / DELREF / ACKED.
  - Nên dùng ACKED để tránh trim message chưa được mọi consumer group ACK.
```

DB vẫn là source of truth lâu dài.

Redis Streams chỉ là transport/recovery buffer ngắn hạn.

---

# 16. Runner Design

## 16.1. Runner Tech Stack

Runner là Node.js service.

MVP dùng:

```text
Promptfoo Node API only
```

Không dùng CLI fallback.

Lý do:

```text
1. Không cần spawn process.
2. Build Promptfoo config trực tiếp bằng object.
3. Dễ control execution context.
4. Dễ normalize result bằng code.
5. Phù hợp service chạy lâu dài.
6. Dễ pin version bằng package-lock.
```

---

## 16.2. Promptfoo Version Pin & Upgrade Rule

v0.5 chốt:

```text
1. Runner phải pin promptfoo exact version trong package.json/package-lock.json.
2. Không dùng promptfoo@latest trong runtime production.
3. Latest observed tại thời điểm PRD v0.5: 0.121.17.
4. package-lock.json là source of truth version thực tế khi deploy.
5. Khi tạo TestRun, backend/runner lưu promptfoo_version vào snapshot/artifact metadata.
6. Khi upgrade Promptfoo, bắt buộc chạy PromptfooAdapterContract regression suite.
```

Lưu vào:

```text
test_runs.domain_config_snapshot_json.promptfooVersion
test_runs.domain_config_snapshot_json.promptfooAdapterSchemaVersion
promptfoo_run_artifacts metadata
artifact ./storage/artifacts/test-runs/{testRunId}/metadata.json
```

Upgrade rule:

```text
1. Không upgrade trực tiếp trong production.
2. Upgrade trong branch riêng.
3. Chạy adapter regression suite với fixture cố định.
4. So sánh normalized result schema, không so sánh raw Promptfoo internal result một cách mù quáng.
5. Nếu Promptfoo output thay đổi, chỉ sửa PromptfooAdapter, không sửa domain model nếu không bắt buộc.
```

---

## 16.3. Runner Instance Model

Mỗi runner instance xử lý **1 test run tại một thời điểm**.

Scale bằng nhiều runner instances.

```text
runner-01 → run A
runner-02 → run B
runner-03 → run C
```

Lý do:

```text
1. MVP đơn giản.
2. Dễ giới hạn memory.
3. Dễ debug.
4. Dễ cancel.
5. Dễ scale ngang.
6. Tránh một process ôm quá nhiều run nặng.
```

---

## 16.4. Runner Heartbeat

v0.5 chốt **có heartbeat monitor trong MVP**.

Runner update heartbeat:

```text
runner_instances.last_heartbeat_at
runner_instances.status
runner_instances.current_test_run_id
```

Default:

```text
heartbeatIntervalSeconds = 10
runnerOfflineThresholdSeconds = 45
```

Backend monitor:

```text
1. Nếu runner không heartbeat quá threshold → status OFFLINE.
2. Nếu runner OFFLINE khi đang giữ current_test_run_id:
   - backend không tự mark run FAILED ngay lập tức.
   - command/event recovery sẽ reclaim pending message.
   - nếu quá maxAttempts hoặc timeout toàn run thì mark FAILED.
```

---

## 16.5. Runner Lifecycle

```text
1. Boot runner.
2. Register runnerId.
3. Start heartbeat loop.
4. Join Redis consumer group vfqc-runners.
5. Wait START_RUN command bằng XREADGROUP.
6. Claim one command.
7. Check idempotency.
8. Fetch full run config.
9. Publish RUN_STARTED.
10. Build Promptfoo config.
11. Write promptfooconfig.json artifact.
12. Execute Promptfoo Node API.
13. For each testcase:
    - check cancellation flag
    - run target/baseline
    - run evaluator/judge
    - mask sensitive fields
    - write partial artifact nếu cần
    - publish TESTCASE_COMPLETED
14. On completed:
    - write final result artifacts
    - publish RUN_COMPLETED
    - ACK command
15. On cancelled:
    - write partial/final cancel artifact
    - publish RUN_CANCELLED
    - ACK command
16. On failed:
    - write error artifact/log
    - publish RUN_FAILED
    - ACK command nếu lỗi đã được xử lý và final event publish thành công
```

---

# 17. Promptfoo Integration

## 17.1. Promptfoo Config Builder

Runner có component:

```text
PromptfooConfigBuilder
```

Input:

```text
- Test run config
- Dataset rows
- Chatbot targets
- Baseline providers
- Evaluator versions
- Judge provider/model
- Response extraction config
```

Output:

```text
Promptfoo TestSuiteConfiguration object
```

Runner build object trực tiếp, không cần sinh YAML để chạy.

---

## 17.2. PromptfooAdapterContract

v0.5 bổ sung adapter contract để hệ thống không bị lock cứng vào Promptfoo internal result/config format.

```text
Component: PromptfooAdapter
Input: LockedRunConfig
Output 1: PromptfooTestSuiteConfiguration
Output 2: NormalizedRunResult / NormalizedTestCaseResult
Adapter schema version: promptfoo_adapter_schema_version
```

Contract phải cover tối thiểu các fixture:

```text
1. HTTP target + transformResponse.
2. Rule-based only.
3. LLM judge only.
4. Mixed evaluator.
5. Comparison run nhiều targets.
6. Target timeout/error.
7. Judge parse error / reasoning output.
8. Sanitized artifact generation.
```

Upgrade Promptfoo chỉ được merge nếu các fixture trên pass.

---

## 17.3. Promptfoo Cache Policy

MVP chốt:

```text
Promptfoo cache is disabled by default for internal chatbot target runs.

Cache may be enabled only for controlled baseline/debug mode.
```

Rule:

```text
1. Internal chatbot target run: cache=false.
2. LLM baseline run: cache=false by default trong MVP để đảm bảo kết quả thật tại thời điểm chạy.
3. Controlled baseline/debug mode có thể bật cache nếu user/admin hiểu rõ mục đích.
4. Nếu cache enabled, run snapshot phải lưu cache_mode và cache_namespace.
5. Dashboard phải hiển thị rõ run có dùng cache hay không.
```

Lý do:

```text
QC cần biết chatbot nội bộ hiện tại trả lời gì.
Nếu cache bật ngầm, result có thể là response cũ và gây hiểu sai chất lượng model/chatbot.
```

---

## 17.4. Mapping Domain Config sang Promptfoo

| VinFast QC Copilot         | Promptfoo                  |
| -------------------------- | -------------------------- |
| Dataset row                | `tests[]`                  |
| Dataset columns            | `tests[].vars`             |
| `$dataset.question`        | `{{question}}`             |
| Chatbot Target HTTP config | HTTP provider              |
| Main answer path           | `transformResponse`        |
| Rule-based evaluator       | assertions                 |
| LLM Judge                  | model-graded assertion     |
| Comparison targets         | multiple providers/targets |
| Result artifacts           | JSON/JSONL/HTML output     |

---

## 17.5. Generated Promptfoo Config Example

Generated config trong artifact phải là sanitized config.

```yaml
description: "VinFast QC Copilot Run run_123"

prompts:
  - "{{question}}"

providers:
  - id: https
    label: "Internal Chatbot"
    config:
      url: "https://internal-chatbot.example.com/api/chat"
      method: POST
      headers:
        Content-Type: application/json
        Authorization: "Bearer {{env.INTERNAL_CHATBOT_TOKEN}}"
      body:
        context: "{{context}}"
        question: "{{question}}"
      transformResponse: "json.data.answer"

tests:
  - description: "TC001 - product info"
    vars:
      question: "VF 8 có mấy phiên bản?"
      expected: "VF 8 có các phiên bản Eco và Plus."
      context: "Thông tin sản phẩm VF 8"
    metadata:
      category: "product_info"
      difficulty: "easy"
    assert:
      - type: contains
        value: "VF 8"
      - type: llm-rubric
        value: |
          Câu trả lời phải đúng ý so với expected: {{expected}}.
          Không hallucinate.
          Không bỏ sót thông tin quan trọng.
        threshold: 0.7
```

Security rule:

```text
1. Không ghi plain API key vào generated config artifact.
2. Nếu Promptfoo cần env var, env var name được ghi lại, value không được ghi.
3. Generated artifact trước khi lưu phải qua VfqcSensitiveDataMasker.
```

---

# 18. Test Run State Machine

## 18.1. Status

```text
PENDING
RUNNING
CANCELLING
CANCELLED
COMPLETED
FAILED
```

---

## 18.2. State Transition

```text
PENDING → RUNNING
PENDING → CANCELLED
PENDING → FAILED
RUNNING → CANCELLING
CANCELLING → CANCELLED
RUNNING → COMPLETED
RUNNING → FAILED
CANCELLING → FAILED
```

Không cho transition:

```text
COMPLETED → RUNNING
FAILED → RUNNING
CANCELLED → RUNNING
```

Muốn chạy lại thì tạo `test_run` mới.

---

## 18.3. Status Meaning

### PENDING

Run đã tạo nhưng runner chưa bắt đầu.

### RUNNING

Runner đang chạy.

### CANCELLING

User đã yêu cầu cancel, backend đã ghi nhận, runner đang dừng an toàn.

### CANCELLED

Run đã bị cancel thành công.

### COMPLETED

Run hoàn tất.

### FAILED

Run lỗi hệ thống hoặc lỗi execution không recover được.

Lưu ý:

```text
Testcase fail do evaluator không làm test_run status = FAILED.
Đó là result hợp lệ của run.
```

---

# 19. Cancel Run Design

## 19.1. Quyết định

MVP dùng:

```text
Cancel command: HTTP POST
Realtime progress: SSE
Không dùng WebSocket trong MVP
```

SSE là một chiều từ backend xuống frontend, phù hợp cho progress/result. Cancel là command từ frontend lên backend nên dùng HTTP API.

---

## 19.2. Cancel Flow

```text
1. User click Cancel Run.
2. Frontend gọi POST /api/test-runs/{testRunId}/cancel.
3. Backend check permission.
4. Backend update test_run status = CANCELLING.
5. Backend publish CANCEL_RUN vào vfqc:runs:commands.
6. Runner nhận CANCEL_RUN nếu đang consume.
7. Runner cũng check DB/control API giữa các testcase để tránh miss command.
8. Runner dừng an toàn.
9. Runner publish RUN_CANCELLED vào vfqc:runs:events.
10. Backend consume event.
11. Backend update status = CANCELLED.
12. Backend đẩy SSE event RUN_CANCELLED xuống frontend.
```

---

## 19.3. Runner Cancellation Check

Runner check cancellation flag:

```text
- Trước khi bắt đầu run.
- Trước mỗi testcase.
- Sau mỗi testcase.
- Trước khi gọi baseline target tiếp theo.
- Trước khi gọi LLM Judge tiếp theo.
- Trước khi write final artifact.
```

Control API:

```text
GET /internal/runner/test-runs/{testRunId}/control-state
```

---

# 20. Realtime Progress via SSE

## 20.1. SSE Endpoint

Frontend mở SSE connection:

```text
GET /api/test-runs/{testRunId}/events
```

Backend push events:

```text
RUN_STARTED
TESTCASE_STARTED
TESTCASE_COMPLETED
RUN_PROGRESS
RUN_COMPLETED
RUN_FAILED
RUN_CANCELLED
```

---

## 20.2. SSE Event Example

```text
event: TESTCASE_COMPLETED
data: {
  "testRunId": "run_123",
  "datasetRowId": "row_001",
  "targetId": "internal_bot",
  "passed": true,
  "score": 0.86,
  "completed": 1,
  "total": 100,
  "latencyMs": 1200
}
```

---

## 20.3. Backend SSE Flow

```text
1. Backend consumes vfqc:runs:events.
2. Backend persists event/result vào DB.
3. Backend publishes internal application event.
4. SSE controller pushes event tới clients đang subscribe run đó.
```

Nếu frontend reconnect, UI gọi snapshot API rồi nghe tiếp SSE:

```text
GET /api/test-runs/{testRunId}
GET /api/test-runs/{testRunId}/results
```

---

## 20.4. SSE Replay / Last-Event-ID

v0.5 bổ sung `runner_events.sequence_no BIGINT` để hỗ trợ replay.

Rule:

```text
1. Backend set SSE id = runner_events.sequence_no hoặc stream_message_id.
2. Frontend lưu last received event id.
3. Khi SSE reconnect, frontend gửi Last-Event-ID.
4. Backend có thể replay events sau sequence_no từ DB nếu còn trong retention.
5. Nếu không replay được, frontend fallback bằng snapshot API:
   - GET /api/test-runs/{testRunId}
   - GET /api/test-runs/{testRunId}/results
```

MVP có thể chỉ implement snapshot fallback trước, nhưng schema field `sequence_no` nên có từ đầu vì chi phí thêm rẻ hơn sửa sau.


---

# 21. Retry, Timeout & Idempotency

## 21.1. Retry Policy

MVP retry system error tối đa 3 lần.

```text
maxAttempts = 3
backoff = exponential
```

Retry khi:

```text
- Runner crash.
- Redis temporary issue.
- Backend temporary unavailable.
- Artifact write temporary failure.
- Provider/network transient error ở cấp system.
- Event persist fail tạm thời.
```

Không retry khi:

```text
- Chatbot trả lời sai.
- LLM Judge chấm fail.
- Rule-based evaluator fail.
- Testcase fail do business logic.
```

Testcase fail là kết quả hợp lệ, không phải system error.

---

## 21.2. Timeout Policy

v0.5 chốt timeout MVP:

```text
targetRequestTimeoutSeconds = 60
llmJudgeTimeoutSeconds = 120
testcaseTimeoutSeconds = 180
runTimeoutHours = 6
```

Rule:

```text
1. Nếu target request timeout:
   - testcase result lưu error_message.
   - testcase được tính là failed/error tùy dashboard metric.
   - run vẫn tiếp tục testcase tiếp theo nếu chưa vượt ngưỡng system failure.

2. Nếu LLM Judge timeout:
   - testcase result lưu judge error.
   - testcase có thể marked failed/error.
   - run vẫn tiếp tục.

3. Nếu toàn run vượt runTimeoutHours:
   - backend/runner mark run FAILED system-level.
```

---

## 21.3. Idempotency

Idempotency key:

```text
commandId + testRunId
```

Rule:

```text
1. Một testRunId chỉ được start một lần.
2. Duplicate START_RUN command không tạo run mới.
3. Runner nhận duplicate command phải check test_run status trước.
4. Backend không cho start lại run đã COMPLETED/FAILED/CANCELLED.
5. Muốn chạy lại thì tạo test_run mới.
6. Backend consume event idempotent theo event_id unique.
7. Testcase result idempotent theo unique(test_run_id, test_run_target_id, dataset_row_id).
```

Runner behavior:

```text
If test_run.status in [RUNNING, COMPLETED, FAILED, CANCELLED]:
  skip duplicate START_RUN
```

---

# 22. Scale & Limits MVP

## 22.1. Runner Concurrency

```text
1 runner instance = 1 active test run
```

Scale ngang bằng cách chạy nhiều runner instances.

---

## 22.2. Dataset Rows Limit

v0.5 chốt có limit MVP.

Default:

```text
maxRowsPerRun = 5_000
```

Lý do:

```text
1. Tránh run quá lớn làm nghẽn runner.
2. Dễ estimate chi phí LLM Judge.
3. Dễ giữ SSE/result persistence ổn định.
4. Có thể tăng bằng config sau khi benchmark.
```

Nếu dataset version vượt limit:

```text
1. UI hiển thị warning.
2. Backend reject run hoặc yêu cầu user chọn subset/sampling trong phase sau.
3. MVP chưa hỗ trợ partial subset UI chính thức.
```

---

## 22.3. Queue Priority

MVP chưa hỗ trợ queue priority.

Tất cả START_RUN commands dùng chung stream và xử lý FIFO tương đối theo Redis Stream order.

---

## 22.4. Rate Limit

MVP chưa làm rate limit phức tạp theo workspace/provider.

MVP chỉ có:

```text
- Timeout per request.
- 1 run per runner instance.
- Optional config max concurrent runners at deployment level.
```

Rate limit theo provider/workspace để MVP+.

---

## 22.5. Run Preflight

Trước khi tạo START_RUN command, backend phải tính preflight summary.

Preflight fields:

```text
row_count
target_count
evaluator_count
llm_judge_evaluator_count
estimated_target_calls
estimated_judge_calls
estimated_max_duration
estimated_cost_range
warnings
```

Công thức MVP:

```text
estimated_target_calls = row_count * target_count
estimated_judge_calls = row_count * target_count * llm_judge_evaluator_count
estimated_max_duration = conservative estimate theo timeout config
```

Warning rule:

```text
1. warning nếu row_count > maxRowsPerRun.
2. warning nếu estimated_judge_calls quá lớn.
3. warning nếu estimated_cost_range vượt workspace/user budget config nếu có.
4. warning nếu comparison run có quá nhiều targets.
5. warning nếu judge provider/model chưa cấu hình cost/token.
```

MVP action:

```text
1. UI hiển thị preflight summary trước khi chạy.
2. Backend reject nếu row_count > maxRowsPerRun.
3. Backend vẫn cho chạy nếu chỉ là cost warning nhưng user có quyền xác nhận.
```


---

# 23. Artifact Storage

## 23.1. Storage Strategy

MVP dùng storage abstraction.

MVP implementation lưu local.

Path:

```text
./storage/artifacts/test-runs/{testRunId}/
```

Cấu trúc:

```text
./storage/artifacts/test-runs/{testRunId}/
  ├── metadata.json
  ├── promptfooconfig.json
  ├── results.json
  ├── results.jsonl
  ├── report.html
  └── runner.log
```

Sau này có thể đổi sang:

```text
- MinIO
- S3
- GCS
- Azure Blob Storage
```

mà không ảnh hưởng domain model.

---

## 23.2. Artifact Metadata

Table:

```text
promptfoo_run_artifacts
- id
- test_run_id
- config_file_path
- result_json_path
- result_jsonl_path
- html_report_path
- logs_path
- config_sha256
- result_sha256
- artifact_schema_version
- sanitized
- created_at
```

File `metadata.json` nên chứa:

```json
{
  "testRunId": "run_123",
  "promptfooVersion": "0.121.17",
  "promptfooAdapterSchemaVersion": 1,
  "runnerId": "runner_01",
  "createdAt": "2026-07-02T10:00:00+07:00",
  "domainConfigHash": "sha256:...",
  "promptfooConfigHash": "sha256:...",
  "configSha256": "sha256:...",
  "resultSha256": "sha256:...",
  "artifactSchemaVersion": 1,
  "sanitized": true,
  "promptfooConfigPath": "promptfooconfig.json",
  "resultJsonPath": "results.json",
  "resultJsonlPath": "results.jsonl",
  "htmlReportPath": "report.html",
  "logsPath": "runner.log"
}
```

Rule:

```text
1. artifact_schema_version tăng khi format file thay đổi.
2. sanitized phải true trước khi expose artifact cho UI/user.
3. config_sha256/result_sha256 dùng để audit artifact integrity.
```

---

## 23.3. Artifact Types

### Promptfoo Config Snapshot

Generated config runner dùng để chạy Promptfoo.

### Result JSON

Full structured result.

### Result JSONL

Line-based result, phù hợp cho ingest hoặc dataset lớn.

### HTML Report

Report view/debug artifact.

### Runner Log

Log của runner trong quá trình chạy.

---

## 23.4. Retention Policy MVP

MVP local dev/staging:

```text
artifactRetentionDays = 30
rawRequestResponseRetentionDays = 30
```

Production có thể đổi bằng config.

DB vẫn lưu summary/result normalized cần thiết cho dashboard; artifact lớn có thể expire theo policy.

---

# 24. Sensitive Data Masking

## 24.1. Quyết định

Hệ thống lưu request/response/config/artifact để debug, nhưng phải mask sensitive data trước khi persist hoặc expose.

Mask ở nhiều lớp:

```text
1. Frontend cURL parser phát hiện secret candidates.
2. Backend-side masking trước khi persist DB.
3. Runner-side masking trước khi publish Redis event.
4. Runner-side masking trước khi write artifact.
5. Optional final scanner kiểm tra artifact trước khi expose UI.
```

Bắt buộc:

```text
1. Không lưu raw_curl nguyên bản nếu có Authorization/API key.
2. raw_curl lưu bản sanitized.
3. Secret thật được tách thành Secret entity.
4. promptfoo_config_snapshot_json trong DB là sanitized snapshot.
5. Full artifact cũng phải đi qua VfqcSensitiveDataMasker trước khi ghi file.
6. Lưu config_hash/result_hash để audit.
```

---

## 24.2. Fields cần mask mặc định

```text
Authorization
Cookie
Set-Cookie
X-API-Key
api_key
apikey
access_token
refresh_token
id_token
client_secret
password
secret
token
bearer
credential
x-auth-token
proxy-authorization
```

---

## 24.3. Mask Format

Ví dụ:

```json
{
  "Authorization": "Bearer ****MASKED****",
  "api_key": "****MASKED****"
}
```

Không nên xóa field hoàn toàn vì QC vẫn cần biết request có header/token hay không.

---

## 24.4. Hash & Audit

Hash rule:

```text
1. Canonicalize JSON trước khi hash.
2. Hash dùng SHA-256.
3. domain_config_hash tính từ sanitized canonical domain snapshot.
4. promptfoo_config_hash tính từ sanitized canonical promptfoo config snapshot.
5. config_sha256/result_sha256 tính từ bytes cuối cùng đã sanitize trên disk.
```

Mục tiêu:

```text
1. Audit được artifact có bị đổi sau khi run không.
2. So sánh được config giữa các run.
3. Không cần lưu secret/plain raw cURL để trace.
```

---

# 25. Result Persistence

## 25.1. Test Run Result

Mỗi run lưu summary machine metrics và final reviewed metrics nếu có QC review.

```text
total_testcases
completed_testcases
machine_passed_count
machine_failed_count
machine_error_count
machine_pass_rate
machine_average_score
final_passed_count
final_failed_count
final_error_count
final_pass_rate
final_average_score
average_latency_ms
reviewed_count
unreviewed_count
needs_discussion_count
needs_rerun_count
status
started_at
finished_at
```

Rule:

```text
1. Machine metrics lấy từ test_case_results.
2. Final metrics lấy từ QC review nếu có, fallback về machine result nếu chưa review.
3. Dashboard nên hiển thị được cả machine metrics và final reviewed metrics.
4. Export/report mặc định dùng final metrics vì QC là người chịu trách nhiệm báo cáo.
```

---

## 25.2. Test Case Result

`test_case_results` lưu machine/original result do Promptfoo, rule-based evaluator và LLM Judge tạo ra.

Mỗi testcase result lưu:

```text
id
test_run_id
test_run_target_id
dataset_row_id
status
input_vars_json
rendered_prompt
resolved_request_json
raw_response_json
extracted_output_json
grading_result_json
component_results_json
score
passed
latency_ms
token_usage_json
cost
error_message
started_at
finished_at
attempt_count
created_at
```

Status MVP:

```text
PASSED
FAILED
ERROR
JUDGE_ERROR
TIMEOUT
SKIPPED
CANCELLED
```

Rule:

```text
1. PASSED/FAILED = machine business evaluation result.
2. ERROR/JUDGE_ERROR/TIMEOUT = system/execution/evaluator error.
3. CANCELLED/SKIPPED = run-control result.
4. Machine result immutable sau khi persisted, trừ trường hợp idempotent retry cùng event/result.
5. QC không sửa trực tiếp test_case_results.
```

Với comparison run:

```text
Một dataset_row_id có nhiều test_case_results,
mỗi result ứng với một target.
```

---

## 25.3. QC Review / Manual Override

Auto evaluation chỉ là bước chấm ban đầu. QC mới là người quyết định kết quả cuối cùng để báo cáo/nộp.

Nguyên tắc:

```text
Machine result is advisory.
QC reviewed result is authoritative for reporting/export.
```

Do đó hệ thống phải có review layer riêng:

```text
test_case_results             # machine/original result, immutable
test_case_result_reviews      # QC final judgment, note, override
```

Mỗi review lưu:

```text
id
test_case_result_id
review_status
override_status
override_passed
override_score
note
reviewed_by
reviewed_at
created_at
updated_at
```

Review status MVP:

```text
UNREVIEWED
REVIEWED
NEEDS_DISCUSSION
NEEDS_RERUN
```

Final result rule:

```text
Nếu test_case_result_reviews có override:
  final_status = override_status
  final_passed = override_passed
  final_score = override_score

Nếu chưa có override:
  final_status = test_case_results.status
  final_passed = test_case_results.passed
  final_score = test_case_results.score
```

Ví dụ nghiệp vụ:

```text
Machine result: FAILED
Judge reason: Câu trả lời thiếu thông tin giá bán.
QC review: PASSED
QC note: Testcase chỉ yêu cầu tên phiên bản, không yêu cầu giá bán. Judge chấm sai tiêu chí.
Final result: PASSED
```

Bulk review:

```text
1. MVP hỗ trợ bulk review cho selected testcase results.
2. Không hỗ trợ bulk review bằng unbounded filter trong MVP để tránh thao tác nhầm diện rộng.
3. Bulk review vẫn phải lưu reviewed_by/reviewed_at và note cho từng result.
```

---

## 25.4. Export Job

Export là first-class workflow vì QC vẫn cần Excel để báo cáo/nộp.

MVP hỗ trợ:

```text
1. Export DatasetVersion ra Excel.
2. Export TestRun results ra Excel.
```

Export result mode:

```text
MACHINE  # chỉ machine/original result
FINAL    # final result sau QC review, fallback machine nếu chưa review
BOTH     # gồm cả machine result và QC review/final result
```

Default:

```text
TestRun result export default resultMode = FINAL.
```

ExportJob lưu:

```text
id
job_type
format
status
workspace_id
project_id
test_run_id
dataset_version_id
request_json
file_path
file_name
content_type
file_size_bytes
error_message
created_by
created_at
started_at
finished_at
expires_at
```

Rule:

```text
1. Export file không chứa secret/plain raw request nếu include request/response.
2. Export request/response phải dùng sanitized data.
3. Không expose server-local path trực tiếp cho frontend.
4. Export file có retention/expiry policy.
5. Export dùng cùng filter/sort/table semantics với UI nếu có thể.
```

---

# 26. Dashboard & Review Workspace MVP

## 26.1. Single Target Dashboard

Hiển thị:

```text
- Total test cases
- Completed test cases
- Machine passed/failed/error
- Machine pass rate
- Final passed/failed/error
- Final pass rate
- Reviewed / unreviewed count
- Needs discussion / needs rerun count
- Average machine score
- Average final score
- Average latency
- Failed cases by category
- Score distribution
- Result table
```

Result table cần hỗ trợ table-heavy QC workflow:

```text
- Row index
- Question
- Expected
- Actual answer
- Machine verdict
- Machine score
- Machine/Judge reason
- QC final verdict
- QC final score
- QC note
- Review status
- Reviewed by
- Reviewed at
- Rule results
- Latency
- Error
```

UI cần có:

```text
1. Table view giống Excel cho scan/filter/sort nhanh.
2. Card/library view cho testcase dài.
3. Detail drawer để xem sâu mà không rời table.
4. Inline review controls: final pass/fail/score/note.
5. Bulk review selected rows.
6. Export Excel.
```

---

## 26.2. Testcase Detail

Mỗi testcase detail cần có:

```text
- Dataset row
- Rendered prompt
- Resolved request
- Raw response
- Extracted output
- Rule assertion results
- LLM judge result
- Machine verdict/reason
- QC final verdict/note
- Error logs
- Artifact references
```

---

## 26.3. Comparison Dashboard

Hiển thị:

```text
- Target list
- Machine pass rate theo target
- Final pass rate theo target
- Average machine/final score theo target
- Average latency theo target
- Error count theo target
- Token/cost nếu có
- Testcase matrix theo target
- Judge reason từng target
- QC review/final verdict từng target nếu có
```

---

# 27. Main User Flows

## 27.1. Admin Setup Workspace

```text
1. Admin tạo Team Workspace.
2. Admin invite QC members.
3. Admin tạo Workspace Secret.
4. Admin tạo Workspace Provider.
5. QC members dùng provider có sẵn để chạy LLM Judge/baseline.
```

---

## 27.2. QC Tạo Project Test

```text
1. QC tạo project.
2. Hệ thống tạo dataset schema mặc định question/expected.
3. QC paste cURL chatbot target.
4. Frontend parse cURL.
5. QC chỉnh request config nếu cần.
6. QC map $dataset.question vào body/query/header.
7. QC test connection.
8. QC chọn answer path từ response example.
9. Backend lưu chatbot target + example history.
```

---

## 27.3. QC Import Dataset từ Project khác

```text
1. QC mở Project Detail.
2. Vào Datasets tab.
3. Bấm Import Dataset.
4. Chọn From another project.
5. Search project nguồn.
6. Chọn dataset + dataset version nguồn.
7. Preview rows.
8. Mapping schema nếu cần.
9. Confirm clone.
10. Backend tạo dataset + dataset_version + dataset_rows mới.
```

---

## 27.4. QC Edit Dataset kiểu Excel

```text
1. QC mở Dataset Detail.
2. UI hiển thị Table View hoặc Card/Library View.
3. QC bấm Edit trên ACTIVE version hiện tại.
4. Backend tạo hoặc reuse DatasetVersion DRAFT từ ACTIVE version.
5. QC click trực tiếp vào cell để sửa question/expected/category/context.
6. QC thêm/xóa row nếu cần.
7. QC paste nhiều dòng từ Excel vào table nếu cần.
8. Backend lưu edit vào DRAFT rows/cells.
9. QC bấm Publish.
10. Backend validate schema/required columns.
11. Backend publish DRAFT thành ACTIVE version mới và update datasets.current_version_id.
12. Version cũ vẫn immutable để run cũ không đổi nghĩa.
```

---

## 27.5. QC Chạy Single/Comparison Run

```text
1. QC chọn dataset version ACTIVE.
2. QC chọn run type SINGLE hoặc COMPARISON.
3. Nếu SINGLE, QC chọn đúng 1 target.
4. Nếu COMPARISON, QC chọn ít nhất 2 comparison targets:
   - chatbot target A
   - chatbot target B
   - LLM baseline nếu cần
5. QC chọn evaluator versions.
6. Backend chạy preflight.
7. Backend validate target count/composition và estimate calls/cost.
8. Backend lock config.
9. Backend tạo test_run SINGLE hoặc COMPARISON.
10. Backend tạo test_run_targets theo đúng thứ tự user chọn.
11. Backend publish START_RUN.
12. Runner chạy từng testcase cho từng target.
13. Backend persist machine results.
14. UI hiển thị realtime progress qua SSE và comparison matrix.
```

---

## 27.6. QC Review Result và sửa final verdict

```text
1. Run hoàn tất.
2. QC mở Review Workspace.
3. UI hiển thị result theo table/card/matrix.
4. QC filter các case machine FAILED, JUDGE_ERROR, low score hoặc unreviewed.
5. QC mở detail drawer để xem question, expected, actual answer, judge reason, rule result.
6. Nếu máy chấm đúng, QC có thể giữ nguyên.
7. Nếu LLM Judge/rule chấm sai, QC override final verdict PASS/FAIL/score.
8. QC thêm note giải thích lý do.
9. Backend lưu vào test_case_result_reviews.
10. Dashboard tính final metrics từ QC review, fallback machine result nếu chưa review.
```

---

## 27.7. QC Export Excel để báo cáo

```text
1. QC review xong result.
2. QC bấm Export Excel.
3. Backend tạo ExportJob type TEST_RUN_RESULTS.
4. Export mặc định dùng resultMode FINAL.
5. Excel gồm machine result, QC final result, QC note, reviewedBy/reviewedAt nếu user chọn.
6. QC tải file và nộp/báo cáo theo workflow quen thuộc.
```

Dataset export flow:

```text
1. QC mở DatasetVersion.
2. QC bấm Export Excel.
3. Backend tạo ExportJob type DATASET_VERSION.
4. QC tải dataset file theo schema column order.
```

---

## 27.8. QC Cancel Run

```text
1. QC bấm Cancel.
2. Frontend gọi HTTP POST cancel.
3. Backend set CANCELLING.
4. Backend publish CANCEL_RUN.
5. Runner check cancellation flag.
6. Runner dừng an toàn.
7. Runner publish RUN_CANCELLED.
8. UI nhận SSE RUN_CANCELLED.
```

---

# 28. MVP Scope v0.6

## 28.1. In Scope

```text
- Personal Workspace
- Team Workspace
- System role PLATFORM_ADMIN/USER
- Workspace role OWNER/ADMIN/MEMBER/VIEWER
- Workspace-level permission
- Personal Secret
- Workspace Secret
- Personal Provider
- Workspace Provider
- Provider model selection
- Project management
- Dataset schema question/expected mặc định
- Schema versioning
- Dataset versioning
- Dataset editable workspace table/card/library view
- Dataset DRAFT edit + publish workflow
- Inline cell edit trên DRAFT DatasetVersion
- Bulk paste/upsert từ Excel vào DRAFT DatasetVersion
- Excel dataset import
- Dataset reuse từ project khác
- Dataset reuse từ workspace/personal dataset
- cURL chatbot target config
- Frontend cURL parsing
- Backend lưu rawCurl + parsedRequestConfig
- Test connection / save example response history
- Response field selection
- Rule-based evaluator
- LLM Judge evaluator
- Evaluator versioning
- Promptfoo Node API integration
- Promptfoo version pin
- PromptfooAdapterContract regression suite
- Promptfoo cache disabled by default
- Redis Stream command queue
- Redis Stream event queue
- Redis pending recovery
- Redis DLQ streams
- Redis stream trimming/retention
- Redis safe trimming rule for pending messages
- Runner service
- Runner heartbeat
- Single target run
- Multi-target comparison run
- SSE realtime progress từng testcase
- Cancel run cơ bản
- Timeout per testcase/judge/run
- Retry system error tối đa 3 lần
- Idempotency theo commandId + testRunId + eventId
- Local artifact storage abstraction
- Raw request/response storage với sensitive data masking
- Sanitized raw cURL storage only
- Artifact config/result SHA-256 audit hash
- Run preflight summary
- SSE sequence id / replay-ready schema
- Single target dashboard
- Comparison dashboard
- QC review/manual override result
- Bulk review selected testcase results
- Final result semantics for dashboard/export
- TestRun result export Excel
- Dataset export Excel
- ExportJob async workflow
```

---

## 28.2. Out of Scope

```text
- Project-level permission
- Custom permission theo từng action
- User-provided custom_javascript evaluator trong MVP UI
- Live reference dataset
- Auto-sync dataset từ source project
- Clone selected rows
- Auto fallback secret phức tạp
- Secret rotation workflow nâng cao
- AI generate dataset
- Scheduled regression testing
- CI/CD integration
- Cost tracking chi tiết nâng cao
- Full audit log enterprise
- Comment/collaboration trên testcase
- Approval workflow
- Red teaming module
- Prompt optimization module
- WebSocket realtime control
- Promptfoo CLI fallback
- Queue priority
- Pause/resume run
- Partial rerun failed testcases
- Distributed lock nâng cao
- Kubernetes job orchestration
- Multi-turn chatbot test
- PDF export/report template nâng cao
```

---

# 29. Domain Entities v0.6

```text
User
AuthRefreshToken
Workspace
WorkspaceMember

Secret
LLMProvider

Project

DatasetSchema
DatasetSchemaColumn
Dataset
DatasetVersion
DatasetRow
DatasetImportJob

ChatbotTarget
ChatbotTargetExample

Evaluator
EvaluatorVersion
EvaluatorRule

TestRun
TestRunTarget
TestRunEvaluatorVersion
TestCaseResult
TestCaseResultReview

ExportJob

RunnerCommand
RunnerEvent
RunnerInstance

PromptfooRunArtifact
```

---

# 30. Database Tables v0.6

## users

```text
id UUID PK
email TEXT NOT NULL
name TEXT
avatar_url TEXT
system_role system_role NOT NULL DEFAULT 'USER'
status user_status NOT NULL DEFAULT 'ACTIVE'
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
deleted_at TIMESTAMPTZ
```

## auth_refresh_tokens

```text
id UUID PK
user_id UUID FK users(id)
token_hash TEXT NOT NULL
family_id UUID NOT NULL
status auth_refresh_token_status NOT NULL
issued_at TIMESTAMPTZ NOT NULL
expires_at TIMESTAMPTZ NOT NULL
rotated_at TIMESTAMPTZ
revoked_at TIMESTAMPTZ
created_ip TEXT
user_agent TEXT
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Rule:

```text
- Refresh token plaintext không lưu DB.
- token_hash dùng để verify/revoke/rotate refresh token.
- Cần table này nếu triển khai refresh token rotation/reuse detection/logout revoke.
```

## workspaces

```text
id UUID PK
name TEXT NOT NULL
type workspace_type NOT NULL
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
deleted_at TIMESTAMPTZ
```

## workspace_members

```text
id BIGINT PK
workspace_id UUID FK workspaces(id)
user_id UUID FK users(id)
role workspace_role NOT NULL
status workspace_member_status NOT NULL
invited_by UUID FK users(id)
joined_at TIMESTAMPTZ
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

## secrets

```text
id UUID PK
scope secret_scope NOT NULL
owner_user_id UUID FK users(id)
workspace_id UUID FK workspaces(id)
name TEXT NOT NULL
encrypted_value TEXT NOT NULL
status secret_status NOT NULL
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
deleted_at TIMESTAMPTZ
```

## llm_providers

```text
id UUID PK
scope provider_scope NOT NULL
owner_user_id UUID FK users(id)
workspace_id UUID FK workspaces(id)
name TEXT NOT NULL
provider_type provider_type NOT NULL
secret_id UUID FK secrets(id)
base_url TEXT
default_model TEXT
available_models_json JSONB
config_json JSONB
status provider_status NOT NULL
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
deleted_at TIMESTAMPTZ
```

## projects

```text
id UUID PK
workspace_id UUID FK workspaces(id)
name TEXT NOT NULL
description TEXT
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
archived_at TIMESTAMPTZ
deleted_at TIMESTAMPTZ
```

## dataset_schemas

```text
id UUID PK
scope dataset_schema_scope NOT NULL
project_id UUID FK projects(id)
workspace_id UUID FK workspaces(id)
owner_user_id UUID FK users(id)
version INT NOT NULL
name TEXT NOT NULL
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
```

## dataset_schema_columns

```text
id BIGINT PK
schema_id UUID FK dataset_schemas(id)
name TEXT NOT NULL
display_name TEXT
data_type TEXT NOT NULL
required BOOLEAN NOT NULL DEFAULT false
description TEXT
order_index INT NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

## datasets

```text
id UUID PK
scope dataset_scope NOT NULL
workspace_id UUID FK workspaces(id)
project_id UUID FK projects(id)
owner_user_id UUID FK users(id)
current_version_id UUID FK dataset_versions(id)
name TEXT NOT NULL
description TEXT
source_dataset_id UUID FK datasets(id)
source_project_id UUID FK projects(id)
source_workspace_id UUID FK workspaces(id)
source_type dataset_source_type
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
deleted_at TIMESTAMPTZ
```

## dataset_versions

```text
id UUID PK
dataset_id UUID FK datasets(id)
version INT NOT NULL
schema_id UUID FK dataset_schemas(id)
schema_version INT NOT NULL
source_dataset_version_id UUID FK dataset_versions(id)
base_version_id UUID FK dataset_versions(id)
import_job_id UUID FK dataset_import_jobs(id)
import_mapping_json JSONB
row_count INT NOT NULL DEFAULT 0
status dataset_version_status NOT NULL DEFAULT 'DRAFT'
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
published_at TIMESTAMPTZ
discarded_at TIMESTAMPTZ
```

## dataset_rows

```text
id BIGINT PK
dataset_version_id UUID FK dataset_versions(id)
row_index INT NOT NULL
data_json JSONB NOT NULL
metadata_json JSONB
revision INT NOT NULL DEFAULT 1
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

## dataset_import_jobs

```text
id UUID PK
target_project_id UUID FK projects(id)
target_dataset_id UUID FK datasets(id)
source_type dataset_import_source_type NOT NULL
source_file_id TEXT
source_dataset_id UUID FK datasets(id)
source_dataset_version_id UUID FK dataset_versions(id)
status dataset_import_status NOT NULL
mapping_json JSONB
preview_json JSONB
error_message TEXT
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
finished_at TIMESTAMPTZ
```

## chatbot_targets

```text
id UUID PK
project_id UUID FK projects(id)
name TEXT NOT NULL
description TEXT
raw_curl_sanitized TEXT
raw_curl_hash TEXT
method TEXT NOT NULL
url TEXT NOT NULL
headers_json JSONB
query_params_json JSONB
body_json JSONB
auth_config_json JSONB
dataset_mapping_json JSONB
latest_example_id BIGINT FK chatbot_target_examples(id)
response_extraction_config_json JSONB
status chatbot_target_status NOT NULL
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
deleted_at TIMESTAMPTZ
```

## chatbot_target_examples

```text
id BIGINT PK
chatbot_target_id UUID FK chatbot_targets(id)
request_json JSONB
response_status_code INT
response_headers_json JSONB
response_body_json JSONB
extracted_output_json JSONB
latency_ms INT
error_message TEXT
tested_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
```

## evaluators

```text
id UUID PK
project_id UUID FK projects(id)
name TEXT NOT NULL
type evaluator_type NOT NULL
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
deleted_at TIMESTAMPTZ
```

## evaluator_versions

```text
id UUID PK
evaluator_id UUID FK evaluators(id)
version INT NOT NULL
config_json JSONB
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
```

## evaluator_rules

```text
id BIGINT PK
evaluator_version_id UUID FK evaluator_versions(id)
field_source TEXT NOT NULL
operator TEXT NOT NULL
expected_value_json JSONB
weight NUMERIC(8,4)
required BOOLEAN NOT NULL DEFAULT false
order_index INT NOT NULL
config_json JSONB
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

## test_runs

```text
id UUID PK
project_id UUID FK projects(id)
run_type test_run_type NOT NULL
dataset_id UUID FK datasets(id)
dataset_version_id UUID FK dataset_versions(id)
dataset_schema_id UUID FK dataset_schemas(id)
dataset_schema_version INT NOT NULL
judge_provider_id UUID FK llm_providers(id)
judge_model TEXT
status test_run_status NOT NULL
progress_completed INT NOT NULL DEFAULT 0
progress_total INT NOT NULL DEFAULT 0
summary_metrics_json JSONB
domain_config_snapshot_json JSONB
promptfoo_config_snapshot_json JSONB  # sanitized compact only
promptfoo_config_hash TEXT
domain_config_hash TEXT
started_by UUID FK users(id)
started_at TIMESTAMPTZ
finished_at TIMESTAMPTZ
cancel_requested_at TIMESTAMPTZ
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

## test_run_targets

```text
id UUID PK
test_run_id UUID FK test_runs(id)
target_type test_run_target_type NOT NULL
chatbot_target_id UUID FK chatbot_targets(id)
llm_provider_id UUID FK llm_providers(id)
model TEXT
display_name TEXT NOT NULL
provider_label TEXT
config_json JSONB
created_at TIMESTAMPTZ NOT NULL
```

## test_run_evaluator_versions

```text
id BIGINT PK
test_run_id UUID FK test_runs(id)
evaluator_version_id UUID FK evaluator_versions(id)
evaluator_type evaluator_type NOT NULL
order_index INT NOT NULL
created_at TIMESTAMPTZ NOT NULL
```

## test_case_results

```text
id BIGINT PK
test_run_id UUID FK test_runs(id)
test_run_target_id UUID FK test_run_targets(id)
dataset_row_id BIGINT FK dataset_rows(id)
status test_case_result_status NOT NULL
input_vars_json JSONB
rendered_prompt TEXT
resolved_request_json JSONB
raw_response_json JSONB
extracted_output_json JSONB
grading_result_json JSONB
component_results_json JSONB
score NUMERIC(8,4)
passed BOOLEAN
latency_ms INT
token_usage_json JSONB
cost NUMERIC(18,8)
error_message TEXT
started_at TIMESTAMPTZ
finished_at TIMESTAMPTZ
attempt_count INT NOT NULL DEFAULT 0
created_at TIMESTAMPTZ NOT NULL
```

## test_case_result_reviews

```text
id BIGINT PK
test_case_result_id BIGINT FK test_case_results(id)
review_status review_status NOT NULL DEFAULT 'UNREVIEWED'
override_status test_case_result_status
override_passed BOOLEAN
override_score NUMERIC(8,4)
note TEXT
reviewed_by UUID FK users(id)
reviewed_at TIMESTAMPTZ
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

Rule:

```text
- unique(test_case_result_id)
- Không sửa trực tiếp machine result trong test_case_results.
- Review record là final QC judgment để dashboard/export dùng.
```

## runner_commands

```text
id BIGINT PK
command_id UUID NOT NULL
test_run_id UUID FK test_runs(id)
command_type runner_command_type NOT NULL
stream_message_id TEXT
status runner_command_status NOT NULL
attempt_count INT NOT NULL DEFAULT 0
last_error TEXT
consumer_name TEXT
created_at TIMESTAMPTZ NOT NULL
published_at TIMESTAMPTZ
consumed_at TIMESTAMPTZ
acknowledged_at TIMESTAMPTZ
failed_at TIMESTAMPTZ
dlq_at TIMESTAMPTZ
```

## runner_events

```text
id BIGINT PK
event_id UUID NOT NULL
test_run_id UUID FK test_runs(id)
event_type TEXT NOT NULL
sequence_no BIGINT NOT NULL
stream_message_id TEXT
status runner_event_status NOT NULL
payload_json JSONB
attempt_count INT NOT NULL DEFAULT 0
last_error TEXT
consumer_name TEXT
created_at TIMESTAMPTZ NOT NULL
processed_at TIMESTAMPTZ
acknowledged_at TIMESTAMPTZ
dlq_at TIMESTAMPTZ
```

## runner_instances

```text
id UUID PK
runner_id TEXT NOT NULL
status runner_instance_status NOT NULL
current_test_run_id UUID FK test_runs(id)
last_heartbeat_at TIMESTAMPTZ
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

## promptfoo_run_artifacts

```text
id BIGINT PK
test_run_id UUID FK test_runs(id)
config_file_path TEXT
result_json_path TEXT
result_jsonl_path TEXT
html_report_path TEXT
logs_path TEXT
config_sha256 TEXT
result_sha256 TEXT
artifact_schema_version INT NOT NULL DEFAULT 1
sanitized BOOLEAN NOT NULL DEFAULT true
created_at TIMESTAMPTZ NOT NULL
```


## export_jobs

```text
id UUID PK
workspace_id UUID FK workspaces(id)
project_id UUID FK projects(id)
job_type export_job_type NOT NULL
format export_format NOT NULL DEFAULT 'XLSX'
status export_job_status NOT NULL
source_test_run_id UUID FK test_runs(id)
source_dataset_version_id UUID FK dataset_versions(id)
request_json JSONB
file_path TEXT
file_name TEXT
content_type TEXT
file_size_bytes BIGINT
error_message TEXT
created_by UUID FK users(id)
created_at TIMESTAMPTZ NOT NULL
started_at TIMESTAMPTZ
finished_at TIMESTAMPTZ
expires_at TIMESTAMPTZ
```

Rule:

```text
- Export file không expose server-local path trực tiếp cho frontend.
- request_json lưu filter/columns/resultMode đã sanitize.
- Exported file phải đi qua sensitive data masking nếu có request/response fields.
```


---

# 31. Important Invariants v0.6

```text
1. PLATFORM_ADMIN có gần như mọi quyền platform, nhưng không được xem lại plain text secret.
2. Workspace role nằm ở workspace_members.role, không nằm ở users.
3. DatasetVersion ACTIVE không được update rows trực tiếp nếu đã có TestRun dùng.
4. EvaluatorVersion không được update nếu đã có TestRunEvaluatorVersion trỏ tới.
5. Khi tạo TestRun, backend phải lock dataset/schema/evaluator/provider/target/run config snapshots.
6. Trong comparison run, mọi target dùng chung dataset_version_id, schema version, evaluator versions, judge provider/model.
7. Mỗi test_run_target chỉ có tối đa một result cho một dataset_row.
8. Duplicate START_RUN không được tạo duplicate execution.
9. Runner event phải idempotent theo event_id.
10. Redis command/event không chứa secret plain text hoặc artifact lớn.
11. Raw request/response phải mask sensitive data trước khi lưu.
12. Redis stream chỉ là transport/recovery buffer; DB là source of truth lâu dài.
13. ACTIVE DatasetVersion không được mutate; edit phải đi qua DRAFT version.
14. Machine result trong test_case_results là immutable/advisory.
15. QC final result lưu riêng ở test_case_result_reviews và là authoritative cho report/export.
16. Export file không expose server-local path và phải respect permission/source visibility.
```

---

# 32. Product Decisions v0.6

```text
1. Promptfoo là execution engine chính.
2. Runner dùng Promptfoo Node API, không dùng CLI.
3. Promptfoo version phải được pin bằng package-lock.json.
4. Latest observed Promptfoo version tại PRD v0.5 là 0.121.17, nhưng production không dùng latest động.
5. PromptfooAdapterContract là boundary giữa domain config và Promptfoo internals.
6. Backend Java là source of truth.
7. Promptfoo config là generated artifact.
8. promptfoo_config_snapshot_json trong DB chỉ là sanitized compact snapshot.
9. domain_config_hash và promptfoo_config_hash được lưu để audit.
10. Queue MVP dùng Redis Streams.
11. Dùng 2 main streams: vfqc:runs:commands và vfqc:runs:events.
12. Dùng 2 DLQ streams: vfqc:runs:commands:dlq và vfqc:runs:events:dlq.
13. Consumer group command: vfqc-runners.
14. Consumer group event: vfqc-backend-events.
15. Backend publish command, Runner consume command.
16. Runner publish event, Backend consume event.
17. Có pending recovery bằng XPENDING/XAUTOCLAIM.
18. Có Redis safe trimming rule; không trim message pending chưa ACK.
19. Nếu Redis >= 8.2, ưu tiên XTRIM ... ACKED cho streams có consumer group.
20. Nếu Redis < 8.2, scheduled trimming phải check XPENDING trước.
21. Backend đẩy realtime progress xuống frontend qua SSE.
22. runner_events.sequence_no dùng làm SSE id/replay key.
23. Cancel dùng HTTP POST, không dùng WebSocket.
24. Cancel flow dùng hybrid: DB status CANCELLING + CANCEL_RUN command + runner check control API.
25. Mỗi runner instance xử lý 1 run tại một thời điểm.
26. Scale bằng nhiều runner instances.
27. Có runner heartbeat monitor.
28. Redis event chứa normalized result.
29. Artifact lưu local qua storage abstraction.
30. Artifact path: ./storage/artifacts/test-runs/{testRunId}/.
31. Artifact phải sanitize trước khi ghi file/expose UI.
32. Lưu raw request/response nhưng mask sensitive fields.
33. Không lưu raw_curl nguyên bản nếu có Authorization/API key/token/cookie.
34. raw_curl chỉ lưu sanitized version.
35. Mask sensitive fields ở cả runner và backend.
36. Promptfoo cache disabled by default cho internal chatbot target runs.
37. Cache chỉ bật trong controlled baseline/debug mode.
38. Retry system error tối đa 3 lần.
39. Không auto-retry testcase fail do evaluation.
40. Idempotency theo commandId + testRunId + eventId.
41. Dataset schema dùng scope-aware model: PROJECT / WORKSPACE / PERSONAL.
42. Dataset import từ project khác dùng wizard: search project → select dataset version → preview/mapping → clone.
43. MVP clone toàn bộ dataset rows.
44. MVP giới hạn maxRowsPerRun = 5_000.
45. MVP có Run Preflight trước khi chạy.
46. MVP có timeout per target request, judge call, testcase và toàn run.
47. Bỏ custom_javascript khỏi MVP UI.
48. LLM Judge Output Normalization là bắt buộc.
49. Dataset là Excel-compatible QC workspace, không chỉ là uploaded file.
50. Dataset edit dùng DRAFT version rồi publish thành ACTIVE version mới.
51. Machine evaluation result chỉ là advisory.
52. QC reviewed/final result là authoritative cho dashboard/report/export.
53. TestRun result export Excel default dùng FINAL result.
54. Queue priority, pause/resume, partial rerun để MVP+.
```

---

# 33. Open Questions cho sau v0.6

```text
1. Có cần object storage ngay từ staging/prod không, hay local shared volume đủ cho MVP?
2. Có cần encrypt artifact local không?
3. Có cần audit log chi tiết cho secret/provider/test run không?
4. Có cần rate limit theo provider/workspace ở MVP+ không?
5. Có cần queue priority cho run quan trọng ở MVP+ không?
6. Có cần pause/resume run không?
7. Có cần partial rerun failed testcases không?
8. Có cần export report nâng cao dạng PDF/custom template không?
9. Có cần multi-turn chatbot test trong MVP+ không?
10. Có cần sampling/subset UI trước khi chạy full dataset không?
11. Có cần multi-region runner sau này không?
12. Có cần support Kubernetes Job orchestration thay vì long-running runner service không?
13. Dataset DRAFT policy nên là một DRAFT global/dataset hay một DRAFT/user/dataset?
14. Export Excel template có cần customize theo từng team QC không?
```

---

# 34. MVP Success Criteria

MVP đạt yêu cầu nếu QC có thể:

```text
1. Tạo project trong workspace.
2. Dùng provider/key do Admin setup hoặc key cá nhân.
3. Paste cURL chatbot target.
4. Mapping question/context vào request.
5. Lưu example response và chọn answer path.
6. Import Excel dataset.
7. Import dataset từ project khác bằng wizard.
8. Xem dataset bằng table/card/library view.
9. Sửa dataset trên web bằng DRAFT version, inline cell edit và bulk paste từ Excel.
10. Publish DRAFT dataset thành ACTIVE version mới.
11. Export dataset ra Excel.
12. Cấu hình rule-based evaluator.
13. Cấu hình LLM Judge.
14. Chạy single target run.
15. Theo dõi từng testcase realtime qua SSE.
16. Cancel run khi cần.
17. Chạy comparison run giữa nhiều chatbot target và/hoặc LLM baseline.
18. Xem dashboard và testcase detail.
19. Debug được request/response/evaluator reason khi testcase fail.
20. QC review kết quả, override final pass/fail/score và thêm note.
21. Dashboard hiển thị được machine metrics và final reviewed metrics.
22. Bulk review được selected testcase results.
23. Export TestRun result ra Excel với resultMode FINAL/BOTH.
24. Xem artifact path/report của từng run.
```

MVP technical success criteria:

```text
1. Backend publish START_RUN vào Redis Stream.
2. Runner consume command bằng consumer group và chạy Promptfoo Node API thành công.
3. Runner publish TESTCASE_COMPLETED events.
4. Backend consume event và persist DB.
5. Frontend nhận realtime update qua SSE.
6. Cancel flow chuyển RUNNING → CANCELLING → CANCELLED.
7. System error retry tối đa 3 lần.
8. Pending Redis messages recover được bằng XAUTOCLAIM.
9. Message lỗi quá số lần retry được đưa vào DLQ.
10. Duplicate command không tạo duplicate run.
11. Duplicate event không tạo duplicate testcase result.
12. Raw request/response được mask trước khi lưu.
13. Artifact được lưu theo path chuẩn.
14. Redis Streams có retention/trimming policy.
15. Runner heartbeat detect được runner offline.
16. Promptfoo version được pin và lưu vào metadata/snapshot.
17. ACTIVE DatasetVersion không mutate; DRAFT edit/publish tạo version mới.
18. test_case_results giữ machine result immutable.
19. test_case_result_reviews lưu QC final judgment riêng.
20. ExportJob tạo và expose file qua download API, không expose server-local path.
```

