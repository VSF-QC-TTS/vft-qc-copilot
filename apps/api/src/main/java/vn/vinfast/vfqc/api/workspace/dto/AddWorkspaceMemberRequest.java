package vn.vinfast.vfqc.api.workspace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;

public record AddWorkspaceMemberRequest(
    @Schema(example = "qa.engineer@vinfast.vn")
    @Email(message = "Member email must be valid")
    @NotBlank(message = "Member email is required")
    String email,
    @Schema(example = "EDITOR")
    @NotNull(message = "Workspace role is required") WorkspaceRole role) {}
