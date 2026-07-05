package vn.vinfast.vfqc.api.provider.client;

import java.util.Map;

public record ProviderClientTestResult(
    String responsePreview, Integer latencyMs, Map<String, Object> tokenUsage) {}
