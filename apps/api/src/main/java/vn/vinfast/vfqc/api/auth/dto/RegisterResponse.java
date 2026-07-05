package vn.vinfast.vfqc.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import vn.vinfast.vfqc.api.user.enums.UserStatus;

public record RegisterResponse(
    @Schema(description = "Created user id") UUID id,
    @Schema(description = "Created user email", example = "user@vinfast.vn") String email,
    @Schema(description = "Current user status") UserStatus status,
    @Schema(example = "Registration accepted. Verify your email to activate the account.")
        String message) {}
