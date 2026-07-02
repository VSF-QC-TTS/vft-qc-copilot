# VinFast QC Copilot — PRD v0.3

## 1. Tổng quan sản phẩm

**VinFast QC Copilot** là platform hỗ trợ đội **QC/QA chuyên test chatbot hoặc AI nội bộ của công ty**.

Platform giúp QC/QA:

* Cấu hình chatbot nội bộ cần test bằng cURL.
* Import và quản lý dataset test.
* Tái sử dụng dataset từ project khác.
* Mapping dataset vào request chatbot.
* Cấu hình rule-based evaluator.
* Cấu hình LLM Judge.
* Chạy test tự động.
* Theo dõi tiến độ chạy realtime.
* Xem kết quả từng testcase.
* Xem dashboard tổng quan.
* So sánh chatbot nội bộ với các LLM/model baseline khác.

**Promptfoo là execution engine chính**. VinFast QC Copilot không tự build toàn bộ evaluation engine từ đầu. Backend lưu domain config, sau đó runner chuyển domain config thành Promptfoo-compatible config và chạy bằng Promptfoo Node API.

Promptfoo official docs hỗ trợ sử dụng như một Node package qua `evaluate(testSuite, options)`, trong đó `testSuite` là object tương đương `promptfooconfig.yaml`, và kết quả có thể lấy qua `toEvaluateSummary()`. Điều này phù hợp với runner service tự build config object rồi execute trực tiếp bằng code.

---

## 2. Mục tiêu MVP

MVP cần đủ để QC hoàn thành flow:

```text id="q5puvz"
1. Vào workspace.
2. Tạo project test chatbot.
3. Cấu hình secret/provider/model.
4. Paste cURL chatbot nội bộ.
5. Frontend parse cURL thành method, URL, headers, query params, body.
6. Mapping dataset fields vào request.
7. Import dataset từ Excel hoặc project khác.
8. Cấu hình evaluator.
9. Chạy test bằng Promptfoo Runner.
10. Theo dõi tiến độ realtime từng testcase.
11. Xem kết quả từng testcase.
12. Chạy comparison với baseline model.
13. Xem dashboard so sánh.
```

---

## 3. Primary User

MVP chỉ tập trung vào nhóm user chính:

```text id="dz3qgn"
QC / QA team chuyên test chatbot hoặc AI nội bộ của công ty
```

MVP chưa ưu tiên:

```text id="o33nz7"
- External customer
- Public SaaS user
- Business user không thuộc đội QC
- Developer platform user bên ngoài
```

---

# 4. Core Architecture

## 4.1. Kiến trúc tổng quan

```text id="d12mtg"
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
  ├── Dataset / Schema Management
  ├── Chatbot Target Config Storage
  ├── Evaluator Config Storage
  ├── Test Run Orchestration
  ├── Redis Stream Producer
  ├── Redis Stream Consumer
  ├── SSE Event Publisher
  ├── Result Persistence
  └── Artifact Metadata Storage

Redis Streams
  ├── vfqc:runs:commands
  └── vfqc:runs:events

Runner Node.js
  ├── Redis Stream Consumer
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

## 4.2. Source of Truth

Source of truth của hệ thống là **domain config trong backend database**, không phải Promptfoo config.

```text id="vmsla7"
Backend Domain Config
  ├── Workspace
  ├── Project
  ├── Dataset
  ├── Chatbot Target
  ├── Evaluator
  ├── Provider
  └── Test Run
