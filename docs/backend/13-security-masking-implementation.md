# VinFast QC Copilot - Security & Masking Implementation

Version: `v0.1`  
Status: Backend Implementation Spec Draft  
Applies to: `apps/api` Spring Boot backend  
Aligned with: PRD v0.7, ERD v0.5, API Contracts `00` to `10`

---

## 1. Purpose

This document defines how the Spring Boot backend handles sensitive data, masking, sanitization, logging safety and artifact exposure.

It is written for AI coding agents and backend developers so generated code does not accidentally persist, log or expose secrets.

This file covers:

```text
1. Sensitive data classification.
2. Shared masking package and components.
3. Raw cURL sanitization.
4. HTTP request/response sanitization.
5. Provider test log sanitization.
6. TestRun result and artifact sanitization.
7. Promptfoo config/result/report/log masking rules.
8. Log safety and MDC rules.
9. Hashing and audit metadata.
10. Error handling and testing checklist.
```

This file does not define cryptographic implementation details for a production KMS. It defines the application-level contract that backend code must follow.

---

## 2. Non-Negotiable Security Rules

These rules override convenience, debugging needs and UI requests.

```text
1. Never return plaintext secret values after create/rotate/runtime-only flows.
2. Never persist unsanitized raw cURL if it contains Authorization, API key, cookie, token or secret material.
3. Never write unsanitized Promptfoo config/result/report/log artifacts.
4. Never log plaintext secrets, runtime secret bundles, Authorization headers, cookies or API keys.
5. Never include secret values in ProblemDetail.detail, validation errors or exception messages.
6. Never expose artifact content when promptfoo_run_artifacts.sanitized=false.
7. Never hide masking inside random DTO mapper code.
8. Never implement one-off masking regexes inside controllers/services.
9. Always use shared masking components from common/masking.
10. Always store audit hashes only over canonical sanitized snapshots/artifacts unless the specific hash is explicitly defined as internal-only.
```

AI coding agents must treat any violation of these rules as a blocker.

---

## 3. Sensitive Data Classification

### 3.1. Secret material

Secret material includes:

```text
1. Plain API keys.
2. Bearer tokens.
3. Basic auth credentials.
4. OAuth access tokens.
5. OAuth refresh tokens.
6. Runner service tokens.
7. User refresh tokens.
8. Cookies.
9. Session IDs.
10. Passwords.
11. Private keys.
12. Webhook secrets.
13. Runtime secret bundles.
```

Secret material may only exist in:

```text
1. Request memory during create/rotate/login/runtime resolution.
2. Encryption/decryption service memory.
3. Auth/token validation memory.
4. Runner runtime secret response body for authenticated runner internal API.
```

Secret material must not exist in:

```text
1. Public API responses.
2. Normal application logs.
3. ProblemDetail responses.
4. Sanitized snapshots.
5. Promptfoo artifact files.
6. Redis command/event payloads.
7. SSE payloads.
```

---

### 3.2. Sensitive transport fields

Field names are sensitive if they match or contain these tokens after normalization:

```text
authorization
auth
api-key
apikey
x-api-key
key
token
access-token
refresh-token
cookie
set-cookie
password
passwd
secret
client-secret
private-key
session
sessionid
jwt
bearer
credential
```

Normalization rule:

```text
1. Convert to lowercase.
2. Remove spaces, underscores and hyphens for broad matching.
3. Keep exact header names for canonical output when possible.
```

Examples:

```text
Authorization -> sensitive
X-API-Key -> sensitive
api_key -> sensitive
refreshToken -> sensitive
clientSecret -> sensitive
session_id -> sensitive
```

---

### 3.3. Potentially sensitive payload content

Some payload values may be sensitive even if their field name is neutral.

The masker should detect common token-like values by pattern only as a second layer.

Examples:

```text
Bearer eyJhbGciOi...
sk-...
ghp_...
glpat-...
xoxb-...
AKIA...
-----BEGIN PRIVATE KEY-----
```

Rules:

```text
1. Field-name masking is primary.
2. Pattern masking is defense-in-depth.
3. Pattern masking must prefer false positives over false negatives.
4. Pattern masking must not make business logic depend on masked values.
```

---

## 4. Package Layout

