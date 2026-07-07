package vn.vinfast.vfqc.api.evaluator.dto;

import java.util.List;

public record EvaluatorFieldSourceResponse(
    List<DatasetVariable> datasetVariables,
    List<ExtractedField> extractedFields,
    List<ResponseField> responseFields) {

  public record DatasetVariable(
      String path, String name, String displayName, String dataType, boolean required) {}

  public record ExtractedField(
      String fieldSource, String label, String dataType, String jsonPath) {}

  public record ResponseField(String fieldSource, String label, String dataType) {}
}