```

Promptfoo config chỉ là **generated artifact** tại thời điểm chạy.

Mỗi test run cần lưu snapshot:

```text id="zg3e30"
- domain config snapshot
- generated promptfoo config snapshot
- promptfoo result artifact path
- normalized result trong database
```

Lý do:

```text id="s6gb74"
1. Dễ debug run cũ.
2. Đảm bảo result cũ không bị đổi nghĩa khi config mới thay đổi.
3. Có thể audit chính xác runner đã chạy gì.
4. Có thể đổi execution engine trong tương lai mà không phá domain model.
```

---

# 5. Workspace Design

## 5.1. Workspace Type

MVP hỗ trợ:

```text id="pp2zf6"
PERSONAL
TEAM
```

### Personal Workspace

Được tạo tự động khi user đăng ký.

```text id="g1nhwg"
User mới → Personal Workspace → user là Owner
```

### Team Workspace

Dùng cho team QC làm việc chung.

Team Workspace chứa:

```text id="bcrf8u"
- Members
- Projects
- Workspace Secrets
- Workspace Providers
- Workspace Datasets
- Shared test configs
```

---

## 5.2. Workspace Role

MVP dùng workspace-level permission.

```text id="jw0dxm"
OWNER
ADMIN
MEMBER
VIEWER
```

Chưa làm project-level permission trong MVP.

Default:

```text id="zaspt3"
Người tạo workspace = OWNER
Người được invite = MEMBER
```

---

## 5.3. Permission Matrix MVP

| Action                  | Owner | Admin | Member | Viewer |
| ----------------------- | ----: | ----: | -----: | -----: |
| Xem workspace           |    Có |    Có |     Có |     Có |
| Sửa workspace           |    Có |    Có |  Không |  Không |
| Xóa workspace           |    Có | Không |  Không |  Không |
| Mời member              |    Có |    Có |  Không |  Không |
| Đổi role member         |    Có |    Có |  Không |  Không |
| Tạo project             |    Có |    Có |     Có |  Không |
| Sửa project             |    Có |    Có |     Có |  Không |
| Xem project             |    Có |    Có |     Có |     Có |
| Import dataset          |    Có |    Có |     Có |  Không |
| Reuse dataset           |    Có |    Có |     Có |  Không |
| Cấu hình chatbot target |    Có |    Có |     Có |  Không |
| Cấu hình evaluator      |    Có |    Có |     Có |  Không |
| Chạy test               |    Có |    Có |     Có |  Không |
| Cancel test run         |    Có |    Có |     Có |  Không |
| Xem dashboard           |    Có |    Có |     Có |     Có |
| Tạo Workspace Secret    |    Có |    Có |  Không |  Không |
| Tạo Workspace Provider  |    Có |    Có |  Không |  Không |
| Tạo Personal Secret     |    Có |    Có |     Có |  Không |
| Tạo Personal Provider   |    Có |    Có |     Có |  Không |
| Xem plain text secret   | Không | Không |  Không |  Không |

---

# 6. Secret & Provider Design

## 6.1. Secret Scope

MVP hỗ trợ:

```text id="hztxdn"
PERSONAL
WORKSPACE
```

### Personal Secret

Do user tạo.

Dùng khi:

```text id="sktnlm"
- QC có API key riêng.
- Workspace key hết quota.
- Workspace key invalid.
- QC muốn fallback sang provider cá nhân.
```

### Workspace Secret

Do Owner/Admin tạo.

Dùng khi:

```text id="t1k3cj"
- Admin setup sẵn key cho team QC.
- Team dùng chung provider/model.
- QC không cần tự nhập API key.
```

Không role nào được xem lại plain text secret sau khi secret đã lưu.

---

## 6.2. Provider Scope

MVP hỗ trợ:

```text id="df1r44"
PERSONAL_PROVIDER
WORKSPACE_PROVIDER
```

Provider có thể dùng cho:

```text id="xyt7n4"
- LLM Judge
- Baseline model trong comparison run
- Dataset generation ở phase sau
```

Provider entity cần lưu:

```text id="tuo5sl"
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
```

Provider type MVP:

```text id="cyp9hz"
OPENAI_COMPATIBLE
GEMINI
CLAUDE
CUSTOM_HTTP
```

---

# 7. Project Design

Project là nơi QC cấu hình test cho một chatbot hoặc một nhóm chatbot liên quan.

Một project thuộc một workspace.

Project chứa:

```text id="ej5onx"
- Dataset Schema
- Datasets
- Chatbot Targets
- Evaluators
- Test Runs
- Comparison Runs
- Reports / Dashboard
```

Project entity:

```text id="av4kx7"
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

# 8. Dataset Schema Design

## 8.1. Default Schema

Khi tạo project, hệ thống tạo schema mặc định:

```text id="ljobij"
question
expected
```

User có thể:

```text id="evziar"
- Đổi tên cột
- Thêm cột
- Sửa cột
- Xóa cột
- Đổi mô tả
- Set required / optional
```

Ví dụ schema mở rộng:

```text id="fb8imx"
question
expected
context
category
difficulty
expected_intent
expected_sources
```

---

## 8.2. Schema Versioning

Schema cần có version.

Lý do:

```text id="rygenb"
1. Import Excel có thể thêm cột mới.
2. Import dataset từ project khác có thể khác schema.
3. Evaluator mapping phụ thuộc vào tên cột.
4. Test run cũ cần biết chính xác schema lúc chạy.
```

Test run phải lưu:

```text id="crz7tu"
dataset_id
dataset_version
schema_id
schema_version
```

---

# 9. Dataset Design

## 9.1. Dataset Scope

MVP hỗ trợ:

```text id="l2y8o9"
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

## 9.2. Mapping Dataset sang Promptfoo

Mỗi dataset row sẽ trở thành một Promptfoo test case.

Ví dụ dataset row:

```json id="t30px9"
{
  "question": "VF 8 có mấy phiên bản?",
  "expected": "VF 8 có các phiên bản Eco và Plus.",
  "context": "Thông tin sản phẩm VF 8",
  "category": "product_info",
  "difficulty": "easy"
}
```

Runner convert thành Promptfoo test case:

```yaml id="sn1i92"
tests:
  - vars:
      question: "VF 8 có mấy phiên bản?"
      expected: "VF 8 có các phiên bản Eco và Plus."
      context: "Thông tin sản phẩm VF 8"
    metadata:
      category: "product_info"
      difficulty: "easy"
