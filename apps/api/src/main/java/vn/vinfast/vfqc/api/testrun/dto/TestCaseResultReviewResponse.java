package vn.vinfast.vfqc.api.testrun.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vinfast.vfqc.api.testrun.enums.ReviewStatus;
import vn.vinfast.vfqc.api.testrun.enums.TestCaseResultStatus;

public record TestCaseResultReviewResponse(
    Long id,
    Long testCaseResultId,
    UUID reviewerId,
    ReviewStatus reviewStatus,
    TestCaseResultStatus overrideStatus,
    BigDecimal overrideScore,
    String note,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
