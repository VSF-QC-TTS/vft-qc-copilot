package vn.vinfast.vfqc.api.evaluator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorType;

public record CreateEvaluatorRequest(
    @NotBlank @Size(max = 160) String name,
    @NotNull EvaluatorType type,
    Map<String, Object> config,
    @Valid List<EvaluatorRuleRequest> rules) {}
