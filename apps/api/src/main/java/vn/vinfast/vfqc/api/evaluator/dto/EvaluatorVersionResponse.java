package vn.vinfast.vfqc.api.evaluator.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorType;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;

public record EvaluatorVersionResponse(
    UUID id,
    UUID evaluatorId,
    UUID projectId,
    String name,
    EvaluatorType type,
    int version,
    Map<String, Object> config,
    List<EvaluatorRuleResponse> rules,
    UUID createdBy,
    UserSummaryResponse createdByUser,
    OffsetDateTime createdAt) {}
