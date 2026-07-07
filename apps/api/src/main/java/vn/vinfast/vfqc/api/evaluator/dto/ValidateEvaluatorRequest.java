package vn.vinfast.vfqc.api.evaluator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorType;

public record ValidateEvaluatorRequest(
    @NotNull EvaluatorType type,
    Map<String, Object> config,
    @Valid List<EvaluatorRuleRequest> rules,
    ValidationContext context) {

  public record ValidationContext(
      UUID datasetSchemaId, Integer datasetSchemaVersion, UUID chatbotTargetId, Long exampleId) {}
}
