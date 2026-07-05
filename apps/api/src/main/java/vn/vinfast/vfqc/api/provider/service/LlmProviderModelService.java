package vn.vinfast.vfqc.api.provider.service;

import java.util.Map;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;

public interface LlmProviderModelService {

  /**
   * Builds non-secret model metadata for provider responses.
   *
   * @param provider provider entity
   * @return model metadata safe for API responses
   */
  Map<String, Object> modelsFor(LlmProvider provider);
}
