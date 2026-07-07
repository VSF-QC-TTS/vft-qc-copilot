package vn.vinfast.vfqc.api.evaluator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorOperator;

public record EvaluatorRuleRequest(
    @NotBlank String fieldSource,
    @NotNull EvaluatorOperator operator,
    Map<String, Object> expectedValue,
    BigDecimal weight,
    boolean required,
    Integer orderIndex,
    Map<String, Object> config) {}
