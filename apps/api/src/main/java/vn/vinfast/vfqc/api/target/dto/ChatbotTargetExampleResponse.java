package vn.vinfast.vfqc.api.target.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record ChatbotTargetExampleResponse(
    Long id,
    Map<String, Object> request,
    Integer responseStatusCode,
    Map<String, Object> responseHeaders,
    Map<String, Object> responseBody,
    Map<String, Object> extractedOutput,
    Integer latencyMs,
    String errorMessage,
    OffsetDateTime createdAt) {}
