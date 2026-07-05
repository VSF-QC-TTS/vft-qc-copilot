package vn.vinfast.vfqc.api.common.masking;

import java.util.Map;

public interface VfqcSensitiveDataMasker {

  SanitizationResult<String> sanitizeText(String text);

  SanitizationResult<Map<String, Object>> sanitizeMap(Map<String, Object> value);
}
