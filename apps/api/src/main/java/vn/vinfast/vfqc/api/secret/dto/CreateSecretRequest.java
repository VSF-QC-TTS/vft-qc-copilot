package vn.vinfast.vfqc.api.secret.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSecretRequest(
    @Schema(description = "Human-readable secret name shown in selectors.", example = "Gemini API key")
        @NotBlank(message = "Secret name is required")
        @Size(max = 160, message = "Secret name must be at most 160 characters")
        String name,
    @Schema(description = "Plaintext secret value. It is encrypted immediately and never returned.")
        @NotBlank(message = "Secret value is required")
        String value) {}
