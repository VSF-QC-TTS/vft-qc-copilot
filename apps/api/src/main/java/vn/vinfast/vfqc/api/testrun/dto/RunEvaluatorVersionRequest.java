package vn.vinfast.vfqc.api.testrun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RunEvaluatorVersionRequest(
    @NotNull(message = "Evaluator version id is required")
        @Schema(description = "Immutable evaluator version id to lock for this run")
        UUID evaluatorVersionId) {}