Recommended package:

```text
vn.vinfast.vfqc.api.common.masking
  VfqcSensitiveDataMasker.java
  SensitiveFieldDetector.java
  SensitiveValueDetector.java
  MaskingPolicy.java
  MaskingContext.java
  MaskingTarget.java
  MaskedValue.java
  SanitizationResult.java
  SanitizationFinding.java
  JsonSanitizer.java
  HeaderSanitizer.java
  CurlSanitizer.java
  ArtifactSanitizer.java
  FormulaInjectionSanitizer.java
```

Responsibilities:

```text
1. Centralize all sensitive field detection.
2. Centralize all value masking behavior.
3. Return metadata about whether masking happened.
4. Support JSON, text, header, cURL and artifact sanitization.
5. Provide safe default masking for unknown structures.
```

Forbidden:

```text
1. target.service.impl.ChatbotTargetServiceImpl implements its own secret regex.
2. provider.service.impl.LlmProviderTestServiceImpl manually masks headers.
3. exportjob.service.impl.ExportJobServiceImpl manually strips formulas without shared sanitizer.
4. runner.service.impl.RunnerEventIngestionServiceImpl persists event payload before sanitization.
```

---

## 5. Core Component Contracts

### 5.1. VfqcSensitiveDataMasker

Recommended interface:

```java
package vn.vinfast.vfqc.api.common.masking;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface VfqcSensitiveDataMasker {

  /**
   * Sanitizes an arbitrary JSON node by masking sensitive keys and token-like values.
   *
   * @param input source JSON node; may be null
   * @param context masking context describing the caller and target data type
   * @return sanitization result containing sanitized JSON and masking findings
   */
  SanitizationResult<JsonNode> sanitizeJson(JsonNode input, MaskingContext context);

  /**
   * Sanitizes HTTP headers by masking sensitive header values while preserving non-sensitive headers.
   *
   * @param headers source headers keyed by original header name
   * @param context masking context describing the caller and target data type
   * @return sanitization result containing sanitized headers and masking findings
   */
  SanitizationResult<Map<String, String>> sanitizeHeaders(
      Map<String, String> headers,
      MaskingContext context
  );

  /**
   * Sanitizes raw cURL text before persistence or display.
   *
   * @param rawCurl raw user-provided cURL text
   * @param context masking context describing the caller and target data type
   * @return sanitization result containing sanitized cURL and masking findings
   */
  SanitizationResult<String> sanitizeCurl(String rawCurl, MaskingContext context);

  /**
   * Sanitizes free-form text such as logs, error messages or artifact fragments.
   *
   * @param text source text; may be null
   * @param context masking context describing the caller and target data type
   * @return sanitization result containing sanitized text and masking findings
   */
  SanitizationResult<String> sanitizeText(String text, MaskingContext context);
}
```

Implementation rules:

```text
1. Use @Slf4j in implementation.
2. Log only counts/types of findings, never source sensitive values.
3. Keep methods deterministic.
4. Keep methods side-effect free.
5. Do not access database from the masker.
6. Do not throw on unknown JSON shape; sanitize best-effort and return findings.
```

---

### 5.2. SanitizationResult

Recommended record:

```java
package vn.vinfast.vfqc.api.common.masking;

import java.util.List;

public record SanitizationResult<T>(
    T value,
    boolean sanitized,
    int maskedFieldCount,
    int maskedValueCount,
    List<SanitizationFinding> findings
) {
}
```

Rules:

```text
1. sanitized=true means the result is safe for normal persistence/exposure.
2. findings must not contain original secret values.
3. findings may include safe metadata such as path, fieldName, reason and maskType.
4. Callers should persist sanitized value, not original value.
```

---

### 5.3. SanitizationFinding

Recommended record:

```java
package vn.vinfast.vfqc.api.common.masking;

public record SanitizationFinding(
    String path,
    String fieldName,
    String reason,
    String maskType
) {
}
```

Safe example:

```json
{
  "path": "$.headers.Authorization",
  "fieldName": "Authorization",
  "reason": "SENSITIVE_FIELD_NAME",
  "maskType": "FULL_MASK"
}
```

Forbidden example:

