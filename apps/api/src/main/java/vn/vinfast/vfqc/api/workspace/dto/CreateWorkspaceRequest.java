package vn.vinfast.vfqc.api.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record CreateWorkspaceRequest(
    @Schema(example = "VF QA Platform")
    @NotBlank(message = "Workspace name is required")
    @Size(max = 120, message = "Workspace name must be at most 120 characters")
    String name) {}