```

Promptfoo config hỗ trợ test cases với `vars` và `assert`, nên dataset row có thể map tự nhiên sang Promptfoo tests.

---

## 9.3. UI Variable Syntax

Trong UI, QC nhìn thấy biến theo dạng dễ hiểu:

```text id="pb7zqz"
$dataset.question
$dataset.expected
$dataset.context
```

Runner convert sang Promptfoo template syntax:

```text id="e6fm6k"
{{question}}
{{expected}}
{{context}}
```

Quyết định này giúp:

```text id="uzm68w"
1. UI thân thiện với QC.
2. Backend domain không bị phụ thuộc trực tiếp vào Promptfoo syntax.
3. Runner chịu trách nhiệm mapping sang Promptfoo.
```

---

# 10. Import Dataset

## 10.1. Import từ Excel

Flow:

```text id="hnfeao"
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
9. Backend lưu dataset + dataset rows.
```

---

## 10.2. Import Dataset từ Project khác

Entry point:

```text id="n55u4o"
Project Detail → Datasets tab → Import Dataset
```

Modal import có options:

```text id="uub96m"
- From Excel
- From another project
- From workspace dataset
- From my datasets
```

MVP ưu tiên flow **From another project**.

---

## 10.3. Flow: From another project

```text id="nd9bgs"
1. QC bấm Import Dataset.
2. Chọn From another project.
3. Modal hiển thị search bar + list project user có quyền xem.
4. QC search/chọn project nguồn.
5. Modal hiển thị list dataset trong project nguồn.
6. QC chọn dataset.
7. UI hiển thị preview dataset.
8. Hệ thống check schema compatibility.
9. Nếu schema giống, cho clone luôn.
10. Nếu schema khác, mở mapping step.
11. QC mapping cột source dataset sang schema project hiện tại.
12. QC xác nhận clone.
13. Backend clone toàn bộ dataset rows vào project hiện tại.
14. Dataset mới lưu source metadata để trace.
```

MVP clone toàn bộ rows.

Chưa hỗ trợ:

```text id="ux63cr"
- Clone selected rows
- Live reference dataset
- Auto sync dataset từ source
```

Dataset clone cần lưu:

```text id="jz5po6"
source_dataset_id
source_project_id
source_workspace_id
source_schema_version
import_mapping_json
imported_by
imported_at
```

---

# 11. Chatbot Target Design

## 11.1. MVP chỉ hỗ trợ cURL

Flow:

```text id="tg2dk2"
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
   - rawCurl
   - parsedRequestConfig
   - datasetMapping
7. Backend lưu config.
8. Backend/Runner test connection.
9. Hệ thống lưu example request và example response.
```

Backend không parse lại raw cURL.

Backend không shell-execute raw cURL.

Backend lưu raw cURL để trace/audit, còn khi chạy thật dùng parsed request config.

---

## 11.2. Chatbot Target Entity

```text id="kq1jn4"
id
project_id
name
description
raw_curl
method
url
headers_json
query_params_json
body_json
auth_config_json
dataset_mapping_json
response_example_json
response_extraction_config_json
status
created_by
created_at
updated_at
deleted_at
```

---

## 11.3. Response Extraction

Khi test connection, hệ thống lưu example response.

Ví dụ:

```json id="zdkznx"
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

```text id="kk13g6"
Main answer path: $.data.answer
Intent path: $.data.intent
Confidence path: $.data.confidence
```

Runner convert main answer path thành Promptfoo `transformResponse`.

Promptfoo HTTP provider hỗ trợ `transformResponse` để extract hoặc transform API response; nếu không cấu hình transform, provider sẽ cố parse JSON, nếu parse thất bại thì trả raw text.

---

# 12. Evaluator Design

## 12.1. Evaluator Types

MVP hỗ trợ:

```text id="y0z5x7"
RULE_BASED
LLM_JUDGE
MIXED
```

---

## 12.2. Rule-based Evaluator

Rule gồm:

```text id="wxp4rc"
field_source
operator
expected_value
weight
required
```

Ví dụ:

```text id="crskkp"
response.status equals "success"
response.data.intent equals $dataset.expected_intent
response.data.answer contains $dataset.expected
response.data.confidence greater_than_or_equal 0.7
latency_ms less_than 3000
```

Operator MVP:

```text id="m5fpa0"
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
custom_javascript
```

Mapping sang Promptfoo:

| UI Operator              | Promptfoo Mapping    |
| ------------------------ | -------------------- |
| equals                   | `equals`             |
| contains                 | `contains`           |
| regex                    | `regex`              |
| is_json                  | `is-json`            |
| contains_json            | `contains-json`      |
| greater_than / less_than | JavaScript assertion |
| complex JSON path        | JavaScript assertion |
| custom_javascript        | JavaScript assertion |

Promptfoo config dùng assertions để kiểm tra output có đạt requirement hay không; docs mô tả config chạy từng prompt qua test case và check bằng assertions.

---

## 12.3. LLM Judge Evaluator

LLM Judge config gồm:

```text id="e1heqs"
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

```text id="an9i9t"
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

## 12.4. Evaluator Versioning

Evaluator cần versioning.

```text id="ukqcgm"
Evaluator
  ├── EvaluatorVersion v1
  └── EvaluatorVersion v2
```

Test run phải lock evaluator version.

Lý do:

```text id="icqx6h"
1. Rubric đổi sẽ làm result đổi.
2. Rule đổi sẽ làm pass/fail đổi.
3. Comparison chỉ công bằng nếu dùng cùng evaluator version.
```

---

# 13. Comparison Design

## 13.1. Mục tiêu

Comparison giúp QC so sánh chatbot nội bộ với một hoặc nhiều baseline models.

MVP hỗ trợ:

```text id="x6lik4"
1 internal chatbot target
+ 1 hoặc nhiều LLM baseline targets
```

---

## 13.2. Điều kiện comparison công bằng

Các target trong cùng comparison run phải dùng chung:

```text id="vbkwt0"
- Dataset
- Dataset version
- Schema version
- Evaluator version
- Judge model
- Run config
```

Chỉ khác:

```text id="m595tu"
- Target chatbot/model
```

---

## 13.3. Comparison Dashboard MVP

Dashboard hiển thị:

```text id="gioub7"
- Pass rate theo target
- Average score theo target
- Average latency theo target
- Failed count theo target
- Cost/token nếu có
- Bảng từng testcase theo target
- Judge reason từng target
```

MVP chưa cần auto winner bằng LLM.

Có thể hiển thị đơn giản:

