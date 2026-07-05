package vn.vinfast.vfqc.api.workspace.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceType;

public record WorkspaceResponse(
    UUID id,
    String name,
    WorkspaceType type,
    WorkspaceRole currentUserRole,
    UUID createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
