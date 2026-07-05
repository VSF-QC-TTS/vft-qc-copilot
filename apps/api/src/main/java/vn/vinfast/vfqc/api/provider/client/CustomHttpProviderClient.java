package vn.vinfast.vfqc.api.provider.client;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.vinfast.vfqc.api.common.error.ErrorCode;

@Component
public class CustomHttpProviderClient implements LlmProviderClient {

  private final RestClient restClient;

  public CustomHttpProviderClient(RestClient.Builder builder) {
    this.restClient = builder.build();
  }

  @Override
  public ProviderClientTestResult test(ProviderClientTestRequest request) {
    if (request.provider().getBaseUrl() == null || request.provider().getBaseUrl().isBlank()) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Custom HTTP provider baseUrl is required");
    }
    var started = Instant.now();
    var body = Map.of("model", request.model(), "prompt", request.prompt());
    var response =
        restClient
            .post()
            .uri(request.provider().getBaseUrl())
            .headers(headers -> headers.setBearerAuth(request.plaintextSecret()))
            .body(body)
            .retrieve()
            .body(String.class);
    return new ProviderClientTestResult(
        preview(response), (int) Duration.between(started, Instant.now()).toMillis(), Map.of());
  }

  private String preview(String value) {
    if (value == null) {
      return "";
    }
    return value.length() <= 500 ? value : value.substring(0, 500);
  }
}
