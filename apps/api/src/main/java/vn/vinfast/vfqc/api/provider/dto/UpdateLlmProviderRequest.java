package vn.vinfast.vfqc.api.provider.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record UpdateLlmProviderRequest(
    @Schema(description = "Updated display name. Omit to leave unchanged.")
        @Size(max = 160, message = "Provider name must be at most 160 characters")
        String name,
    @Schema(description = "Replacement secret id. Omit to keep current secret.")
        UUID secretId,
    @Schema(description = "Provider base URL. Omit to keep current value.")
        @Size(max = 500, message = "Base URL must be at most 500 characters")
        String baseUrl,
    @Schema(description = "Default model. Omit to keep current model.")
        @Size(max = 200, message = "Default model must be at most 200 characters")
        String defaultModel,
    @Schema(description = "Replacement non-secret provider configuration. Sensitive fields are sanitized.")
        Map<String, Object> config) {}
