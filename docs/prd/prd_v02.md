# VinFast QC Copilot — MVP Product Spec v0.2

## 1. Tổng quan sản phẩm

**VinFast QC Copilot** là platform hỗ trợ đội **QC/QA chuyên test chatbot hoặc AI nội bộ của công ty**.

Platform giúp QC:

* Quản lý workspace/project test.
* Cấu hình chatbot nội bộ cần test bằng cURL.
* Import và tái sử dụng dataset.
* Mapping dataset vào request chatbot.
* Cấu hình rule-based evaluator.
* Cấu hình LLM Judge.
* Chạy test tự động.
* Xem dashboard kết quả.
* So sánh chatbot nội bộ với các LLM/model baseline khác.

**Promptfoo là execution engine chính**. VinFast QC Copilot không cần tự xây toàn bộ evaluation engine từ đầu. Hệ thống sẽ lưu config ở dạng domain model riêng, sau đó runner chuyển config này thành Promptfoo-compatible config để thực thi.

## Promptfoo có cấu trúc cốt lõi gồm `providers/targets`, `tests`, `vars`, `assertions`, `transformResponse` và các output report như JSON/JSONL/CSV/HTML. Vì vậy MVP cần thiết kế domain model sao cho mapping được rõ sang các khái niệm này. Promptfoo docs xác nhận HTTP provider có thể gọi endpoint HTTP, truyền method/header/body/query params, dùng biến template trong request và dùng `transformResponse` để lấy output từ response.

## 2. Product Goal

MVP cần giúp QC hoàn thành flow sau:

```text id="2wkyk5"
1. Vào workspace.
2. Tạo project test chatbot.
3. Cấu hình secret/provider/model.
4. Paste cURL chatbot nội bộ.
5. Parse cURL trên frontend thành request config.
6. Mapping dataset field vào request body/header/query.
7. Import hoặc reuse dataset.
8. Cấu hình evaluator.
9. Chạy test bằng Promptfoo runner.
10. Xem kết quả từng testcase.
11. Xem dashboard tổng quan.
12. Chạy comparison với baseline model.
```

---

## 3. Primary User

MVP chỉ phục vụ nhóm user chính:

```text id="e424bc"
QC / QA team chuyên test chatbot hoặc AI nội bộ của công ty
```

Không mở rộng MVP cho:

* External customer.
* General business user.
* Public SaaS user.
* Developer platform user bên ngoài.

---

# 4. Core Architecture

## 4.1. Kiến trúc tổng quan

```text id="0xfyjy"
Frontend
  └── Parse cURL
  └── Mapping UI
  └── Dataset import UI
  └── Evaluator builder UI
  └── Dashboard UI

Backend Java
  └── Auth / permission
  └── Workspace / project management
  └── Secret / provider management
  └── Dataset / schema management
  └── Chatbot target config storage
  └── Evaluator config storage
  └── Test run orchestration
  └── Result storage

Runner Node.js / Promptfoo Runtime
  └── Convert domain config → Promptfoo config
  └── Run Promptfoo eval
  └── Call internal chatbot target
  └── Call baseline LLM targets
  └── Run assertions / LLM Judge
  └── Export results JSON/JSONL
  └── Return normalized results to backend
```

## 4.2. Lý do nên có Runner riêng

Backend đang dùng Java, trong khi Promptfoo là CLI/library thiên về Node.js ecosystem. Promptfoo cũng có Node API và CLI, hỗ trợ evaluate, stream result callback, cache, concurrency và batch evaluation. Vì vậy nên tách runner riêng để backend Java không phải xử lý trực tiếp logic Promptfoo.

Backend chỉ cần:

```text id="pdszmq"
- Lưu config
- Validate permission
- Tạo test run
- Gửi job sang runner
- Nhận result
- Lưu result
- Hiển thị dashboard
```

Runner chịu trách nhiệm:

```text id="a60nsv"
- Build promptfooconfig
- Resolve secret an toàn
- Chạy promptfoo eval
- Export JSON/JSONL result
- Normalize result về domain result của hệ thống
```

Promptfoo CLI hỗ trợ export result sang JSON, JSONL, CSV, HTML và JUnit XML. JSON/JSONL phù hợp nhất cho backend ingest kết quả.

---

# 5. Promptfoo Concept Mapping

## 5.1. Mapping giữa VinFast QC Copilot và Promptfoo

