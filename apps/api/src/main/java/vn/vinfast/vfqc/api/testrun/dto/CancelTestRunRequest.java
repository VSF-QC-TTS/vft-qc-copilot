package vn.vinfast.vfqc.api.testrun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record CancelTestRunRequest(
    @Size(max = 1000, message = "Cancel reason must be at most 1000 characters")
        @Schema(description = "Optional human-readable cancellation reason", example = "Superseded by a newer run")
        String reason) {}
