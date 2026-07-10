package vn.vinfast.vfqc.api.auth.dto;

import java.util.UUID;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;

public record CurrentUserResponse(
    UUID id,
    String email,
    String displayName,
    String avatarUrl,
    SystemRole systemRole,
    UserStatus status) {}
