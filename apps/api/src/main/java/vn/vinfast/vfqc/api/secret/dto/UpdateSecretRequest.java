package vn.vinfast.vfqc.api.secret.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record UpdateSecretRequest(
    @Schema(description = "Updated display name. Omit to leave unchanged.", example = "Primary Gemini key")
        @Size(max = 160, message = "Secret name must be at most 160 characters")
        String name) {}
