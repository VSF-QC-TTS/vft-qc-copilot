package vn.vinfast.vfqc.api.evaluator.dto;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

public record CreateEvaluatorVersionRequest(
    Map<String, Object> config, @Valid List<EvaluatorRuleRequest> rules) {}