```text id="w4ou8e"
- Highest score
- Lowest latency
- Passed targets
```

---

# 14. Execution Architecture v0.3

## 14.1. Queue MVP

MVP dùng **Redis Streams** để giao tiếp giữa Backend và Runner.

Redis Streams là append-only log có hỗ trợ consumer groups, phù hợp cho event processing và nhiều consumer cùng xử lý stream. Redis docs mô tả Streams có thể dùng để ghi nhận và phân phối events realtime, đồng thời hỗ trợ consumption strategy như consumer groups.

MVP dùng 2 streams:

```text id="wdsdqo"
vfqc:runs:commands
vfqc:runs:events
```

### `vfqc:runs:commands`

Backend publish command.

Runner consume command.

Ví dụ command:

```json id="somfgq"
{
  "commandId": "cmd_123",
  "commandType": "START_RUN",
  "testRunId": "run_123",
  "projectId": "project_456",
  "requestedBy": "user_789",
  "createdAt": "2026-07-01T10:00:00+07:00"
}
```

Cancel command:

```json id="w4bve0"
{
  "commandId": "cmd_124",
  "commandType": "CANCEL_RUN",
  "testRunId": "run_123",
  "requestedBy": "user_789",
  "createdAt": "2026-07-01T10:02:00+07:00"
}
```

### `vfqc:runs:events`

Runner publish event.

Backend consume event.

Ví dụ event:

```json id="7d0788"
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
  "createdAt": "2026-07-01T10:00:15+07:00"
}
```

Redis `XREADGROUP` là command dùng để đọc stream qua consumer group, phù hợp với việc nhiều runner instance cùng consume command stream.

---

## 14.2. Communication Flow

```text id="glrhhr"
1. User click Run.
2. Backend validate permission.
3. Backend lock dataset/schema/evaluator versions.
4. Backend tạo test_run status = PENDING.
5. Backend XADD START_RUN vào vfqc:runs:commands.
6. Runner consume START_RUN bằng consumer group.
7. Runner lấy full run config từ Backend API.
8. Runner build Promptfoo config.
9. Runner chạy Promptfoo bằng Node API.
10. Runner publish progress/result event vào vfqc:runs:events.
11. Backend consume events.
12. Backend persist result vào DB.
13. Backend đẩy realtime progress xuống frontend qua SSE.
14. Khi run xong, runner publish RUN_COMPLETED hoặc RUN_FAILED.
15. Backend update final status.
```

---

## 14.3. Backend ↔ Runner Data Contract

### START_RUN command chỉ chứa reference

Redis command không nên chứa full dataset/config/secret.

Command chỉ chứa:

```text id="zkgveg"
- commandId
- commandType
- testRunId
- projectId
- requestedBy
- createdAt
```

Runner gọi Backend API để lấy full config:

```text id="pne74u"
GET /internal/runner/test-runs/{testRunId}/config
```

Lý do:

```text id="ennp4k"
1. Redis message nhỏ.
2. Không expose secret/dataset lớn trong queue.
3. Backend vẫn là source of truth.
4. Runner luôn lấy config mới nhất đã lock theo test run.
```

---

## 14.4. Runner Event Payload

Redis event chứa **normalized result**, không chứa artifact lớn.

Event payload nên chứa:

```text id="f7ggmc"
- eventId
- eventType
- testRunId
- runnerId
- datasetRowId
- targetId
- status
- passed
- score
- latencyMs
- compact extracted output
- compact judge reason
- error message nếu có
- artifact references nếu cần
```

Full artifact lưu local.

Không nhét full report hoặc file lớn vào Redis Stream.

---

# 15. Runner Design

## 15.1. Runner Tech Stack

Runner là Node.js service.

MVP dùng:

```text id="h2eqhe"
Promptfoo Node API only
```

Không dùng CLI fallback.

Lý do:

```text id="m1lhwd"
1. Không cần spawn process.
2. Build Promptfoo config trực tiếp bằng object.
3. Dễ integrate progress callback.
4. Dễ control execution context.
5. Dễ normalize result bằng code.
6. Phù hợp service chạy lâu dài.
```

Promptfoo Node package cho phép gọi `promptfoo.evaluate(testSuite, options)` và nhận eval record để lấy summary, nên phù hợp với runner service hơn là chỉ gọi CLI.

---

## 15.2. Runner Instance Model

Mỗi runner instance xử lý **1 test run tại một thời điểm**.

Scale bằng nhiều runner instances.

```text id="b9u47l"
runner-01 → run A
runner-02 → run B
runner-03 → run C
```

Lý do:

```text id="bzxikc"
1. MVP đơn giản.
2. Dễ giới hạn memory.
3. Dễ debug.
4. Dễ cancel.
5. Dễ scale ngang.
6. Tránh một process ôm quá nhiều run nặng.
```

---

## 15.3. Runner Lifecycle

```text id="kebfht"
1. Boot runner.
2. Register runnerId.
3. Join Redis consumer group.
4. Wait START_RUN command.
5. Claim one command.
6. Check idempotency.
7. Fetch full run config.
8. Publish RUN_STARTED.
9. Build Promptfoo config.
10. Write config artifact.
11. Execute Promptfoo Node API.
12. For each testcase completed:
    - mask sensitive fields
    - write partial artifact nếu cần
    - publish TESTCASE_COMPLETED
13. On completed:
    - write final result artifacts
    - publish RUN_COMPLETED
    - ACK command
14. On failed:
    - write error artifact/log
    - publish RUN_FAILED
    - ACK command nếu lỗi đã được xử lý và persist
```

