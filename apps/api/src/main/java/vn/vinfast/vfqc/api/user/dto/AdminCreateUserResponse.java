package vn.vinfast.vfqc.api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import vn.vinfast.vfqc.api.user.enums.UserStatus;

public record AdminCreateUserResponse(
    @Schema(description = "Created user id") UUID id,
    @Schema(description = "Created user email", example = "user@vinfast.vn") String email,
    @Schema(description = "Created user summary") UserSummaryResponse user,
    @Schema(description = "Current user status") UserStatus status,
    @Schema(example = "User created. Invite email sent.") String message) {}
