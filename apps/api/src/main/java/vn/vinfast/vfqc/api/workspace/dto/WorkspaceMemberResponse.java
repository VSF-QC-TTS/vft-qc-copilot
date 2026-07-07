package vn.vinfast.vfqc.api.workspace.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceMemberStatus;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;

public record WorkspaceMemberResponse(
    Long id,
    UUID workspaceId,
    UUID userId,
    UserSummaryResponse user,
    WorkspaceRole role,
    WorkspaceMemberStatus status,
    UUID invitedBy,
    UserSummaryResponse invitedByUser,
    OffsetDateTime joinedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