```json
{
  "path": "$.headers.Authorization",
  "originalValue": "Bearer real-token"
}
```

---

### 5.4. MaskingContext

Recommended record:

```java
package vn.vinfast.vfqc.api.common.masking;

import java.util.UUID;

public record MaskingContext(
    MaskingTarget target,
    String module,
    UUID projectId,
    UUID testRunId,
    String traceId
) {
  public static MaskingContext forModule(MaskingTarget target, String module) {
    return new MaskingContext(target, module, null, null, null);
  }
}
```

Recommended `MaskingTarget` values:

```text
RAW_CURL
CHATBOT_TARGET_CONFIG
CHATBOT_TARGET_EXAMPLE
PROVIDER_CONFIG
PROVIDER_TEST_LOG
TEST_RUN_SNAPSHOT
TEST_CASE_RESULT
RUNNER_EVENT
PROMPTFOO_CONFIG_ARTIFACT
PROMPTFOO_RESULT_ARTIFACT
PROMPTFOO_HTML_REPORT
PROMPTFOO_LOG_ARTIFACT
EXPORT_FILE
ERROR_MESSAGE
APPLICATION_LOG
```

---

## 6. Masking Output Rules

Use consistent masking markers.

Recommended markers:

```text
****MASKED****
****REDACTED****
```

Preferred rule:

```text
1. Use ****MASKED**** for values hidden because they are sensitive.
2. Use ****REDACTED**** for large omitted values or intentionally hidden debug content.
```

Examples:

```json
{
  "Authorization": "Bearer ****MASKED****",
  "x-api-key": "****MASKED****"
}
```

```json
{
  "rawResponseBody": "****REDACTED****"
}
```

Rules:

```text
1. Do not expose secret prefix/suffix by default in artifacts.
2. maskedPreview for secrets may show a very small safe suffix/prefix only in SecretResponse.
3. Do not preserve token length in masks unless explicitly required.
4. Do not generate a different random mask for the same value.
5. Do not log the masked and original value side-by-side.
```

---

## 7. Raw cURL Sanitization

### 7.1. Storage rule

For chatbot target setup:

```text
1. raw_curl_sanitized stores only sanitized cURL.
2. raw_curl_hash stores hash of original raw cURL or canonical sanitized cURL depending on security policy.
3. Plain raw cURL must not be stored if it contains Authorization/API key/token/cookie.
4. Secret values extracted from cURL must become Secret entities or SecretBinding references.
```

Recommended default:

```text
raw_curl_hash = SHA-256(canonical sanitized cURL)
```

Rationale:

```text
1. Avoid keeping a derived verifier of real secret material unless explicitly required.
2. Still support audit and duplicate detection at sanitized config level.
```

---

### 7.2. Header examples

Input:

```bash
curl 'https://chatbot.example.vn/api/chat' \
  -H 'Authorization: Bearer abc.real.token' \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: vf-secret-key' \
  --data '{"question":"hello"}'
```

Sanitized output:

```bash
curl 'https://chatbot.example.vn/api/chat' \
  -H 'Authorization: Bearer ****MASKED****' \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: ****MASKED****' \
  --data '{"question":"hello"}'
```

Rules:

```text
1. Preserve method, URL and non-sensitive structure.
2. Mask sensitive header/query/body values.
3. Do not remove headers entirely if UI needs to show that auth is configured.
4. Do not shell-execute cURL during sanitization.
```

---

## 8. JSON Sanitization

### 8.1. Object rule

When sanitizing JSON object fields:

```text
1. If key is sensitive, replace value with mask.
2. If value is object/array under a sensitive key, replace the whole subtree with mask.
3. If key is not sensitive, recursively sanitize child values.
4. If scalar value looks token-like, replace value with mask.
```

Input:

```json
{
  "headers": {
    "Authorization": "Bearer real-token",
    "Content-Type": "application/json"
  },
  "body": {
    "question": "hello",
    "apiKey": "real-key",
    "metadata": {
      "sessionId": "abc123"
    }
  }
}
```

Output:

```json
{
  "headers": {
    "Authorization": "Bearer ****MASKED****",
    "Content-Type": "application/json"
  },
  "body": {
    "question": "hello",
    "apiKey": "****MASKED****",
    "metadata": {
      "sessionId": "****MASKED****"
    }
  }
}
```

