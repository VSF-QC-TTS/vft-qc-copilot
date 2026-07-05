package vn.vinfast.vfqc.api.provider.client;

public interface LlmProviderClient {

  ProviderClientTestResult test(ProviderClientTestRequest request);
}
