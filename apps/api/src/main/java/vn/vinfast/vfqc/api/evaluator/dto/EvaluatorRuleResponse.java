package vn.vinfast.vfqc.api.evaluator.dto;

import java.math.BigDecimal;
import java.util.Map;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorOperator;

public record EvaluatorRuleResponse(
    Long id,
    String fieldSource,
    EvaluatorOperator operator,
    Map<String, Object> expectedValue,
    BigDecimal weight,
    boolean required,
    int orderIndex,
    Map<String, Object> config) {}