---

### 8.2. Array rule

Arrays must be sanitized recursively.

Input:

```json
[
  {"name":"Authorization","value":"Bearer real-token"},
  {"name":"Content-Type","value":"application/json"}
]
```

Output:

```json
[
  {"name":"Authorization","value":"Bearer ****MASKED****"},
  {"name":"Content-Type","value":"application/json"}
]
```

Special rule:

```text
When an object uses name/value pairs, detect sensitivity from the name field and mask the value field.
```

---

## 9. Module-Specific Masking Rules

### 9.1. Auth module

Must never log:

```text
1. Password.
2. Password hash.
3. Access token.
4. Refresh token.
5. Refresh token hash if avoidable.
6. Cookie value.
```

Safe logs:

```text
Login succeeded for userId={}
Login failed for emailHash={} reason={}
Refresh token rotation succeeded for userId={} familyId={}
Logout completed for userId={}
```

Forbidden logs:

```text
Login request email={} password={}
Refresh token={}
Authorization={}
```

---

### 9.2. Secret module

Secret create/rotate rules:

```text
1. Accept plaintext secretValue only in request DTO.
2. Validate length and format where appropriate.
3. Encrypt before persistence.
4. Store encrypted_value only.
5. Return SecretResponse with hasValue=true and maskedPreview only.
6. Do not map encrypted_value or secretValue through MapStruct response mappers.
```

MapStruct rule:

```text
SecretMapper must explicitly ignore encryptedValue.
```

Example:

```java
@Mapper(componentModel = "spring")
public interface SecretMapper {

  @Mapping(target = "hasValue", constant = "true")
  @Mapping(target = "maskedPreview", expression = "java(maskedPreview(secret))")
  SecretResponse toResponse(Secret secret);

  default String maskedPreview(Secret secret) {
    return "****";
  }
}
```

Do not add `secretValue` to response records.

---

### 9.3. Provider module

Provider config rules:

```text
1. Provider stores secret_id reference, not API key.
2. config_json must be sanitized before persistence.
3. Provider test request/response/error must be sanitized before llm_provider_test_logs insert.
4. Provider test should not keep DB transaction open while calling external provider.
5. Provider test logs must not contain plaintext secret, Authorization header or provider raw token error.
```

Provider test log fields requiring sanitization:

```text
request_json
response_headers_json
response_body_json
error_message
```

---

### 9.4. Chatbot target module

Chatbot target fields requiring sanitization before persistence:

```text
raw_curl_sanitized
headers_json
query_params_json
body_json
auth_config_json
request_json in chatbot_target_examples
response_headers_json in chatbot_target_examples
response_body_json in chatbot_target_examples
extracted_output_json in chatbot_target_examples
error_message in chatbot_target_examples
```

Rules:

```text
1. Backend must validate that secretBindings reference Secret entities.
2. Runtime secret injection is not done in normal target CRUD.
3. Test connection may resolve secret for runtime only.
4. Test connection must persist only masked request/response/example.
```

---

### 9.5. Evaluator module

Evaluator config is usually not secret-bearing, but still sanitize:

```text
1. LLM Judge input template.
2. Rubric text.
3. Custom provider config references.
4. Validation error detail.
```

Rules:

```text
1. Do not accept arbitrary custom JavaScript in MVP.
2. Do not allow rubric/config to embed API keys intentionally.
3. If a rubric includes token-like content, mask it in logs and debug output.
4. Store evaluator config only after validation.
```

---

### 9.6. TestRun module

TestRun fields requiring sanitization:

```text
domain_config_snapshot_json
promptfoo_config_snapshot_json
resolved_request_json
raw_response_json
extracted_output_json
grading_result_json
component_results_json
error_message
```

Rules:

```text
1. TestRun snapshots are compact sanitized audit snapshots.
2. Full generated Promptfoo config must go to artifact storage after masking.
3. resolved_request_json may show request shape but not secrets.
4. raw_response_json must be sanitized because chatbot output may echo secrets.
5. error_message must be sanitized before persistence.
```

---

### 9.7. Runner internal module

Runner internal APIs may return runtime secrets only to authenticated runner service.

