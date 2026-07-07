package vn.vinfast.vfqc.api.evaluator.dto;

import java.util.List;
import java.util.Map;

public record EvaluatorValidationResponse(
    boolean valid, List<Warning> warnings, Map<String, Object> normalized) {

  public record Warning(String code, String message) {}
}
