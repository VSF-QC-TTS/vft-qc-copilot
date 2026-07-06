package vn.vinfast.vfqc.api.provider.client;

import java.util.Map;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;

public record ProviderClientTestRequest(
    LlmProvider provider,
    String plaintextSecret,
    String model,
    String prompt,
    Map<String, Object> overrides) {}
