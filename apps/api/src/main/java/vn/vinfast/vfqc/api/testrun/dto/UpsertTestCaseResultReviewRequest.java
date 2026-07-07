package vn.vinfast.vfqc.api.testrun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import vn.vinfast.vfqc.api.testrun.enums.ReviewStatus;
import vn.vinfast.vfqc.api.testrun.enums.TestCaseResultStatus;

public record UpsertTestCaseResultReviewRequest(
    @NotNull(message = "Review status is required")
        @Schema(description = "QC review status for this machine result", example = "OVERRIDDEN")
        ReviewStatus reviewStatus,
    @Schema(description = "Optional final status override", example = "PASSED")
    TestCaseResultStatus overrideStatus,
    @Schema(description = "Optional final score override", example = "0.9500")
    BigDecimal overrideScore,
    @Size(max = 4000, message = "Review note must be at most 4000 characters")
        @Schema(description = "Sanitized reviewer note", example = "Expected answer is acceptable.")
        String note) {}