---

# 16. Promptfoo Integration

## 16.1. Promptfoo Config Builder

Runner có component:

```text id="6qiesr"
PromptfooConfigBuilder
```

Input:

```text id="t8m0bn"
- Test run config
- Dataset rows
- Chatbot targets
- Baseline providers
- Evaluator versions
- Judge provider/model
- Response extraction config
```

Output:

```text id="83k38z"
Promptfoo TestSuiteConfiguration object
```

Promptfoo docs mô tả Node API nhận `TestSuiteConfiguration` object tương đương file `promptfooconfig.yaml`, nên runner có thể build object trực tiếp thay vì sinh YAML rồi gọi CLI.

---

## 16.2. Mapping Domain Config sang Promptfoo

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

Promptfoo output docs hỗ trợ export result sang nhiều format như JSON, JSONL, CSV và HTML, trong đó JSON/JSONL phù hợp cho backend ingest còn HTML phù hợp làm report artifact.

---

## 16.3. Generated Promptfoo Config Example

```yaml id="mlucse"
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
      expected: "VF 8 có hai phiên bản Eco và Plus."
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

---

# 17. Test Run State Machine

## 17.1. Status

```text id="z6uolz"
PENDING
RUNNING
CANCELLING
CANCELLED
COMPLETED
FAILED
```

---

## 17.2. State Transition

```text id="rpbov3"
PENDING → RUNNING
PENDING → CANCELLED
RUNNING → CANCELLING
CANCELLING → CANCELLED
RUNNING → COMPLETED
RUNNING → FAILED
CANCELLING → FAILED
```

Không cho transition:

```text id="sa7efc"
COMPLETED → RUNNING
FAILED → RUNNING
CANCELLED → RUNNING
```

Muốn chạy lại thì tạo `test_run` mới.

---

## 17.3. Status Meaning

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

```text id="2s5nsr"
Testcase fail do evaluator không làm test_run status = FAILED.
Đó là result hợp lệ của run.
```

---

# 18. Cancel Run Design

## 18.1. Quyết định

MVP dùng:

```text id="ij0b93"
Cancel command: HTTP POST
Realtime progress: SSE
Không dùng WebSocket trong MVP
```

SSE là kết nối một chiều từ server xuống client; MDN mô tả Server-Sent Events/EventSource là unidirectional, dữ liệu đi từ server đến browser. Vì vậy SSE phù hợp để đẩy progress/result xuống frontend, còn cancel là hành động client gửi lên server nên dùng HTTP API là hợp lý hơn.

WebSocket hỗ trợ giao tiếp hai chiều giữa browser và server, nhưng MVP chưa cần vì chỉ có một chiều realtime từ backend xuống frontend, còn command từ frontend lên backend có thể dùng HTTP request thông thường.

---

## 18.2. Cancel Flow

```text id="rgw5fb"
1. User click Cancel Run.
2. Frontend gọi:
   POST /api/test-runs/{testRunId}/cancel
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

## 18.3. Runner Cancellation Check

Runner check cancellation flag:

```text id="jx9ypy"
- Trước khi bắt đầu run.
- Trước mỗi testcase.
- Sau mỗi testcase.
- Trước khi gọi baseline target tiếp theo.
- Trước khi gọi LLM Judge tiếp theo.
```

Control API:

```text id="7rza9l"
GET /internal/runner/test-runs/{testRunId}/control-state
```

Response:

```json id="u955rb"
{
  "testRunId": "run_123",
  "status": "CANCELLING",
  "shouldCancel": true
}
```

---

# 19. Realtime Progress via SSE

## 19.1. SSE Endpoint

Frontend mở SSE connection:

```text id="lnfdyk"
GET /api/test-runs/{testRunId}/events
```

Backend push events:

```text id="hpw8ct"
RUN_STARTED
TESTCASE_STARTED
TESTCASE_COMPLETED
RUN_PROGRESS
RUN_COMPLETED
RUN_FAILED
RUN_CANCELLED
```

---

## 19.2. SSE Event Example

```text id="y1m5f2"
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

## 19.3. Backend SSE Flow

```text id="x3m0m8"
1. Backend consumes vfqc:runs:events.
2. Backend persists event/result vào DB.
3. Backend publishes internal application event.
4. SSE controller pushes event tới clients đang subscribe run đó.
```

Nếu frontend reconnect, UI có thể gọi:

```text id="l896cv"
GET /api/test-runs/{testRunId}
GET /api/test-runs/{testRunId}/results
```

để lấy snapshot hiện tại rồi tiếp tục nghe SSE.

---

# 20. Retry & Idempotency

## 20.1. Retry Policy

MVP retry system error tối đa 3 lần.

```text id="ky0wvw"
maxAttempts = 3
backoff = exponential
```

Retry khi:

```text id="zq7wr3"
- Runner crash.
- Redis temporary issue.
- Backend temporary unavailable.
- Artifact write temporary failure.
- Provider/network transient error ở cấp system.
```

Không retry khi:

```text id="ufoufi"
- Chatbot trả lời sai.
- LLM Judge chấm fail.
- Rule-based evaluator fail.
- Testcase fail do business logic.
```

Testcase fail là kết quả hợp lệ, không phải system error.

---

## 20.2. Redis ACK Rule

Runner chỉ ACK command khi:

```text id="zhxg2h"
1. Run đã completed/failed/cancelled.
2. Runner đã publish final event.
3. Backend đã consume/persist final result hoặc có cơ chế đảm bảo event sẽ được consume.
```

Với Redis Streams, message không ACK sẽ nằm trong pending state của consumer group và có thể được inspect/recover qua cơ chế pending entries. Redis docs mô tả consumer group read qua `XREADGROUP`; command như `XACK` được dùng để acknowledge message đã xử lý.

---

## 20.3. Idempotency

Idempotency key:

```text id="z8wp93"
commandId + testRunId
```

Rule:

```text id="ksiybx"
1. Một testRunId chỉ được start một lần.
2. Duplicate START_RUN command không tạo run mới.
3. Runner nhận duplicate command phải check test_run status trước.
4. Backend không cho start lại run đã COMPLETED/FAILED/CANCELLED.
5. Muốn chạy lại thì tạo test_run mới.
```

Runner behavior:

```text id="qlszie"
If test_run.status in [RUNNING, COMPLETED, FAILED, CANCELLED]:
  skip duplicate START_RUN
