package vn.vinfast.vfqc.api.evaluator.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;

public record EvaluatorVersionSummaryResponse(
    UUID id, int version, OffsetDateTime createdAt, UserSummaryResponse createdByUser) {}
