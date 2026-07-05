package vn.vinfast.vfqc.api.provider.service.impl;

import java.util.Map;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;
import vn.vinfast.vfqc.api.provider.service.LlmProviderModelService;

@Service
public class LlmProviderModelServiceImpl implements LlmProviderModelService {

  @Override
  public Map<String, Object> modelsFor(LlmProvider provider) {
    if (provider.getAvailableModelsJson() != null && !provider.getAvailableModelsJson().isEmpty()) {
      return provider.getAvailableModelsJson();
    }
    return Map.of("defaultModel", provider.getDefaultModel());
  }
}
