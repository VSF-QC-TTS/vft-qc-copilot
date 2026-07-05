package vn.vinfast.vfqc.api.workspace.dto;

import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateWorkspaceRequest(
    @Schema(example = "VF QA Platform")
    @Size(max = 120, message = "Workspace name must be at most 120 characters") String name) {}
