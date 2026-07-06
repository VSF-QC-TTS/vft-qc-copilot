package vn.vinfast.vfqc.api.target.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.target.dto.ResponseExtractionConfigDto;
import vn.vinfast.vfqc.api.target.dto.ResponseExtractionFieldDto;
import vn.vinfast.vfqc.api.target.enums.ResponseExtractionSource;
import vn.vinfast.vfqc.api.target.service.ResponseExtractionService;

@Service
@RequiredArgsConstructor
public class ResponseExtractionServiceImpl implements ResponseExtractionService {

  private final ObjectMapper objectMapper;

  @Override
  public Map<String, Object> extract(String responseBody, ResponseExtractionConfigDto config) {
    if (config == null || config.safeFields().isEmpty()) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Response extraction config is required");
    }
    var output = new LinkedHashMap<String, Object>();
    for (var field : config.safeFields()) {
      var value = extractField(responseBody == null ? "" : responseBody, field);
      if (value == null && field.required()) {
        throw ErrorCode.VALIDATION_ERROR.throwIt("Required extraction field is missing");
      }
      output.put(field.fieldName(), value);
    }
    return output;
  }

  private Object extractField(String responseBody, ResponseExtractionFieldDto field) {
    if (field.source() == ResponseExtractionSource.BODY_TEXT) {
      return responseBody;
    }
    if (field.path() == null || field.path().isBlank()) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("JSON extraction path is required");
    }
    return extractJsonPath(responseBody, field.path());
  }

  private Object extractJsonPath(String responseBody, String path) {
    try {
      Object current =
          objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
      for (var segment : normalize(path).split("\\.")) {
        current = descend(current, segment);
        if (current == null) {
          return null;
        }
      }
      return current;
    } catch (Exception exception) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Response body is not valid JSON");
    }
  }

  private String normalize(String path) {
    return path.startsWith("$.") ? path.substring(2) : path;
  }

  @SuppressWarnings("unchecked")
  private Object descend(Object current, String segment) {
    var bracket = segment.indexOf('[');
    var key = bracket < 0 ? segment : segment.substring(0, bracket);
    if (!(current instanceof Map<?, ?> map)) {
      return null;
    }
    Object value = ((Map<String, Object>) map).get(key);
    if (bracket < 0) {
      return value;
    }
    var end = segment.indexOf(']', bracket);
    if (end < 0 || !(value instanceof List<?> list)) {
      return null;
    }
    var index = Integer.parseInt(segment.substring(bracket + 1, end));
    return index >= 0 && index < list.size() ? list.get(index) : null;
  }
}
