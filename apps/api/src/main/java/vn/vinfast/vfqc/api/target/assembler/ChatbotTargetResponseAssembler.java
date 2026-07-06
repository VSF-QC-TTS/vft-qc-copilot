package vn.vinfast.vfqc.api.target.assembler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.target.dto.ChatbotTargetExampleResponse;
import vn.vinfast.vfqc.api.target.dto.ChatbotTargetResponse;
import vn.vinfast.vfqc.api.target.dto.DatasetMappingDto;
import vn.vinfast.vfqc.api.target.dto.ResponseExtractionConfigDto;
import vn.vinfast.vfqc.api.target.dto.SecretBindingDto;
import vn.vinfast.vfqc.api.target.dto.TargetRequestConfigDto;
import vn.vinfast.vfqc.api.target.entity.ChatbotTarget;
import vn.vinfast.vfqc.api.target.entity.ChatbotTargetExample;
import vn.vinfast.vfqc.api.target.enums.TargetBodyType;

@Component
public class ChatbotTargetResponseAssembler {

  private final ObjectMapper objectMapper;

  public ChatbotTargetResponseAssembler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<ChatbotTargetResponse> toResponses(List<ChatbotTarget> targets) {
    return targets.stream().map(this::toResponse).toList();
  }

  public ChatbotTargetResponse toResponse(ChatbotTarget target) {
    return new ChatbotTargetResponse(
        target.getId(),
        target.getProjectId(),
        target.getName(),
        target.getDescription(),
        target.getRawCurlSanitized(),
        toRequestConfig(target),
        toExtractionConfig(target.getResponseExtractionConfigJson()),
        target.getLatestExampleId(),
        target.getStatus(),
        target.getCreatedAt(),
        target.getUpdatedAt());
  }

  public ChatbotTargetExampleResponse toResponse(ChatbotTargetExample example) {
    return new ChatbotTargetExampleResponse(
        example.getId(),
        valueOrEmpty(example.getRequestJson()),
        example.getResponseStatusCode(),
        valueOrEmpty(example.getResponseHeadersJson()),
        valueOrEmpty(example.getResponseBodyJson()),
        valueOrEmpty(example.getExtractedOutputJson()),
        example.getLatencyMs(),
        example.getErrorMessage(),
        example.getCreatedAt());
  }

  public TargetRequestConfigDto toRequestConfig(ChatbotTarget target) {
    var body = valueOrEmpty(target.getBodyJson());
    return new TargetRequestConfigDto(
        target.getMethod(),
        target.getUrl(),
        toStringMap(target.getHeadersJson()),
        toStringMap(target.getQueryParamsJson()),
        bodyType(body),
        body.get("value") == null ? null : String.valueOf(body.get("value")),
        toSecretBindings(target.getAuthConfigJson()),
        toDatasetMappings(target.getDatasetMappingJson()));
  }

  public ResponseExtractionConfigDto toExtractionConfig(Map<String, Object> config) {
    if (config == null || config.isEmpty()) {
      return null;
    }
    return objectMapper.convertValue(config, ResponseExtractionConfigDto.class);
  }

  private TargetBodyType bodyType(Map<String, Object> body) {
    if (body.get("type") == null) {
      return TargetBodyType.NONE;
    }
    return TargetBodyType.valueOf(String.valueOf(body.get("type")));
  }

  private List<SecretBindingDto> toSecretBindings(Map<String, Object> value) {
    return convertList(valueOrEmpty(value).get("secretBindings"), new TypeReference<>() {});
  }

  private List<DatasetMappingDto> toDatasetMappings(Map<String, Object> value) {
    return convertList(valueOrEmpty(value).get("datasetMappings"), new TypeReference<>() {});
  }

  private <T> List<T> convertList(Object value, TypeReference<List<T>> type) {
    return value == null ? List.of() : objectMapper.convertValue(value, type);
  }

  private Map<String, String> toStringMap(Map<String, Object> value) {
    return valueOrEmpty(value).entrySet().stream()
        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
  }

  private Map<String, Object> valueOrEmpty(Map<String, Object> value) {
    return value == null ? Map.of() : value;
  }
}
