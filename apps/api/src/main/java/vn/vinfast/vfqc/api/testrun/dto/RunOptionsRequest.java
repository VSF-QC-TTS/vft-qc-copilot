package vn.vinfast.vfqc.api.testrun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RunOptionsRequest(
    @Min(value = 1, message = "Maximum row count must be at least 1")
        @Max(value = 10000, message = "Maximum row count must be at most 10000")
        @Schema(description = "Optional cap on dataset rows to include in the run", example = "500")
        Integer maxRows,
    @Schema(description = "Whether the response should include the sanitized Promptfoo snapshot", example = "true")
    Boolean includePromptfooSnapshot) {}
