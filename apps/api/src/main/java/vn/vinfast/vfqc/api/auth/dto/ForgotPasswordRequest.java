package vn.vinfast.vfqc.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
    @Schema(description = "Account email address", example = "user@vinfast.vn")
        @Email(message = "Email must be valid")
        @NotBlank(message = "Email is required")
        String email) {}
