package vn.vinfast.vfqc.api.evaluator.dto;

import java.util.List;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorOperator;

public record EvaluatorOperatorResponse(
    EvaluatorOperator operator,
    String label,
    boolean expectedValueRequired,
    List<String> compatibleDataTypes,
    String description) {}