| VinFast QC Copilot          | Promptfoo concept                                  | Ghi chú                                                      |
| --------------------------- | -------------------------------------------------- | ------------------------------------------------------------ |
| Chatbot Target nội bộ       | HTTP provider / target                             | Dùng `providers` hoặc `targets` dạng HTTP                    |
| LLM baseline model          | Provider / target                                  | OpenAI, Gemini, Claude, custom provider                      |
| Dataset row                 | Test case                                          | Mỗi row thành một item trong `tests`                         |
| Dataset columns             | `vars`                                             | Ví dụ `question`, `expected`, `context`                      |
| `$dataset.question` trên UI | `{{question}}` trong Promptfoo                     | UI-friendly syntax, runner convert                           |
| Response extraction         | `transformResponse`                                | Lấy `json.data.answer`, `json.output`, v.v.                  |
| Rule-based evaluator        | Assertion                                          | `contains`, `equals`, `regex`, `is-json`, `javascript`, etc. |
| LLM Judge evaluator         | Model-graded assertion                             | `llm-rubric`, `factuality`, `answer-relevance`, etc.         |
| Comparison run              | Multi-provider eval                                | Cùng tests, nhiều providers/targets                          |
| Dashboard result            | Promptfoo output JSON/JSONL + normalized DB result | Backend lưu lại result đã normalize                          |

Promptfoo test case có thể chứa `vars`, `assert` và `metadata`; đây là mapping tự nhiên cho dataset row, expected result và category/difficulty của testcase.

---

## 5.2. Ví dụ Promptfoo config runner cần sinh ra

Ví dụ một chatbot nội bộ nhận request HTTP:

```yaml
description: "VinFast QC Copilot Run #123"

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
  - description: "TC001 - hỏi thông tin xe"
    vars:
      question: "VF 8 có mấy phiên bản?"
      expected: "VF 8 có các phiên bản..."
      context: "Thông tin sản phẩm VF 8"
      category: "product_info"
    assert:
      - type: llm-rubric
        value: "Câu trả lời phải đúng ý với expected, không hallucinate, không bỏ sót thông tin quan trọng."
        threshold: 0.7
      - type: contains
        value: "VF 8"
```

Trong UI, QC có thể nhìn thấy mapping dạng:

```text id="bfxevl"
body.context  ← $dataset.context
body.question ← $dataset.question
```

Nhưng runner sẽ convert thành:

```text id="xrngm8"
body.context  ← {{context}}
body.question ← {{question}}
```

---

# 6. Workspace Design

## 6.1. Workspace Type

MVP hỗ trợ:

```text id="mghrzb"
PERSONAL
TEAM
```

### Personal Workspace

Được tạo tự động khi user đăng ký.

```text id="oz9n8f"
User mới → Personal Workspace → user là Owner
```

### Team Workspace

Dùng cho team QC làm việc chung.

Workspace chứa:

```text id="gmm4gz"
- Members
- Projects
- Workspace Secrets
- Workspace Providers
- Workspace Datasets
- Shared configs
```

---

## 6.2. Workspace Role

MVP dùng workspace-level role:

```text id="4wf9mj"
OWNER
ADMIN
MEMBER
VIEWER
```

Chưa làm project-level permission trong MVP.

### Default

```text id="g0hzjz"
Người tạo workspace = OWNER
Người được invite = MEMBER
```

---

## 6.3. Permission Matrix MVP

| Action                  | Owner | Admin |   Member | Viewer |
| ----------------------- | ----: | ----: | -------: | -----: |
| Xem workspace           |    Có |    Có |       Có |     Có |
| Sửa workspace           |    Có |    Có |    Không |  Không |
| Xóa workspace           |    Có | Không |    Không |  Không |
| Mời member              |    Có |    Có |    Không |  Không |
| Đổi role member         |    Có |    Có |    Không |  Không |
| Tạo project             |    Có |    Có |       Có |  Không |
| Sửa project             |    Có |    Có |       Có |  Không |
| Xóa project             |    Có |    Có | Giới hạn |  Không |
| Xem project             |    Có |    Có |       Có |     Có |
| Import dataset          |    Có |    Có |       Có |  Không |
| Reuse dataset           |    Có |    Có |       Có |  Không |
| Cấu hình chatbot target |    Có |    Có |       Có |  Không |
| Cấu hình evaluator      |    Có |    Có |       Có |  Không |
| Chạy test               |    Có |    Có |       Có |  Không |
| Xem dashboard           |    Có |    Có |       Có |     Có |
| Tạo Workspace Secret    |    Có |    Có |    Không |  Không |
| Tạo Workspace Provider  |    Có |    Có |    Không |  Không |
| Tạo Personal Secret     |    Có |    Có |       Có |  Không |
| Tạo Personal Provider   |    Có |    Có |       Có |  Không |
| Xem plain text secret   | Không | Không |    Không |  Không |

