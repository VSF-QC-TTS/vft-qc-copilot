package vn.vinfast.vfqc.api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(
    @Schema(description = "User email address", example = "user@vinfast.vn")
    @Email(message = "Email must be valid")
    @NotBlank(message = "Email is required")
    String email,
    @Schema(description = "Display name shown in the workspace", example = "Nguyen Van A")
    @NotBlank(message = "Display name is required")
    @Size(max = 120, message = "Display name must be at most 120 characters")
    String displayName) {}