Rules:

```text
1. GET /internal/runner/test-runs/{testRunId}/config returns secret references only.
2. POST /internal/runner/test-runs/{testRunId}/runtime-secrets returns runtime secret material.
3. Runtime secret response must not be persisted as a runner event.
4. Runtime secret response must not be logged.
5. Runner service token must never be stored in runner_events.
6. Runner event payloads must already be sanitized or sanitized before persistence.
```

---

### 9.8. SSE module

SSE payloads must never contain:

```text
1. Runtime secrets.
2. Full raw request.
3. Full raw response.
4. Full Promptfoo artifacts.
5. Authorization headers.
6. Cookies.
```

Allowed SSE payloads:

```text
1. Progress counts.
2. Testcase status.
3. Score/pass flag.
4. Target id/display name.
5. Dataset row id/rowIndex.
6. Compact sanitized error code/message.
```

SSE broadcasts must use persisted `runner_events` rows, not raw Redis event bodies.

---

### 9.9. Export job module

Export files must be sanitized before writing to disk.

Rules:

```text
1. Export default resultMode=FINAL.
2. Export may include sanitized debug columns only when explicitly requested.
3. Formula injection protection is mandatory for all spreadsheet text cells.
4. Do not export secret bindings, runtime secret bundles or unsanitized request headers.
5. Do not expose download endpoint until ExportJob.status=COMPLETED and file is sanitized.
```

Formula injection prefix characters:

```text
=
+
-
@
\t
\r
```

Recommended strategy:

```text
If a cell starts with a formula injection prefix, prefix it with a single quote.
```

---

### 9.10. Artifact module

Artifacts requiring masking before write:

```text
1. Promptfoo generated config file.
2. Promptfoo result JSON.
3. Promptfoo result JSONL.
4. Promptfoo HTML report.
5. Promptfoo logs.
6. Any runner diagnostic bundle.
```

Artifact metadata rule:

```text
promptfoo_run_artifacts.sanitized must be true only after sanitizer has completed successfully.
```

If sanitizer fails:

```text
1. Do not expose artifact content.
2. Store sanitized=false.
3. Store safe error code/message.
4. Mark run FAILED only if artifact failure prevents required result persistence.
```

---

## 10. Logging Safety

### 10.1. General rules

Use `@Slf4j` in implementation classes, but keep logs safe and useful.

Allowed log data:

```text
1. Resource IDs.
2. User ID.
3. Workspace ID.
4. Project ID.
5. TestRun ID.
6. Status transitions.
7. Counts.
8. Durations.
9. Safe error codes.
10. Trace ID / request ID.
```

Forbidden log data:

```text
1. Plain request DTO when it may contain secretValue/password/rawCurl.
2. Authorization header.
3. Cookie header.
4. Runtime secret bundle.
5. Raw provider response with headers.
6. Raw Promptfoo config/result.
7. Full exception message from external provider if it may contain tokens.
```

Recommended pattern:

```java
log.info(
    "Created chatbot target projectId={} targetId={} status={}",
    projectId,
    targetId,
    status
);
```

Forbidden pattern:

```java
log.info("Create target request={}", request);
```

---

### 10.2. Exception logging

For expected business errors:

```text
1. Throw ApiException/ErrorCode.
2. Do not log stack trace at error level.
3. Let GlobalExceptionHandler produce ProblemDetail.
```

For unexpected errors:

```text
1. Log error with traceId and safe context.
2. Do not include raw request body.
3. Sanitize external exception message before exposing it.
```

Recommended:

```java
log.error(
    "Provider test failed providerId={} errorCode={} traceId={}",
    providerId,
    errorCode,
    traceId,
    ex
);
```

But ensure exception type/message does not include secrets before logging. For external HTTP clients, prefer wrapping with sanitized exception.

---

### 10.3. MDC keys

Recommended MDC keys:

```text
traceId
requestId
userId
workspaceId
projectId
testRunId
runnerId
```

Forbidden MDC values:

```text
accessToken
refreshToken
secretId value is allowed, secret value is not
authorization
cookie
```

---

## 11. ProblemDetail Safety

ProblemDetail responses must follow API conventions, but must not leak secrets.

Safe fields:

