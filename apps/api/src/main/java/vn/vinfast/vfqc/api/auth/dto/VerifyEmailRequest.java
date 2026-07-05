package vn.vinfast.vfqc.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
    @Schema(description = "One-time email verification token")
    @NotBlank(message = "Token is required")
    String token) {}
