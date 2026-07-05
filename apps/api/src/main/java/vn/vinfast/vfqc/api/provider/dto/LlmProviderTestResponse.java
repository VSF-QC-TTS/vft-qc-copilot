package vn.vinfast.vfqc.api.provider.dto;

import java.util.Map;
import vn.vinfast.vfqc.api.provider.enums.ProviderTestStatus;

public record LlmProviderTestResponse(
    ProviderTestStatus status,
    String model,
    Map<String, Object> request,
    String responsePreview,
    String errorCode,
    String errorMessage,
    Integer latencyMs,
    Map<String, Object> tokenUsage) {}
