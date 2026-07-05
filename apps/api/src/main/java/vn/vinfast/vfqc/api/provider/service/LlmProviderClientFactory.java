package vn.vinfast.vfqc.api.provider.service;

import vn.vinfast.vfqc.api.provider.client.LlmProviderClient;
import vn.vinfast.vfqc.api.provider.enums.ProviderType;

public interface LlmProviderClientFactory {

  /**
   * Selects the provider client implementation for a provider type.
   *
   * @param providerType provider type
   * @return provider client
   */
  LlmProviderClient clientFor(ProviderType providerType);
}