```

---

# 21. Artifact Storage

## 21.1. Storage Strategy

MVP dùng storage abstraction.

MVP implementation lưu local.

Path:

```text id="vmchrb"
./storage/artifacts/test-runs/{testRunId}/
```

Cấu trúc:

```text id="mptmf0"
./storage/artifacts/test-runs/{testRunId}/
  ├── promptfooconfig.json
  ├── results.json
  ├── results.jsonl
  ├── report.html
  └── runner.log
```

Sau này có thể đổi sang:

```text id="f0po4o"
- MinIO
- S3
- GCS
- Azure Blob Storage
```

mà không ảnh hưởng domain model.

---

## 21.2. Artifact Metadata Table

```text id="yw9sw7"
promptfoo_run_artifacts
- id
- test_run_id
- config_file_path
- result_json_path
- result_jsonl_path
- html_report_path
- logs_path
- created_at
```

---

## 21.3. Artifact Types

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

# 22. Sensitive Data Masking

## 22.1. Quyết định

Hệ thống lưu raw request/response nhưng phải mask sensitive fields.

Mask ở hai lớp:

```text id="k8o04a"
1. Runner-side masking trước khi publish event/artifact.
2. Backend-side masking trước khi persist DB.
```

---

## 22.2. Fields cần mask mặc định

```text id="dryjlz"
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
```

---

## 22.3. Mask Format

Ví dụ:

```json id="k1f0rq"
{
  "Authorization": "Bearer ****MASKED****",
  "api_key": "****MASKED****"
}
```

Không nên xóa field hoàn toàn vì QC vẫn cần biết request có header/token hay không.

---

# 23. Result Persistence

## 23.1. Test Run Result

Mỗi run lưu summary:

```text id="ijw47q"
total_testcases
completed_testcases
passed_count
failed_count
error_count
pass_rate
average_score
average_latency_ms
status
started_at
finished_at
```

---

## 23.2. Test Case Result

Mỗi testcase result lưu:

```text id="ixzrom"
id
test_run_id
test_run_target_id
dataset_row_id
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
created_at
```

Với comparison run:

```text id="eqwkr9"
Một dataset_row_id có nhiều test_case_results,
mỗi result ứng với một target.
```

---

# 24. Dashboard MVP

## 24.1. Single Target Dashboard

Hiển thị:

```text id="r00j6w"
- Total test cases
- Completed test cases
- Passed
- Failed
- Pass rate
- Average score
- Average latency
- Error count
- Failed cases by category
- Score distribution
- Result table
```

Result table:

```text id="xaznwe"
- Question
- Expected
- Actual answer
- Pass/fail
- Score
- Judge reason
- Rule results
- Latency
- Error
```

---

## 24.2. Testcase Detail

Mỗi testcase detail cần có:

```text id="cm99lh"
- Dataset row
- Rendered prompt
- Resolved request
- Raw response
- Extracted output
- Rule assertion results
- LLM judge result
- Error logs
- Artifact references
```

---

## 24.3. Comparison Dashboard

Hiển thị:

```text id="jfdjgv"
- Metrics by target
- Pass/fail by target
- Average score by target
- Average latency by target
- Side-by-side output
- Side-by-side judge reason
- Filter failed cases
- Filter by category/difficulty
```

---

# 25. Main User Flows

## 25.1. Admin Setup Workspace

```text id="ho2r9m"
1. Admin tạo Team Workspace.
2. Admin invite QC members.
3. Admin tạo Workspace Secret.
4. Admin tạo Workspace Provider.
5. Admin chọn default model cho LLM Judge.
6. QC vào workspace và dùng provider đã setup.
7. Nếu workspace key invalid/quota exceeded, QC có thể dùng Personal Provider.
```

---

## 25.2. QC Tạo Project Test

```text id="b8o8wk"
1. QC tạo project.
2. Hệ thống tạo schema mặc định question + expected.
3. QC paste cURL chatbot target.
4. Frontend parse cURL.
5. QC preview request.
6. QC mapping dataset fields.
7. QC test connection.
8. Hệ thống lưu example response.
9. QC chọn main answer path.
10. QC import/reuse dataset.
11. QC cấu hình evaluator.
12. QC chạy test.
13. Backend publish START_RUN.
14. Runner chạy Promptfoo Node API.
15. Backend nhận events.
16. UI nhận progress qua SSE.
17. QC xem dashboard.
```

---

## 25.3. QC Import Dataset từ Project khác

```text id="i1j5ho"
1. QC bấm Import Dataset.
2. Chọn From another project.
3. Modal hiển thị search project.
4. QC chọn project nguồn.
5. Modal hiển thị list dataset trong project nguồn.
6. QC chọn dataset.
7. UI preview dataset.
8. Hệ thống check schema.
9. Nếu schema khác, QC mapping cột.
10. QC xác nhận clone.
11. Backend clone toàn bộ rows vào project hiện tại.
12. Dataset mới lưu source metadata.
```

---

## 25.4. QC Chạy Comparison

```text id="vlq4hn"
1. QC chọn dataset.
2. QC chọn internal chatbot target.
3. QC chọn baseline providers/models.
4. QC chọn evaluator.
5. QC chọn LLM Judge.
6. QC click Run comparison.
7. Backend tạo COMPARISON_RUN.
8. Backend publish START_RUN.
9. Runner chạy cùng tests trên nhiều targets.
10. Runner publish events theo testcase/target.
11. Backend persist result.
12. Frontend nhận realtime progress qua SSE.
13. UI hiển thị comparison dashboard.
```

---

## 25.5. QC Cancel Run

```text id="qrxqsu"
1. QC bấm Cancel.
2. Frontend gọi POST /api/test-runs/{testRunId}/cancel.
3. Backend check permission.
4. Backend update status = CANCELLING.
5. Backend publish CANCEL_RUN vào command stream.
6. Runner nhận command hoặc phát hiện status qua control API.
7. Runner dừng giữa testcase/batch.
8. Runner publish RUN_CANCELLED.
9. Backend update status = CANCELLED.
10. UI nhận RUN_CANCELLED qua SSE.
```

---

# 26. MVP Scope v0.3

## 26.1. In Scope

```text id="a0t5pu"
- Personal Workspace
- Team Workspace
- Workspace role: Owner/Admin/Member/Viewer
- Workspace-level permission
- Personal Secret
- Workspace Secret
- Personal Provider
- Workspace Provider
- Provider model selection
- Project management
- Dataset schema question/expected mặc định
- Schema versioning
- Excel dataset import
- Dataset reuse từ project khác
- Dataset reuse từ workspace/personal dataset
- cURL chatbot target config
- Frontend cURL parsing
- Backend lưu rawCurl + parsedRequestConfig
- Test connection / save example response
- Response field selection
- Rule-based evaluator
- LLM Judge evaluator
- Evaluator versioning
- Promptfoo Node API integration
- Redis Stream command queue
- Redis Stream event queue
- Runner service
- Single target run
- Comparison run
- SSE realtime progress từng testcase
- Cancel run cơ bản
- Retry system error tối đa 3 lần
- Idempotency theo commandId + testRunId
- Local artifact storage abstraction
- Raw request/response storage với sensitive data masking
- Single target dashboard
- Comparison dashboard
```

---

## 26.2. Out of Scope

```text id="mik09l"
- Project-level permission
- Custom permission theo từng action
- Live reference dataset
- Auto-sync dataset từ source project
- Auto fallback secret phức tạp
- Secret rotation workflow nâng cao
- AI generate dataset
- Scheduled regression testing
- CI/CD integration
- Cost tracking chi tiết
- Full audit log enterprise
- Comment/collaboration trên testcase
- Approval workflow
- Red teaming module
- Prompt optimization module
- WebSocket realtime control
- Promptfoo CLI fallback
- Distributed lock nâng cao
- Kubernetes job orchestration
```

---

# 27. Domain Entities v0.3

```text id="zzfe3r"
User
Workspace
WorkspaceMember

