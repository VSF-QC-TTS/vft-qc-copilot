package vn.vinfast.vfqc.api.provider.validator;

import java.util.Map;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.masking.SanitizationResult;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.provider.enums.ProviderType;

@Component
public class ProviderConfigValidator {

  private final VfqcSensitiveDataMasker masker;

  public ProviderConfigValidator(VfqcSensitiveDataMasker masker) {
    this.masker = masker;
  }

  public SanitizationResult<Map<String, Object>> validateAndSanitize(
      ProviderType providerType, Map<String, Object> config) {
    if (providerType == null) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Provider type is required");
    }
    return masker.sanitizeMap(config == null ? Map.of() : config);
  }
}
