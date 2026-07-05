package vn.vinfast.vfqc.api.provider.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import vn.vinfast.vfqc.api.provider.enums.ProviderTestStatus;

public record LlmProviderTestLogResponse(
    Long id,
    ProviderTestStatus status,
    String model,
    Map<String, Object> requestJsonSanitized,
    String responsePreview,
    String errorCode,
    String errorMessage,
    Integer latencyMs,
    Map<String, Object> tokenUsageJson,
    OffsetDateTime testedAt) {}
