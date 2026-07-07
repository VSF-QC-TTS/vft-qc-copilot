package vn.vinfast.vfqc.api.testrun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record RunPreflightRequest(
    @NotNull(message = "Dataset id is required")
        @Schema(description = "Dataset id whose active version will be used for run preflight")
        UUID datasetId,
    @NotEmpty(message = "At least one run target is required")
        @Valid
        @Schema(description = "Targets or baselines to evaluate")
        List<RunTargetRequest> targets,
    @NotEmpty(message = "At least one evaluator version is required")
        @Valid
        @Schema(description = "Immutable evaluator versions to apply")
        List<RunEvaluatorVersionRequest> evaluators,
    @Valid @Schema(description = "Optional run limits and snapshot options") RunOptionsRequest options) {}
