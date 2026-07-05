package vn.vinfast.vfqc.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @Schema(example = "admin@vinfast.vn")
    @Email(message = "Email must be valid")
    @NotBlank(message = "Email is required")
    String email,
    @Schema(example = "change-me")
    @NotBlank(message = "Password is required") String password) {}

