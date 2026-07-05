package vn.vinfast.vfqc.api.workspace.dto;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;

public record UpdateWorkspaceMemberRoleRequest(
    @Schema(example = "VIEWER")
    @NotNull(message = "Workspace role is required") WorkspaceRole role) {}