---

# 7. Secret & Provider Design

## 7.1. Secret Scope

MVP hỗ trợ hai loại secret:

```text id="mfuuup"
PERSONAL
WORKSPACE
```

### Personal Secret

Do user tạo, chỉ user đó dùng.

Dùng khi:

```text id="gy35pb"
- QC có key riêng
- Workspace key hết quota
- Workspace key invalid
- QC muốn chạy test bằng provider cá nhân
```

### Workspace Secret

Do Owner/Admin tạo.

Dùng khi:

```text id="57udx1"
- Admin setup sẵn key cho QC
- Team dùng chung provider/model
- Không muốn từng QC phải tự nhập key
```

Không role nào được xem lại plain text secret sau khi lưu.

---

## 7.2. Provider Scope

MVP hỗ trợ:

```text id="z5dus6"
PERSONAL_PROVIDER
WORKSPACE_PROVIDER
```

Provider có thể dùng cho:

```text id="iymq5z"
- LLM Judge
- Baseline model trong comparison run
- Dataset generation ở phase sau
```

Provider cần lưu:

```text id="gwey49"
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

---

## 7.3. Provider Type MVP

Nên hỗ trợ tối thiểu:

```text id="so88va"
OPENAI_COMPATIBLE
GEMINI
CLAUDE
CUSTOM_HTTP
```

Trong đó `CUSTOM_HTTP` có thể map sang Promptfoo HTTP provider.

Promptfoo hỗ trợ nhiều provider/model khác nhau và cũng cho phép dùng HTTP endpoint custom như một provider/target.

---

# 8. Project Design

Một project thuộc một workspace.

Project là nơi QC cấu hình test cho một chatbot hoặc một nhóm chatbot liên quan.

Project chứa:

```text id="ftrhv5"
- Dataset Schema
- Datasets
- Chatbot Targets
- Evaluators
- Test Runs
- Comparison Runs
- Reports / Dashboard
```

Project entity:

```text id="y6e6up"
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

MVP chưa có project-level permission riêng. Quyền lấy từ workspace role.

---

# 9. Dataset Schema Design

## 9.1. Default Schema

Khi tạo project, hệ thống tạo schema mặc định:

```text id="5fklq9"
question
expected
```

User có thể:

```text id="1khtyp"
- Đổi tên cột
- Thêm cột
- Sửa cột
- Xóa cột
- Đổi mô tả
- Set required / optional
```

Ví dụ schema:

```text id="5mmqux"
question
expected
context
category
difficulty
expected_intent
expected_sources
```

---

## 9.2. Schema Versioning

Schema nên có version.

Lý do:

```text id="wc2kdw"
- Import Excel có thể thêm cột mới
- Reuse dataset từ project khác có thể khác schema
- Test run cũ cần biết schema lúc chạy
- Evaluator mapping phụ thuộc vào tên cột
```

Mỗi test run phải lưu:

```text id="j1k51p"
dataset_id
dataset_version
schema_id
schema_version
```

---

# 10. Dataset Design

## 10.1. Dataset Scope

MVP hỗ trợ ba scope:

```text id="6epk95"
PROJECT
WORKSPACE
PERSONAL
```

### Project Dataset

Dataset thuộc project hiện tại.

### Workspace Dataset

Dataset dùng chung trong workspace.

### Personal Dataset

Dataset thuộc user, có thể tái sử dụng ở workspace khác nếu user đó có quyền trong project đích.

---

## 10.2. Dataset Row Mapping sang Promptfoo

Mỗi dataset row sẽ thành một Promptfoo test case.

Ví dụ dataset row:

```json
{
  "question": "VF 8 có mấy phiên bản?",
  "expected": "VF 8 có các phiên bản...",
  "context": "Thông tin sản phẩm VF 8",
  "category": "product_info",
  "difficulty": "easy"
}
```

