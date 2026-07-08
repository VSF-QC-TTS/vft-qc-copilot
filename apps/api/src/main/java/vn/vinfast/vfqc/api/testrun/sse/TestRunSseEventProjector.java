package vn.vinfast.vfqc.api.testrun.sse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.runner.entity.RunnerEvent;

@Component
@RequiredArgsConstructor
class TestRunSseEventProjector {

  private final VfqcSensitiveDataMasker masker;

  TestRunSseEventResponse toResponse(RunnerEvent event) {
    return new TestRunSseEventResponse(
        event.getEventId(),
        event.getSequenceNo(),
        event.getTestRunId(),
        event.getEventType(),
        event.getOccurredAt(),
        event.getCreatedAt(),
        projectPayload(event.getPayloadJson()));
  }

  private Map<String, Object> projectPayload(Map<String, Object> payload) {
    var sanitized = masker.sanitizeMap(payload == null ? Map.of() : payload).value();
    return stripUnsafeMap(sanitized);
  }

  private Map<String, Object> stripUnsafeMap(Map<String, Object> source) {
    var result = new LinkedHashMap<String, Object>();
    for (var entry : source.entrySet()) {
      if (isUnsafeKey(entry.getKey())) {
        continue;
      }
      result.put(entry.getKey(), stripUnsafeValue(entry.getValue()));
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private Object stripUnsafeValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      return stripUnsafeMap((Map<String, Object>) map);
    }
    if (value instanceof List<?> list) {
      return list.stream().map(this::stripUnsafeValue).toList();
    }
    if (value instanceof String string) {
      return masker.sanitizeText(string).value();
    }
    return value;
  }

  private boolean isUnsafeKey(String key) {
    var normalized = key.toLowerCase().replace("_", "").replace("-", "").replace(" ", "");
    return normalized.equals("request")
        || normalized.equals("response")
        || normalized.equals("headers")
        || normalized.equals("body")
        || normalized.equals("raw")
        || normalized.equals("rawrequest")
        || normalized.equals("rawresponse")
        || normalized.equals("rawcurl")
        || normalized.equals("runtimeSecrets")
        || normalized.equals("runtimesecrets")
        || normalized.equals("secretbundle")
        || normalized.equals("promptfooconfig")
        || normalized.equals("promptfooconfigsnapshot")
        || normalized.equals("domainconfigsnapshot")
        || normalized.equals("artifactcontent")
        || normalized.equals("fulllog")
        || normalized.equals("fulllogs");
  }
}
