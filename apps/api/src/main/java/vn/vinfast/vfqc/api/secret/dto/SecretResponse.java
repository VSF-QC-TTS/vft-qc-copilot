package vn.vinfast.vfqc.api.secret.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vinfast.vfqc.api.secret.enums.SecretScope;
import vn.vinfast.vfqc.api.secret.enums.SecretStatus;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;

public record SecretResponse(
    UUID id,
    SecretScope scope,
    UUID ownerUserId,
    UserSummaryResponse ownerUser,
    UUID workspaceId,
    String name,
    String maskedPreview,
    SecretStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
