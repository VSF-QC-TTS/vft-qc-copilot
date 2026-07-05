package vn.vinfast.vfqc.api.support;

import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.provider.client.LlmProviderClient;
import vn.vinfast.vfqc.api.provider.client.ProviderClientTestResult;
import vn.vinfast.vfqc.api.provider.enums.ProviderType;
import vn.vinfast.vfqc.api.provider.service.LlmProviderClientFactory;

@Component
@Primary
public class FakeLlmProviderClientFactory implements LlmProviderClientFactory {

  private RuntimeException failure;
  private ProviderClientTestResult result = new ProviderClientTestResult("ok", 10, Map.of());

  @Override
  public LlmProviderClient clientFor(ProviderType providerType) {
    return request -> {
      if (failure != null) {
        throw failure;
      }
      return result;
    };
  }

  public void succeedWith(String responsePreview) {
    failure = null;
    result = new ProviderClientTestResult(responsePreview, 10, Map.of());
  }

  public void failWith(String message) {
    failure = new RuntimeException(message);
  }
}
