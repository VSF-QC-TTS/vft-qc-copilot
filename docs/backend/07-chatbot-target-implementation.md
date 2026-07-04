# VinFast QC Copilot - Chatbot Target Implementation

Version: `v0.2`  
Status: Spring Boot 4.0.7-first Implementation Spec  
Applies to: `apps/api`

---

## 1. Purpose

This document explains Chatbot Target configuration, cURL inspection, HTTP test connection and response extraction implementation.

---

## 2. Design Principles

1. Backend never shell-executes raw cURL.
2. Backend persists only sanitized raw cURL.
3. Plain Authorization/API key/token/cookie values are not stored in target config.
4. Secrets are stored as Secret entities and referenced by ID.
5. Target config is structured, validated and replayable.
6. UI variable syntax is `$dataset.<columnName>`.
7. Backend stores domain variable syntax, not Promptfoo `{{column}}` syntax.
8. Runner converts target config to Promptfoo-compatible runtime config.
9. Test request/response examples are masked before persistence.
10. ACTIVE target requires request config and response extraction config.
11. Use Spring `RestClient` for synchronous HTTP target calls.
12. Do not add WebFlux/WebClient unless dependency and architecture explicitly change.

---

## 3. Package Layout

```text
target
  controller/ChatbotTargetController.java
  controller/ChatbotTargetExampleController.java
  controller/ChatbotTargetCurlController.java
  controller/ChatbotTargetConnectionController.java
  controller/ChatbotTargetExtractionController.java
  dto/request/InspectCurlRequest.java
  dto/request/CreateChatbotTargetRequest.java
  dto/request/UpdateChatbotTargetRequest.java
  dto/request/TestConnectionRequest.java
  dto/request/ResponseExtractionConfigRequest.java
  dto/response/ChatbotTargetSummaryResponse.java
  dto/response/ChatbotTargetDetailResponse.java
  dto/response/ChatbotTargetExampleResponse.java
  dto/response/InspectCurlResponse.java
  dto/response/TestConnectionResponse.java
  dto/response/ResponseExtractionConfigResponse.java
  entity/ChatbotTarget.java
  entity/ChatbotTargetExample.java
  enums/ChatbotTargetStatus.java
  enums/HttpBodyType.java
  enums/SecretBindingLocation.java
  enums/DatasetTargetLocation.java
  repository/ChatbotTargetRepository.java
  repository/ChatbotTargetExampleRepository.java
  mapper/ChatbotTargetMapper.java
  mapper/ChatbotTargetExampleMapper.java
  service/ChatbotTargetService.java
  service/CurlInspectionService.java
  service/ChatbotTargetTestService.java
  service/ResponseExtractionService.java
  service/ChatbotTargetLookupService.java
  service/ChatbotTargetReadinessService.java
  validator/ChatbotTargetValidator.java
  validator/DatasetRequestMappingValidator.java
  validator/ResponseExtractionValidator.java
  validator/SecretBindingValidator.java
  client/HttpTargetClient.java
  client/RestClientHttpTargetClient.java
  model/ParsedHttpRequestConfig.java
  model/SecretBinding.java
  model/DatasetRequestMapping.java
  model/ResponseExtractionConfig.java
```

---

## 4. Entity Rules

### ChatbotTarget

Fields:

```text
id
projectId
name
nameLookup
description
rawCurlSanitized
rawCurlHash
method
url
headersJson
queryParamsJson
bodyJson
authConfigJson
datasetMappingJson
latestExampleId
responseExtractionConfigJson
status
createdBy
createdAt
updatedAt
deletedAt
```

Rules:

1. JSONB fields use Hibernate 7 JSON mapping.
2. `rawCurlHash` is hash of sanitized canonical cURL.
3. Do not store secret values in JSONB.
4. Parse JSONB into typed internal models before validation/execution.
5. Deleted target cannot be selected for new run.

### ChatbotTargetExample

Fields:

```text
id
chatbotTargetId
requestJson
responseStatusCode
responseHeadersJson
responseBodyJson
extractedOutputJson
latencyMs
errorMessage
testedBy
createdAt
```

Rules:

1. One row per test attempt.
2. Save failed attempts too.
3. All request/response/error fields are sanitized before persistence.
4. `responseStatusCode` may be null on timeout/network error.

---

## 5. cURL Inspection

Flow:

1. Receive raw cURL text.
2. Parse cURL safely without executing it.
3. Sanitize headers/body/query.
4. Detect likely secret values.
5. Suggest Secret bindings.
6. Return structured preview.

Rules:

1. Do not persist raw input from inspect endpoint unless create endpoint is called.
2. Never execute cURL command.
3. Do not support shell substitution, file upload or command pipes in MVP.
4. Reject extremely large cURL input.
5. Persist only sanitized cURL when creating/updating target.

---

## 6. URL and SSRF Protection

Target URL validation must reject:

1. Non-HTTP/HTTPS schemes.
2. Loopback/localhost.
3. Private IPv4 ranges unless explicitly allowed by deployment policy.
4. Link-local addresses.
5. Cloud metadata service addresses.
6. DNS rebinding/redirect to blocked addresses.
7. User-info URLs like `https://user:pass@example.com`.

If internal company endpoints are private IPs and must be allowed, introduce an explicit allowlist config. Do not allow all private networks by default in public/SaaS deployments.

---

## 7. HTTP Client

Use Spring `RestClient`.

Recommended boundary:

```java
public interface HttpTargetClient {
  TargetHttpResult execute(TargetHttpRequest request, Duration timeout);
}
```

Implementation rules:

1. Build request from structured config.
2. Resolve secrets in memory only.
3. Apply dataset variables from row values.
4. Enforce timeout.
5. Limit response body size.
6. Sanitize before returning data to persistence layer.
7. Do not log full request/response.

---

## 8. Response Extraction

Response extraction config is selected from masked example response.

Supported MVP extraction:

1. JSONPath-like path for JSON response.
2. Direct body text if response is plain text.
3. Multiple extracted fields for evaluator mapping.

Rules:

1. Validate path against latest masked example when possible.
2. Extraction config stores field name, source path, type and required flag.
3. TestRun uses locked extraction config snapshot.
4. Missing required extraction during run creates testcase ERROR/JUDGE_ERROR according to evaluator stage.

---

## 9. Status Rules

Recommended statuses:

```text
DRAFT
READY
ACTIVE
DISABLED
DELETED
```

Rules:

1. DRAFT may lack extraction config.
2. READY has valid request and extraction config.
3. ACTIVE can be selected for runs.
4. DISABLED cannot be selected for new runs.
5. DELETED is soft-deleted.

---

## 10. Tests

Required tests:

1. cURL with Authorization stores only masked value.
2. Secret binding is required for sensitive headers.
3. Raw cURL is never executed.
4. SSRF blocked URL is rejected.
5. Test connection saves sanitized example on success/failure.
6. ACTIVE target requires extraction config.
7. Deleted target cannot be selected for TestRun.
