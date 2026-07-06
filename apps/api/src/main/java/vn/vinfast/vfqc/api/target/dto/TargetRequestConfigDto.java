package vn.vinfast.vfqc.api.target.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import vn.vinfast.vfqc.api.target.enums.TargetBodyType;

public record TargetRequestConfigDto(
    @Schema(example = "POST") String method,
    @Schema(example = "https://chatbot.example.test/api")
        @Size(max = 2000, message = "Target URL must be at most 2000 characters")
        String url,
    Map<String, String> headers,
    Map<String, String> queryParams,
    TargetBodyType bodyType,
    @Size(max = 20000, message = "Target body must be at most 20000 characters") String body,
    List<@Valid SecretBindingDto> secretBindings,
    List<@Valid DatasetMappingDto> datasetMappings) {

  public Map<String, String> safeHeaders() {
    return headers == null ? Map.of() : headers;
  }

  public Map<String, String> safeQueryParams() {
    return queryParams == null ? Map.of() : queryParams;
  }

  public TargetBodyType safeBodyType() {
    return bodyType == null ? TargetBodyType.NONE : bodyType;
  }

  public List<SecretBindingDto> safeSecretBindings() {
    return secretBindings == null ? List.of() : secretBindings;
  }

  public List<DatasetMappingDto> safeDatasetMappings() {
    return datasetMappings == null ? List.of() : datasetMappings;
  }
}
