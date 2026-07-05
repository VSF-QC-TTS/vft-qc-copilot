package vn.vinfast.vfqc.api.provider.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import vn.vinfast.vfqc.api.provider.enums.ProviderType;

public record CreateLlmProviderRequest(
    @Schema(description = "Human-readable provider name.", example = "Gemini Judge")
        @NotBlank(message = "Provider name is required")
        @Size(max = 160, message = "Provider name must be at most 160 characters")
        String name,
    @Schema(description = "Provider implementation type.")
        @NotNull(message = "Provider type is required")
        ProviderType providerType,
    @Schema(description = "Secret id used by this provider. The secret value is never returned.")
        @NotNull(message = "Secret id is required")
        UUID secretId,
    @Schema(description = "Provider base URL. Optional for built-in provider defaults.")
        @Size(max = 500, message = "Base URL must be at most 500 characters")
        String baseUrl,
    @Schema(description = "Default model used by provider tests and evaluator defaults.")
        @NotBlank(message = "Default model is required")
        @Size(max = 200, message = "Default model must be at most 200 characters")
        String defaultModel,
    @Schema(description = "Non-secret provider configuration. Sensitive fields are sanitized.")
        Map<String, Object> config) {}