Runner convert thành:

```yaml
tests:
  - vars:
      question: "VF 8 có mấy phiên bản?"
      expected: "VF 8 có các phiên bản..."
      context: "Thông tin sản phẩm VF 8"
    metadata:
      category: "product_info"
      difficulty: "easy"
```

Promptfoo hỗ trợ test case có `vars` và `metadata`, vì vậy dataset schema có thể map tự nhiên sang config chạy eval.

---

## 10.3. Import Dataset từ Excel

Flow import Excel:

```text id="0ojams"
1. QC upload Excel.
2. Frontend đọc sheets/cột hoặc backend đọc file.
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

Import options:

```text id="n2c9b9"
- Select sheet
- Preview rows
- Auto-detect column mapping
- Manual mapping
- Ignore column
- Add new schema column
- Validate required columns
- Validate duplicate column names
```

---

# 11. Import Dataset từ Project khác

Ý của bạn hợp lý. Nên thiết kế UX theo hướng **wizard/modal 3 bước**, không phải chỉ là dropdown đơn giản.

## 11.1. Entry Point

Trong màn hình Dataset của Project hiện tại có nút:

```text id="pyn5kq"
Import Dataset
```

Khi bấm, mở modal với các tab:

```text id="mw9xwl"
- From Excel
- From another project
- From workspace dataset
- From my datasets
```

MVP có thể gom lại thành:

```text id="c1n47y"
Import Dataset
  ├── Upload Excel
  └── Reuse Existing Dataset
```

Trong `Reuse Existing Dataset`, user chọn nguồn.

---

## 11.2. Flow: Import từ Project khác

```text id="7xrv65"
1. User vào Project A.
2. User mở tab Datasets.
3. User click "Import Dataset".
4. User chọn "From another project".
5. Modal hiển thị search bar + list project user có quyền xem.
6. User search/chọn Project B.
7. Modal hiển thị list datasets trong Project B.
8. User chọn Dataset X.
9. UI hiển thị preview dataset.
10. Hệ thống kiểm tra schema compatibility.
11. Nếu schema giống → cho import luôn.
12. Nếu schema khác → mở mapping step.
13. User mapping cột source dataset sang schema project hiện tại.
14. User chọn clone dataset.
15. Backend tạo dataset mới trong Project A.
16. Dataset mới lưu source_dataset_id để trace nguồn gốc.
```

---

## 11.3. Modal UI đề xuất

### Step 1 — Select source project

```text id="x84p9b"
Search project...
--------------------------------
[Project] Banking Bot QC
[Project] Sales Assistant QC
[Project] Customer Support Bot
[Project] HR Internal Bot
```

Filter nên có:

```text id="wxu1eh"
- Current workspace
- My accessible projects
- Recently used
```

MVP nên ưu tiên hiển thị project trong cùng workspace trước.

---

### Step 2 — Select dataset

Sau khi chọn project:

```text id="a1khpe"
Project: Banking Bot QC

Search dataset...
--------------------------------
[Dataset] Banking FAQ - Smoke Test       120 rows
[Dataset] Banking FAQ - Regression       850 rows
[Dataset] Edge Cases - Vietnamese        300 rows
```

Hiển thị metadata:

```text id="9eqru4"
- Dataset name
- Row count
- Schema version
- Created by
- Updated at
- Tags/category nếu có
```

---

### Step 3 — Preview & mapping

```text id="onqe8j"
Source dataset: Banking FAQ - Smoke Test
Target project: VinFast Sales Bot QC

