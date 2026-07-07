package vn.vinfast.vfqc.api.evaluator.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorOperatorResponse;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorOperator;
import vn.vinfast.vfqc.api.evaluator.service.EvaluatorOperatorService;

@Service
public class EvaluatorOperatorServiceImpl implements EvaluatorOperatorService {

  @Override
  public List<EvaluatorOperatorResponse> list() {
    return List.of(
        op(EvaluatorOperator.EQUALS, "Equals", true, List.of("ANY"), "Passes when values are equal."),
        op(EvaluatorOperator.NOT_EQUALS, "Not equals", true, List.of("ANY"), "Passes when values differ."),
        op(EvaluatorOperator.CONTAINS, "Contains", true, List.of("TEXT", "ARRAY"), "Passes when source contains expected value."),
        op(EvaluatorOperator.NOT_CONTAINS, "Not contains", true, List.of("TEXT", "ARRAY"), "Passes when source does not contain expected value."),
        op(EvaluatorOperator.REGEX_MATCH, "Regex match", true, List.of("TEXT"), "Passes when source matches a safe regex."),
        op(EvaluatorOperator.STARTS_WITH, "Starts with", true, List.of("TEXT"), "Passes when source starts with expected value."),
        op(EvaluatorOperator.ENDS_WITH, "Ends with", true, List.of("TEXT"), "Passes when source ends with expected value."),
        op(EvaluatorOperator.JSON_PATH_EXISTS, "JSON path exists", false, List.of("JSON"), "Passes when JSONPath resolves."),
        op(EvaluatorOperator.NUMERIC_EQUALS, "Numeric equals", true, List.of("NUMBER"), "Passes when numbers are equal."),
        op(EvaluatorOperator.NUMERIC_GT, "Greater than", true, List.of("NUMBER"), "Passes when source is greater."),
        op(EvaluatorOperator.NUMERIC_GTE, "Greater than or equal", true, List.of("NUMBER"), "Passes when source is greater or equal."),
        op(EvaluatorOperator.NUMERIC_LT, "Less than", true, List.of("NUMBER"), "Passes when source is less."),
        op(EvaluatorOperator.NUMERIC_LTE, "Less than or equal", true, List.of("NUMBER"), "Passes when source is less or equal."),
        op(EvaluatorOperator.IS_EMPTY, "Is empty", false, List.of("ANY"), "Passes when source is empty."),
        op(EvaluatorOperator.IS_NOT_EMPTY, "Is not empty", false, List.of("ANY"), "Passes when source is not empty."));
  }

  private EvaluatorOperatorResponse op(
      EvaluatorOperator operator,
      String label,
      boolean expectedValueRequired,
      List<String> dataTypes,
      String description) {
    return new EvaluatorOperatorResponse(
        operator, label, expectedValueRequired, dataTypes, description);
  }
}
