# VinFast QC Copilot - Security & Masking Implementation

Version: `v0.2`  
Status: Spring Boot 4.0.7-first Implementation Spec  
Applies to: `apps/api`

---

## 1. Purpose

This document defines sensitive data classification, sanitization, logging safety and artifact exposure rules.

---

## 2. Non-Negotiable Rules

1. Never return plaintext secret values after create/rotate/runtime-only flows.
2. Never persist unsanitized raw cURL containing auth/key/cookie/token material.
3. Never write unsanitized Promptfoo config/result/report/log artifacts.
4. Never log plaintext secrets, runtime secret bundles, Authorization headers, cookies or API keys.
5. Never include secret values in ProblemDetail detail, validation errors or exception messages.
6. Never expose artifact content when `sanitized=false`.
7. Never hide masking inside random DTO mappers.
8. Never implement one-off masking regexes inside controllers/services.
9. Always use shared masking components.
10. Store audit hashes over canonical sanitized snapshots/artifacts unless a hash is explicitly internal-only.

---

## 3. Sensitive Data Classification

Secret material:

```text
API keys
Bearer tokens
Basic auth credentials
OAuth access tokens
OAuth refresh tokens
Runner service tokens
User refresh tokens
Cookies
Session IDs
Passwords
Private keys
Webhook secrets
Runtime secret bundles
Service account JSON
```

Secret material may exist only in:

1. Request memory during create/rotate/login/runtime resolution.
2. Encryption/decryption service memory.
3. Auth/token validation memory.
4. Runner runtime secret response body for authenticated runner.

Secret material must not exist in:

1. Public API responses.
2. Logs.
3. ProblemDetail responses.
4. Sanitized snapshots.
5. Promptfoo artifacts.
6. Redis payloads.
7. SSE payloads.
8. Export files.

---

## 4. Sensitive Field Detection

Field/header names are sensitive if normalized name matches or contains:

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
service-account
webhook-secret
spring.ai.google.genai.api-key
google.api-key
gemini.api-key
vertex.credentials
private_key
client_email
client_id
```

Normalization:

1. Lowercase.
2. Remove spaces, underscores and hyphens for broad matching.
3. Preserve original field name in sanitized output when possible.

---

## 5. Sensitive Value Detection

Pattern detection is defense-in-depth.

Mask token-like values such as:

```text
Bearer eyJ...
sk-...
ghp_...
glpat-...
xoxb-...
AKIA...
-----BEGIN PRIVATE KEY-----
ya29....
AIza...
```

Rules:

1. Field-name detection is primary.
2. Pattern masking prefers false positives over false negatives.
3. Business logic must not depend on masked values.

---

## 6. Package Layout

```text
common/masking
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

---

## 7. Core Interface

```java
/**
 * Central application service for sanitizing sensitive data before it is persisted,
 * logged, exported, streamed, or exposed through API responses.
 *
 * <p>This interface is intentionally used as a small facade for MVP code. Feature modules
 * should depend on this abstraction instead of implementing their own masking regexes or
 * sanitizer helpers.
 *
 * <p>Implementations must follow these safety rules:
 *
 * <ul>
 *   <li>Never return plaintext secrets, tokens, cookies, passwords, private keys, or runtime
 *       secret bundles in sanitized output.</li>
 *   <li>Never include original sensitive values in {@link SanitizationFinding}.</li>
 *   <li>Never log original sensitive values while sanitizing.</li>
 *   <li>Prefer false positives over false negatives when detecting token-like values.</li>
 *   <li>Be deterministic and side-effect free.</li>
 *   <li>Do not access the database or external services.</li>
 * </ul>
 */
public interface VfqcSensitiveDataMasker {

  /**
   * Sanitizes an arbitrary JSON payload by masking sensitive field names and token-like values.
   *
   * <p>Typical callers include provider config persistence, chatbot target examples, test run
   * snapshots, runner event ingestion, Promptfoo artifact metadata, and API responses containing
   * external payload fragments.
   *
   * <p>The implementation should recursively inspect JSON objects and arrays. Field-name detection
   * is the primary protection mechanism, while value-pattern detection is a defense-in-depth layer.
   *
   * @param input source JSON payload; may be {@code null}
   * @param context masking context describing the caller module and data boundary
   * @return sanitization result containing the sanitized JSON value and safe finding metadata
   */
  SanitizationResult<JsonNode> sanitizeJson(JsonNode input, MaskingContext context);

  /**
   * Sanitizes HTTP headers by masking sensitive header values while preserving non-sensitive
   * operational headers.
   *
   * <p>Headers such as {@code Authorization}, {@code Cookie}, {@code Set-Cookie},
   * {@code X-API-Key}, and token-like custom headers must be masked. Non-sensitive headers such as
   * {@code Content-Type} may be preserved.
   *
   * @param headers source headers keyed by their original header names; may be {@code null}
   * @param context masking context describing the caller module and data boundary
   * @return sanitization result containing sanitized headers and safe finding metadata
   */
  SanitizationResult<Map<String, String>> sanitizeHeaders(
      Map<String, String> headers,
      MaskingContext context);

  /**
   * Sanitizes raw cURL text before persistence, logging, display, hashing, or conversion into a
   * structured chatbot target configuration.
   *
   * <p>The sanitizer must mask sensitive headers, cookies, query parameters, body fields, and
   * token-like free-form values while preserving enough structure for user review and debugging.
   *
   * <p>The returned sanitized cURL may be stored in the database. The original raw cURL must not be
   * persisted or logged.
   *
   * @param rawCurl raw cURL text received from the user; may be {@code null}
   * @param context masking context describing the caller module and data boundary
   * @return sanitization result containing sanitized cURL text and safe finding metadata
   */
  SanitizationResult<String> sanitizeCurl(String rawCurl, MaskingContext context);

  /**
   * Sanitizes free-form text such as external exception messages, provider error messages, runner
   * logs, artifact fragments, and ProblemDetail-safe error details.
   *
   * <p>This method should be used only for text that may contain externally sourced content or
   * sensitive values. It must not be used as a replacement for structured JSON/header sanitization
   * when structured data is available.
   *
   * @param text source text; may be {@code null}
   * @param context masking context describing the caller module and data boundary
   * @return sanitization result containing sanitized text and safe finding metadata
   */
  SanitizationResult<String> sanitizeText(String text, MaskingContext context);
}
```

