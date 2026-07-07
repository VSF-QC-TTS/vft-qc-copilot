package vn.vinfast.vfqc.api.project.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;

public record ProjectResponse(
    UUID id,
    UUID workspaceId,
    String name,
    String description,
    UUID createdBy,
    UserSummaryResponse createdByUser,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime archivedAt) {}
