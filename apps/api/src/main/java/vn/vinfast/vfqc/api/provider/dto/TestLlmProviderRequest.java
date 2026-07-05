package vn.vinfast.vfqc.api.provider.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record TestLlmProviderRequest(
    @Schema(description = "Model to test. Defaults to provider default model when omitted.")
        @Size(max = 200, message = "Model must be at most 200 characters")
        String model,
    @Schema(description = "Small non-sensitive prompt used for provider connectivity test.")
        @Size(max = 1000, message = "Prompt must be at most 1000 characters")
        String prompt,
    @Schema(description = "Optional non-secret request overrides. Sensitive fields are sanitized.")
        Map<String, Object> overrides) {}