Rules:

1. Deterministic.
2. Side-effect free.
3. Does not access database.
4. Logs only counts/types of findings.
5. Does not throw on unknown JSON shape unless input exceeds configured limits.

---

## 8. SanitizationResult

```java
public record SanitizationResult<T>(
    T value,
    boolean sanitized,
    int maskedFieldCount,
    int maskedValueCount,
    List<SanitizationFinding> findings
) {}
```

Findings must not include original values.

Safe finding:

```json
{
  "path": "$.headers.Authorization",
  "fieldName": "Authorization",
  "reason": "SENSITIVE_FIELD_NAME",
  "maskType": "FULL_MASK"
}
```

Forbidden finding:

```json
{
  "path": "$.headers.Authorization",
  "originalValue": "Bearer real-token"
}
```

---

## 9. Masking Targets

Recommended `MaskingTarget` values:

```text
RAW_CURL
CHATBOT_TARGET_CONFIG
CHATBOT_TARGET_EXAMPLE
PROVIDER_CONFIG
PROVIDER_TEST_LOG
SPRING_AI_CONFIG
TEST_RUN_SNAPSHOT
TEST_CASE_RESULT
RUNNER_EVENT
PROMPTFOO_CONFIG_ARTIFACT
PROMPTFOO_RESULT_ARTIFACT
PROMPTFOO_HTML_REPORT
PROMPTFOO_LOG_ARTIFACT
EXPORT_FILE
ERROR_MESSAGE
SSE_PAYLOAD
```

---

## 10. Raw cURL Sanitization

Rules:

1. Sanitize before persistence.
2. Mask sensitive headers.
3. Mask token-like values in body/query.
4. Preserve enough structure for user review.
5. Store hash of sanitized canonical cURL, not raw cURL.
6. Never log raw cURL.

---

## 11. Provider and Spring AI Config Sanitization

Rules:

1. Provider config JSON must be sanitized before save.
2. Spring AI config values must not be copied into provider snapshots if they contain secrets.
3. Google GenAI API key and Vertex credentials are Secret references.
4. Provider test request/response logs are sanitized.
5. Spring AI exceptions must be sanitized before persistence or ProblemDetail.

---

## 12. TestRun, Runner and Artifact Sanitization

Rules:

1. Domain snapshots are sanitized before DB save.
2. Promptfoo config snapshot is sanitized before DB save.
3. Full Promptfoo config artifact is sanitized before file write.
4. Runner events are sanitized before persistence.
5. SSE payload is derived from sanitized RunnerEvent.
6. Artifact metadata cannot claim `sanitized=true` unless sanitizer ran successfully.
7. Unsanitized artifact content must not be downloadable.

---

## 13. Export Sanitization

Rules:

1. Export files must not contain secrets.
2. Export cell values pass through FormulaInjectionSanitizer.
3. Result exports use sanitized extracted output/error summaries.
4. Raw request/response columns are excluded unless explicitly sanitized and contract allows.

---

## 14. Log Safety

Rules:

1. Use English technical logs.
2. Include IDs and counts, not content.
3. Never log passwords/tokens/secrets/cookies/runtime secret bundles.
4. Never log full dataset rows/prompts/responses.
5. Sanitize external exception messages before logging at warn/error if they may contain request details.

Recommended MDC keys:

```text
traceId
userId
workspaceId
projectId
testRunId
runnerId
```

---

## 15. Tests

Required tests:

1. Header names are detected case-insensitively.
2. `api_key`, `clientSecret`, `Authorization`, `Cookie` are masked.
3. Token-like neutral-field values are masked.
4. Raw cURL sanitizer masks auth headers and body secrets.
5. Provider config sanitizer masks Google/Spring AI keys.
6. Runner event payload sanitizer masks accidental tokens.
7. Export sanitizer prevents formula injection.
8. ProblemDetail never includes raw secret value.
9. Findings never include original values.