Schema compatibility:
- question → question
- expected_answer → expected
- category → category
- intent → Add as new column / Ignore / Map
```

User có thể chọn:

```text id="3hg75m"
- Clone all rows
- Clone selected rows
- Import with mapping
- Cancel
```

MVP chỉ cần `Clone all rows`.

---

## 11.4. Clone hay Reference?

MVP nên dùng **clone**, không dùng live reference.

Lý do:

```text id="j17y44"
- An toàn
- Dễ hiểu với QC
- Không bị ảnh hưởng nếu dataset gốc thay đổi
- Dễ lưu schema version
- Dễ audit test run
```

Dataset clone nên lưu:

```text id="ewbf4a"
source_dataset_id
source_project_id
source_workspace_id
source_schema_version
import_mapping_json
imported_by
imported_at
```

Sau này có thể thêm `reference mode`.

---

# 12. Chatbot Target Design

## 12.1. MVP chỉ hỗ trợ cURL

MVP chỉ hỗ trợ cấu hình chatbot target bằng cURL.

Flow:

```text id="v9pzf7"
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
8. Backend yêu cầu runner hoặc test-call service validate request.
9. Hệ thống lưu example request và example response.
```

---

## 12.2. Tại sao backend không nên shell-execute raw cURL

Backend nên lưu `rawCurl` để audit/trace, nhưng không nên chạy trực tiếp raw cURL bằng shell.

Thay vào đó, backend/runner nên dùng `parsedRequestConfig`.

Lý do:

```text id="6ao61o"
- Tránh command injection
- Dễ mask secret
- Dễ validate URL/header/body
- Dễ convert sang Promptfoo HTTP provider
- Không cần Java backend parse cURL lại
```

---

## 12.3. Chatbot Target Entity

```text id="mevzcz"
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

## 12.4. Response Extraction

Khi validate chatbot target, hệ thống lưu example response.

QC chọn field chính để chấm.

Ví dụ response:

```json
{
  "status": "success",
  "data": {
    "answer": "VF 8 có các phiên bản Eco và Plus.",
    "intent": "product_info",
    "confidence": 0.92
  },
  "metadata": {
    "latency": 1200
  }
}
```

QC chọn:

```text id="6sqyd2"
Main answer path: $.data.answer
Intent path: $.data.intent
Confidence path: $.data.confidence
```

Runner convert `Main answer path` thành `transformResponse`.

Ví dụ:

```yaml
transformResponse: "json.data.answer"
```

Promptfoo HTTP provider hỗ trợ `transformResponse` để extract một phần response, ví dụ lấy `json.choices[0].message.content`, hoặc xử lý text response.

---

## 12.5. Chấm cả raw response hoặc field trong response

Có 2 mode cần hỗ trợ:

### Mode A — Main Answer Mode

Dùng khi chỉ chấm câu trả lời chính.

```text id="y8s4sh"
transformResponse = json.data.answer
assertions chạy trên answer string
```

Phù hợp cho LLM Judge.

### Mode B — Structured Response Mode

Dùng khi muốn chấm nhiều field trong response.

```text id="hw2eo3"
transformResponse trả về object đã normalize:
{
  "answer": json.data.answer,
  "intent": json.data.intent,
  "confidence": json.data.confidence,
  "raw": json
}
```

Sau đó rule-based evaluator có thể dùng JavaScript assertion để check field.

## Promptfoo hỗ trợ JavaScript assertion và assertion function có thể nhận `output` cùng context như `vars`, provider response, test config. Điều này phù hợp để build rule phức tạp hơn nếu các assertion built-in chưa đủ.

# 13. Evaluator Design

## 13.1. Evaluator Types

MVP hỗ trợ:

```text id="6h5qok"
RULE_BASED
LLM_JUDGE
MIXED
```

### RULE_BASED

Dùng deterministic assertions.

Promptfoo hỗ trợ nhiều assertion như `equals`, `contains`, `regex`, `is-json`, `contains-json`, JavaScript/Python assertion, và các assertion có weight/threshold.

### LLM_JUDGE

Dùng model-graded assertions như `llm-rubric`, `factuality`, `answer-relevance`, `g-eval`, v.v. Promptfoo docs mô tả `llm-rubric` là grader dùng LLM để đánh giá output theo tiêu chí/rubric do mình cung cấp.

### MIXED

Kết hợp rule-based và LLM Judge.

Ví dụ:

```text id="n5v00d"
- HTTP status phải success
- confidence >= 0.7
- answer không empty
- LLM Judge score >= 0.7
```

---

## 13.2. Rule-based Evaluator MVP

Rule UI nên là form builder.

Mỗi rule gồm:

```text id="75pwcu"
field_source
operator
expected_value
weight
required
```

Ví dụ:

```text id="jm312n"
response.status equals "success"
response.data.intent equals $dataset.expected_intent
response.data.answer contains $dataset.expected
response.data.confidence greater_than_or_equal 0.7
latency_ms less_than 3000
```

Operator MVP:

```text id="vvfiy8"
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

Runner mapping:

| UI operator           | Promptfoo mapping      |
| --------------------- | ---------------------- |
| equals                | `equals`               |
| contains              | `contains`             |
| regex                 | `regex`                |
| is_json               | `is-json`              |
| contains_json         | `contains-json`        |
| field/path comparison | `javascript` assertion |
| greater/less than     | `javascript` assertion |
| complex JSON path     | `javascript` assertion |

---

## 13.3. LLM Judge Evaluator MVP

LLM Judge config gồm:

```text id="q16edt"
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

```text id="bb943g"
Chấm câu trả lời theo thang điểm 0-10.
Pass nếu score >= 7.

Tiêu chí:
1. Đúng ý chính so với expected.
2. Không hallucinate.
3. Không bỏ sót thông tin quan trọng.
4. Trả lời rõ ràng, phù hợp ngữ cảnh.
```

Runner mapping sang Promptfoo:

```yaml
assert:
  - type: llm-rubric
    value: |
      Chấm câu trả lời theo tiêu chí:
      - Đúng ý so với expected: {{expected}}
      - Không hallucinate
      - Không bỏ sót thông tin quan trọng
      - Phù hợp context: {{context}}
    threshold: 0.7
    provider: openai:gpt-4o-mini
```

Model-graded assertions trong Promptfoo cho phép set `threshold`, `provider` hoặc custom rubric prompt.

---

## 13.4. Evaluator Versioning

Evaluator cần versioning.

```text id="dce77w"
Evaluator
 └── EvaluatorVersion v1
 └── EvaluatorVersion v2
```

Test run phải lưu evaluator version đã dùng.

Lý do:

```text id="urg4j3"
- Rubric thay đổi sẽ làm result thay đổi
- Rule thay đổi sẽ làm pass/fail thay đổi
- Comparison chỉ công bằng nếu dùng cùng evaluator version
```

---

# 14. Test Run Design

## 14.1. Run Type

MVP hỗ trợ:

```text id="uswvk6"
SINGLE_TARGET_RUN
COMPARISON_RUN
```

### Single Target Run

Một chatbot target chạy trên một dataset.

### Comparison Run

Một internal chatbot target chạy song song với một hoặc nhiều baseline models.

---

## 14.2. Test Run Entity

```text id="pkkmln"
id
project_id
run_type
dataset_id
dataset_version
dataset_schema_id
dataset_schema_version
evaluator_version_ids
judge_provider_id
judge_model
status
started_by
started_at
finished_at
summary_metrics_json
promptfoo_config_snapshot_json
promptfoo_result_file_path
created_at
updated_at
```

Status:

```text id="dd1w8l"
PENDING
RUNNING
COMPLETED
FAILED
CANCELLED
```

---

## 14.3. Test Run Target

```text id="g14e86"
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

Target type:

```text id="8migty"
INTERNAL_CHATBOT
LLM_BASELINE
```

---

## 14.4. Test Case Result

```text id="y7mmdq"
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

Promptfoo JSON/JSONL outputs có thể chứa `success/pass`, `score`, `response.output`, `gradingResult`, `componentResults`, latency, token usage, cost và error details; backend nên normalize các field này về bảng `test_case_results`.

---

# 15. Comparison Design

## 15.1. Mục tiêu

Comparison giúp QC biết chatbot nội bộ đang tốt hơn/kém hơn baseline model nào.

MVP hỗ trợ:

```text id="ep1qwn"
1 internal chatbot target
+ 1 hoặc nhiều LLM baseline targets
```

Promptfoo vốn có mô hình chạy cùng test set qua nhiều providers/targets và HTML report có side-by-side output comparison, nên comparison run là một use case phù hợp với Promptfoo.

---

## 15.2. Điều kiện comparison công bằng

Các target trong cùng comparison run phải dùng chung:

```text id="ubxc7g"
- Dataset
- Dataset version
- Schema version
- Evaluator version
- Judge model
- Run config
```

Chỉ khác:

```text id="eeqg1m"
- Target chatbot/model
```

---

## 15.3. Comparison Dashboard MVP

Hiển thị:

```text id="w91h09"
- Pass rate theo target
- Average score theo target
- Average latency theo target
- Failed count theo target
- Cost/token nếu có
- Bảng từng testcase theo target
- Judge reason từng target
```

Bảng:

