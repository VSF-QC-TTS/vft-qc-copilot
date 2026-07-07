package vn.vinfast.vfqc.api.evaluator.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorType;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;

public record EvaluatorResponse(
    UUID id,
    UUID projectId,
    String name,
    EvaluatorType type,
    EvaluatorVersionSummaryResponse latestVersion,
    UUID createdBy,
    UserSummaryResponse createdByUser,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