```text
type
title
status
detail
instance
code
messageKey
traceId
fieldErrors
```

Rules:

```text
1. detail must be an English safe message.
2. detail must not include user-provided raw cURL or secretValue.
3. fieldErrors may include field names but not rejected sensitive values.
4. For sensitive validation errors, use generic rejectedValue=null.
5. External provider errors must be normalized and sanitized.
```

Example safe validation error:

```json
{
  "field": "secretValue",
  "code": "SECRET_VALUE_INVALID",
  "message": "Secret value is invalid or empty.",
  "rejectedValue": null
}
```

Forbidden:

```json
{
  "field": "secretValue",
  "message": "Invalid value sk-real-secret"
}
```

---

## 12. Hashing and Audit Rules

### 12.1. Snapshot hashes

Use SHA-256 over canonical JSON for:

```text
domain_config_hash
promptfoo_config_hash
config_sha256
result_sha256
```

Rules:

```text
1. Canonicalize JSON before hashing.
2. Hash sanitized snapshots/artifacts unless explicitly internal-only.
3. Store hash after sanitizer output is finalized.
4. Do not hash plaintext secret values for frontend-visible audit.
```

---

### 12.2. Secret hashes

For auth refresh tokens and similar credentials:

```text
1. Store token_hash, not plaintext token.
2. Use strong one-way hashing suitable for token verification.
3. Never return hash to frontend.
4. Do not log hash unless needed for internal security audit and approved.
```

For `raw_curl_hash`:

```text
1. Prefer hash of canonical sanitized cURL.
2. If product later requires original raw cURL hash, mark it internal-only and never expose it.
3. Document the chosen behavior in the entity/service implementation.
```

---

## 13. Transaction Boundaries

Masking must happen before persistence.

Recommended pattern:

```text
1. Validate request.
2. Resolve permission.
3. Build domain object.
4. Sanitize sensitive fields.
5. Persist sanitized domain object inside transaction.
6. Log safe summary.
7. Return response DTO.
```

For external calls:

```text
1. Persist initial safe state if needed.
2. Commit transaction.
3. Call external service without holding DB transaction.
4. Sanitize result/error.
5. Open new transaction to persist sanitized log/result.
```

Applies to:

```text
1. Provider test.
2. Chatbot target test connection.
3. Export job processing.
4. Runner event ingestion where payload needs sanitization before result insert.
```

---

## 14. MapStruct Safety Rules

MapStruct is required for DTO mapping, but it must not hide security decisions.

Rules:

```text
1. Do not map encryptedValue to any response DTO.
2. Do not map secretValue to any response DTO.
3. Do not use @AfterMapping to perform complex masking secretly.
4. Prefer service layer to sanitize before mapper receives entity/DTO.
5. Mappers may call safe formatting helpers for maskedPreview only.
6. Mappers must not decrypt secrets.
```

Recommended:

```java
@Mapper(componentModel = "spring")
public interface LlmProviderMapper {
  LlmProviderResponse toResponse(LlmProvider provider);
}
```

Forbidden:

```java
@AfterMapping
void decryptSecret(@MappingTarget LlmProviderResponse response, LlmProvider provider) {
  // Never do this.
}
```

---

## 15. DTO Safety Rules

Request records may contain plaintext sensitive fields when required.

Examples:

```text
CreateSecretRequest.secretValue
RotateSecretRequest.secretValue
LoginRequest.password
InspectCurlRequest.rawCurl
```

Response records must not contain plaintext sensitive fields.

Forbidden response fields:

```text
secretValue
password
accessToken in non-auth endpoints
refreshToken
encryptedValue
runtimeSecretBundle
rawCurlOriginal
```

Allowed safe response fields:

```text
hasValue
maskedPreview
rawCurlSanitized
sanitized=true
configHash
resultHash
artifactType
```

---

## 16. Async and Redis Safety

Async jobs and Redis events must follow the same masking policy.

Rules:

```text
1. Redis START_RUN command contains IDs only, not full config or secrets.
2. Redis CANCEL_RUN command contains IDs/control metadata only.
3. Runner events must not contain full artifacts or runtime secrets.
4. Backend event consumer sanitizes event payload again before persistence.
5. Async export jobs sanitize before writing files.
6. @Async methods must not receive plaintext secret bundles as method arguments unless unavoidable and internal-only.
7. If an async method must handle sensitive data, it must sanitize before logging/persistence and clear references as soon as practical.
```

