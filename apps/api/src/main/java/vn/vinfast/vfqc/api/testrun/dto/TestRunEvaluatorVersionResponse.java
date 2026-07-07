package vn.vinfast.vfqc.api.testrun.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorType;

public record TestRunEvaluatorVersionResponse(
    Long id,
    UUID evaluatorId,
    UUID evaluatorVersionId,
    EvaluatorType evaluatorType,
    int version,
    int orderIndex,
    Map<String, Object> config,
    List<Map<String, Object>> rules) {}
