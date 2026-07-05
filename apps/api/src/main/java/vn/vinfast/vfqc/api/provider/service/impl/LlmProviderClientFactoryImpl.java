package vn.vinfast.vfqc.api.provider.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.provider.client.CustomHttpProviderClient;
import vn.vinfast.vfqc.api.provider.client.GoogleGenAiProviderClient;
import vn.vinfast.vfqc.api.provider.client.LlmProviderClient;
import vn.vinfast.vfqc.api.provider.client.OpenAiCompatibleProviderClient;
import vn.vinfast.vfqc.api.provider.enums.ProviderType;
import vn.vinfast.vfqc.api.provider.service.LlmProviderClientFactory;

@Service
@RequiredArgsConstructor
public class LlmProviderClientFactoryImpl implements LlmProviderClientFactory {

  private final OpenAiCompatibleProviderClient openAiClient;
  private final GoogleGenAiProviderClient googleGenAiClient;
  private final CustomHttpProviderClient customHttpClient;

  @Override
  public LlmProviderClient clientFor(ProviderType providerType) {
    return switch (providerType) {
      case OPENAI_COMPATIBLE -> openAiClient;
      case GOOGLE_GENAI -> googleGenAiClient;
      case CUSTOM_HTTP -> customHttpClient;
      case null -> throw ErrorCode.VALIDATION_ERROR.throwIt("Provider type is required");
    };
  }
}