| Testcase | Internal Bot | GPT Baseline | Gemini Baseline |
| -------- | ------------ | ------------ | --------------- |
| TC001    | Fail / 0.5   | Pass / 0.8   | Pass / 0.7      |
| TC002    | Pass / 0.9   | Pass / 0.8   | Fail / 0.4      |

---

## 15.4. Winner Selection

MVP chưa cần auto winner phức tạp.

Có thể hiển thị:

```text id="pz8ihf"
- Highest score
- Lowest latency
- Passed targets
```

Nếu muốn chọn winner bằng LLM, Promptfoo có `select-best` assertion để chọn output tốt nhất theo criterion, nhưng MVP nên để phase sau vì dễ làm UI/result phức tạp hơn.

---

# 16. Promptfoo Runner Flow

## 16.1. Backend tạo run

```text id="6x3w1l"
1. User click Run.
2. Backend check permission.
3. Backend lock dataset version.
4. Backend lock schema version.
5. Backend lock evaluator version.
6. Backend tạo test_run status PENDING.
7. Backend gửi job sang runner.
```

---

## 16.2. Runner build Promptfoo config

Runner nhận:

```json
{
  "testRunId": "...",
  "dataset": "...",
  "datasetSchema": "...",
  "targets": [],
  "evaluators": [],
  "judgeProvider": {},
  "secrets": {},
  "runConfig": {}
}
```

Runner sinh:

```text id="42fmcq"
promptfooconfig.yaml hoặc in-memory config
```

Gồm:

```text id="pc41wi"
- description
- prompts
- providers
- tests
- defaultTest/assert
- outputPath
```

Promptfoo cho phép import providers/tests từ files, dùng `defaultTest` để áp dụng assertion mặc định cho toàn bộ tests, và dùng test case riêng có `vars/assert`.

---

## 16.3. Runner execute

Runner chạy:

```text id="zjsbur"
promptfoo eval --output results.jsonl --output results.json
```

Hoặc dùng Node API.

Với dataset lớn, JSONL phù hợp hơn vì mỗi line là một result row. Promptfoo docs cũng gợi ý JSONL hữu ích cho eval lớn hoặc khi JSON export gặp vấn đề bộ nhớ.

---

## 16.4. Runner normalize result

Runner parse Promptfoo output rồi gửi backend:

```json
{
  "testRunId": "...",
  "status": "COMPLETED",
  "summary": {
    "total": 100,
    "passed": 82,
    "failed": 18,
    "averageScore": 0.78
  },
  "results": []
}
```

Backend lưu:

```text id="r90h5e"
- summary_metrics_json
- test_case_results
- raw promptfoo result file path
- normalized result
```

---

# 17. Dashboard MVP

## 17.1. Single Target Dashboard

Hiển thị:

```text id="n39w2e"
- Total test cases
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

```text id="ae317m"
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

## 17.2. Testcase Detail

Mỗi testcase detail cần có:

```text id="5t1oc5"
- Dataset row
- Rendered prompt
- Resolved request
- Raw response
- Extracted output
- Rule assertion results
- LLM judge result
- Error logs
```

---

## 17.3. Comparison Dashboard

Hiển thị:

```text id="b2cwin"
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

# 18. Main User Flows

## 18.1. Admin setup workspace

```text id="h4o4gb"
1. Admin tạo Team Workspace.
2. Admin invite QC members.
3. Admin tạo Workspace Secret.
4. Admin tạo Workspace Provider.
5. Admin chọn default model cho LLM Judge.
6. QC vào workspace và dùng provider đã setup.
7. Nếu workspace key invalid/quota exceeded, QC có thể dùng Personal Provider.
```

---

## 18.2. QC tạo project test

```text id="mtibry"
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
13. Runner chạy Promptfoo.
14. Backend lưu result.
15. UI hiển thị dashboard.
```

---

## 18.3. QC import dataset từ project khác

```text id="r3mndw"
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
11. Backend tạo dataset mới trong project hiện tại.
12. Dataset mới lưu source metadata.
```

---

## 18.4. QC chạy comparison

```text id="ofx6wb"
1. QC chọn dataset.
2. QC chọn internal chatbot target.
3. QC chọn baseline providers/models.
4. QC chọn evaluator.
5. QC chọn LLM Judge.
6. QC click Run comparison.
7. Runner chạy cùng tests trên nhiều targets.
8. Backend lưu result theo target.
9. UI hiển thị comparison dashboard.
```

---

# 19. MVP Scope v0.2

## 19.1. In Scope

```text id="4ko6dy"
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
- Promptfoo runner integration
- Single target run
- Comparison run
- JSON/JSONL result ingestion
- Single target dashboard
- Comparison dashboard
```

---

## 19.2. Out of Scope

```text id="9288vf"
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
```

Lưu ý: Promptfoo có nhiều tính năng nâng cao như dataset generation, red teaming, prompt optimization, retry/resume, cache, MCP, share result. MVP chưa nên ôm hết, chỉ dùng phần evaluation core trước.

---

# 20. Domain Entities v0.2

```text id="budn63"
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
PromptfooRunArtifact
```

---

# 21. Database Tables v0.2

## workspaces

```text id="71xeya"
id
name
type
created_by
created_at
updated_at
deleted_at
```

## workspace_members

```text id="gjy1y3"
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

