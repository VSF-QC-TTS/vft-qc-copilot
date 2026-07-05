package vn.vinfast.vfqc.api.workspace.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceMemberStatus;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;

public record WorkspaceMemberResponse(
    Long id,
    UUID workspaceId,
    UUID userId,
    WorkspaceRole role,
    WorkspaceMemberStatus status,
    UUID invitedBy,
    OffsetDateTime joinedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
