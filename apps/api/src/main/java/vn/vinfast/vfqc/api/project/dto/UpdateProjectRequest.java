package vn.vinfast.vfqc.api.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
    @Schema(example = "VF Assistant Regression")
        @Size(max = 120, message = "Project name must be at most 120 characters")
        String name,
    @Schema(example = "Regression suite for customer-support chatbot flows")
        @Size(max = 1000, message = "Project description must be at most 1000 characters")
        String description) {}