Secret
LLMProvider

Project

DatasetSchema
DatasetSchemaColumn
Dataset
DatasetRow
DatasetImportJob

ChatbotTarget
ChatbotTargetExample

Evaluator
EvaluatorVersion
EvaluatorRule

TestRun
TestRunTarget
TestCaseResult

RunnerCommand
RunnerEvent
RunnerInstance

PromptfooConfigSnapshot
PromptfooRunArtifact
```

---

# 28. Database Tables v0.3

## workspaces

```text id="meyc1v"
id
name
type
created_by
created_at
updated_at
deleted_at
```

## workspace_members

```text id="ls5yrt"
id
workspace_id
user_id
role
status
invited_by
joined_at
created_at
updated_at
```

## secrets

```text id="zywzrz"
id
scope
owner_user_id
workspace_id
name
encrypted_value
status
created_by
created_at
updated_at
deleted_at
```

## llm_providers

```text id="il1z0j"
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

## projects

```text id="ik6fzy"
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

## dataset_schemas

```text id="ewmmfj"
id
project_id
version
name
created_by
created_at
```

## dataset_schema_columns

```text id="l12xit"
id
schema_id
name
display_name
data_type
required
description
order_index
created_at
updated_at
```

## datasets

```text id="qbinrz"
id
scope
workspace_id
project_id
owner_user_id
schema_id
schema_version
name
description
source_dataset_id
source_project_id
source_workspace_id
source_type
import_mapping_json
created_by
created_at
updated_at
deleted_at
```

## dataset_rows

```text id="sbbdk7"
id
dataset_id
row_index
data_json
metadata_json
created_at
updated_at
```

## dataset_import_jobs

```text id="mgm3yh"
id
target_project_id
source_type
source_file_id
source_dataset_id
status
mapping_json
preview_json
error_message
created_by
created_at
finished_at
```

## chatbot_targets

```text id="yh9y36"
id
project_id
name
description
raw_curl
method
url
headers_json
query_params_json
body_json
auth_config_json
dataset_mapping_json
response_example_json
response_extraction_config_json
status
created_by
created_at
updated_at
deleted_at
```

## evaluators

```text id="cyegaz"
id
project_id
name
type
created_by
created_at
updated_at
deleted_at
```

## evaluator_versions

```text id="t7kp1c"
id
evaluator_id
version
config_json
created_by
created_at
```

## test_runs

```text id="n70xs5"
id
project_id
run_type
dataset_id
dataset_version
dataset_schema_id
dataset_schema_version
evaluator_version_ids_json
judge_provider_id
judge_model
status
progress_completed
progress_total
summary_metrics_json
promptfoo_config_snapshot_json
started_by
started_at
finished_at
cancel_requested_at
created_at
updated_at
```

## test_run_targets

```text id="cab1bx"
id
test_run_id
target_type
chatbot_target_id
llm_provider_id
model
display_name
provider_label
config_json
created_at
```

## test_case_results

```text id="wrowkp"
id
test_run_id
test_run_target_id
dataset_row_id
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
created_at
```

## runner_commands

```text id="gjz4s4"
id
command_id
test_run_id
command_type
stream_message_id
status
attempt_count
created_at
acknowledged_at
```

## runner_events

```text id="x90sl5"
id
event_id
test_run_id
event_type
stream_message_id
payload_json
created_at
processed_at
```

## runner_instances

```text id="xezgon"
id
runner_id
status
current_test_run_id
last_heartbeat_at
created_at
updated_at
```

## promptfoo_run_artifacts

```text id="hyvsc8"
id
test_run_id
config_file_path
result_json_path
result_jsonl_path
html_report_path
logs_path
created_at
```

---

# 29. Product Decisions v0.3

```text id="n27qbp"
1. Promptfoo là execution engine chính.
2. Runner dùng Promptfoo Node API, không dùng CLI.
3. Backend Java là source of truth.
4. Promptfoo config là generated artifact.
5. Queue MVP dùng Redis Streams.
6. Dùng 2 streams: vfqc:runs:commands và vfqc:runs:events.
7. Backend publish command, Runner consume command.
8. Runner publish event, Backend consume event.
9. Backend đẩy realtime progress xuống frontend qua SSE.
10. Cancel dùng HTTP POST, không dùng WebSocket.
11. Cancel flow dùng hybrid: DB status CANCELLING + CANCEL_RUN command + runner check control API.
12. Mỗi runner instance xử lý 1 run tại một thời điểm.
13. Scale bằng nhiều runner instances.
14. Redis event chứa normalized result.
15. Artifact lưu local qua storage abstraction.
16. Artifact path: ./storage/artifacts/test-runs/{testRunId}/.
17. Lưu raw request/response nhưng mask sensitive fields.
18. Mask sensitive fields ở cả runner và backend.
19. Retry system error tối đa 3 lần.
20. Không auto-retry testcase fail do evaluation.
21. Idempotency theo commandId + testRunId.
22. Dataset import từ project khác dùng wizard: search project → select dataset → preview/mapping → clone.
23. MVP clone toàn bộ dataset rows.
```

---

# 30. Open Questions cho v0.4

```text id="pe14bt"
1. Runner có cần heartbeat monitor để auto-detect runner chết không?
2. Có cần reclaim pending Redis messages bằng XAUTOCLAIM trong MVP không?
3. Có cần giới hạn max dataset rows per run ở MVP không?
4. Có cần timeout per testcase và timeout toàn run không?
5. Có cần rate limit theo provider/workspace không?
6. Có cần queue priority cho run quan trọng không?
7. Có cần pause/resume run không?
8. Có cần partial rerun failed testcases không?
9. Có cần export report từ dashboard không?
10. Có cần retention policy cho raw request/response/artifacts không?
11. Có cần encrypt artifact local không?
12. Có cần object storage ngay từ staging/prod không?
13. Có cần audit log chi tiết cho secret/provider/test run không?
14. Có cần hỗ trợ multi-turn chatbot test trong MVP+ không?
15. Có cần test dataset bằng sampling trước khi chạy full dataset không?
```

---

# 31. MVP Success Criteria

MVP đạt yêu cầu nếu QC có thể:

```text id="m60s5n"
1. Tạo project trong workspace.
2. Dùng provider/key do Admin setup hoặc key cá nhân.
3. Paste cURL chatbot target.
4. Mapping question/context vào request.
5. Lưu example response và chọn answer path.
6. Import Excel dataset.
7. Import dataset từ project khác bằng wizard.
8. Cấu hình rule-based evaluator.
9. Cấu hình LLM Judge.
10. Chạy single target run.
11. Theo dõi từng testcase realtime qua SSE.
12. Cancel run khi cần.
13. Chạy comparison run với baseline model.
14. Xem dashboard và testcase detail.
15. Debug được request/response/evaluator reason khi testcase fail.
16. Xem artifact path/report của từng run.
```

MVP technical success criteria:

```text id="z45ic9"
1. Backend publish START_RUN vào Redis Stream.
2. Runner consume command và chạy Promptfoo Node API thành công.
3. Runner publish TESTCASE_COMPLETED events.
4. Backend consume event và persist DB.
5. Frontend nhận realtime update qua SSE.
6. Cancel flow chuyển RUNNING → CANCELLING → CANCELLED.
7. System error retry tối đa 3 lần.
8. Duplicate command không tạo duplicate run.
9. Raw request/response được mask trước khi lưu.
10. Artifact được lưu theo path chuẩn.
```