```text id="l0z5jk"
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

```text id="5qd90k"
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

```text id="78toj6"
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

```text id="u5p8qz"
id
project_id
version
name
created_by
created_at
```

## dataset_schema_columns

```text id="jss94h"
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

```text id="4gsuc8"
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

```text id="2ausra"
id
dataset_id
row_index
data_json
metadata_json
created_at
updated_at
```

## dataset_import_jobs

```text id="qu5k1z"
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

```text id="tub4c3"
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

```text id="z92cl8"
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

```text id="pjbljl"
id
evaluator_id
version
config_json
created_by
created_at
```

## test_runs

```text id="qknqjs"
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
summary_metrics_json
promptfoo_config_snapshot_json
started_by
started_at
finished_at
created_at
updated_at
```

## test_run_targets

```text id="10lq20"
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

```text id="59ybfo"
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

## promptfoo_run_artifacts

```text id="cemyuq"
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

# 22. Product Decisions v0.2

```text id="ja68c7"
1. Promptfoo là execution engine chính.
2. Backend không tự build eval engine, chỉ orchestrate.
3. Runner nên là Node.js service riêng.
4. UI dùng $dataset.column để thân thiện với QC.
5. Runner convert $dataset.column sang Promptfoo vars/template.
6. Dataset row map sang Promptfoo tests.vars.
7. Chatbot target map sang Promptfoo HTTP provider.
8. Response extraction map sang transformResponse.
9. Rule-based evaluator map sang Promptfoo assertions.
10. Rule phức tạp map sang JavaScript assertion.
11. LLM Judge map sang llm-rubric/model-graded assertions.
12. Comparison run map sang multi-provider/multi-target eval.
13. Result nên ingest từ JSON/JSONL output.
14. Dataset import từ project khác nên dùng search project → select project → select dataset → preview → mapping → clone.
15. MVP dùng clone dataset, chưa dùng live reference.
```

---

# 23. Open Questions cho v0.3

```text id="in55c8"
1. Runner sẽ dùng Promptfoo CLI hay Node API là chính?
2. Test connection chatbot target sẽ chạy qua runner hay backend HTTP client?
3. Có cần lưu raw response đầy đủ vĩnh viễn không?
4. Có cần mask field nhạy cảm trong raw response trước khi lưu không?
5. Rule builder có cho QC viết custom JavaScript không, hay chỉ admin?
6. Workspace Provider có được tất cả Member dùng không, hay cần bật/tắt theo project?
7. Member có được xóa project do người khác tạo không?
8. Comparison run có cần auto winner trong MVP không?
9. Dataset clone có cho clone selected rows không, hay luôn clone all rows?
10. Có cần hỗ trợ multi-turn chatbot ngay MVP không?
```

---

# 24. MVP Success Criteria

MVP đạt yêu cầu nếu QC có thể:

```text id="8llhqi"
1. Tạo project trong workspace.
2. Dùng provider/key do Admin setup hoặc key cá nhân.
3. Paste cURL chatbot target.
4. Mapping question/context vào request.
5. Lưu example response và chọn answer path.
6. Import Excel dataset.
7. Import dataset từ project khác bằng flow search project → select dataset.
8. Cấu hình rule-based evaluator.
9. Cấu hình LLM Judge.
10. Chạy single target run.
11. Chạy comparison run với baseline model.
12. Xem dashboard và testcase detail.
13. Debug được request/response/evaluator reason khi testcase fail.
```