---

## 17. Error Codes

Recommended error codes:

```text
SENSITIVE_DATA_DETECTED
SANITIZATION_FAILED
UNSANITIZED_ARTIFACT_FORBIDDEN
SECRET_VALUE_NOT_READABLE
SECRET_SCOPE_INVALID
RUNTIME_SECRET_ACCESS_DENIED
RAW_CURL_SANITIZATION_FAILED
PROVIDER_TEST_LOG_SANITIZATION_FAILED
ARTIFACT_SANITIZATION_FAILED
EXPORT_SANITIZATION_FAILED
```

Rules:

```text
1. Use stable error codes.
2. Use English default messages.
3. Use messageKey for frontend localization.
4. Do not include raw sensitive values in detail.
```

---

## 18. Testing Checklist

### 18.1. Unit tests

Test `SensitiveFieldDetector`:

```text
1. Authorization is sensitive.
2. X-API-Key is sensitive.
3. api_key is sensitive.
4. refreshToken is sensitive.
5. Content-Type is not sensitive.
```

Test `JsonSanitizer`:

```text
1. Sensitive object field is masked.
2. Sensitive nested field is masked.
3. Array name/value pair is masked.
4. Token-like scalar value is masked.
5. Non-sensitive values are preserved.
```

Test `CurlSanitizer`:

```text
1. Authorization header is masked.
2. Cookie header is masked.
3. Query api_key is masked.
4. JSON body password is masked.
5. Method/URL/non-sensitive headers are preserved.
```

Test `FormulaInjectionSanitizer`:

```text
1. =cmd is escaped.
2. +SUM(A1:A2) is escaped.
3. -1 is handled according to spreadsheet export policy.
4. @HYPERLINK is escaped.
5. Normal text is preserved.
```

---

### 18.2. Integration tests

Required integration tests:

```text
1. Create secret returns no secretValue/encryptedValue.
2. Rotate secret returns no secretValue/encryptedValue.
3. Provider test log stores masked request/response/error.
4. Chatbot target inspect-curl returns sanitized output.
5. Chatbot target create stores only sanitized raw cURL.
6. Test connection example stores masked request/response.
7. TestRun result detail returns sanitized request/response.
8. Artifact content endpoint refuses sanitized=false artifact.
9. Export file does not contain Authorization/API key/cookie.
10. ProblemDetail validation error does not include rejected secret value.
```

---

### 18.3. Log tests

Where feasible, add log capture tests for sensitive flows:

```text
1. Login failure does not log password.
2. Secret create does not log secretValue.
3. Provider test does not log Authorization header.
4. Chatbot target test does not log raw response with token.
5. Runtime secret resolution does not log runtime bundle.
```

---

## 19. AI Coding Agent Checklist

Before generating or modifying backend code, AI coding agents must check:

```text
1. Does this method accept secretValue/password/rawCurl/token/runtimeSecretBundle?
2. Is the sensitive value ever logged?
3. Is the sensitive value ever mapped to response DTO?
4. Is the sensitive value persisted plaintext?
5. Is masking using common/masking components?
6. Is external exception detail sanitized before persistence/exposure?
7. Is artifact content sanitized before write/download?
8. Is Redis payload compact and free of secrets?
9. Is SSE payload compact and free of secrets?
10. Are tests added for sensitive cases?
```

If any answer is unsafe, stop and fix the implementation before continuing.

---

## 20. Done Criteria

This module is complete when:

```text
1. common/masking has reusable components and tests.
2. Secret/provider/target/testrun/runner/export modules use VfqcSensitiveDataMasker.
3. No service/controller contains ad hoc masking regexes.
4. No response DTO exposes plaintext secret fields.
5. No artifact endpoint exposes sanitized=false content.
6. Logs for sensitive flows are verified safe.
7. ProblemDetail responses are verified safe.
8. Export formula injection sanitizer is used for all spreadsheet exports.
9. Security/masking tests run in CI.
10. Documentation and implementation use the same class/component names.
```
