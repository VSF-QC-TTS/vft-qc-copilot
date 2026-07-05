package vn.vinfast.vfqc.api.secret.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RotateSecretRequest(
    @Schema(description = "New plaintext secret value for rotation. It is never returned.")
        @NotBlank(message = "Secret value is required")
        String value) {}
