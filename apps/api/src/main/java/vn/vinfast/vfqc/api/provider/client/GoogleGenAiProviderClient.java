package vn.vinfast.vfqc.api.provider.client;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GoogleGenAiProviderClient implements LlmProviderClient {

  private final RestClient restClient;

  public GoogleGenAiProviderClient(RestClient.Builder builder) {
    this.restClient = builder.build();
  }

  @Override
  public ProviderClientTestResult test(ProviderClientTestRequest request) {
    var started = Instant.now();
    var baseUrl =
        request.provider().getBaseUrl() == null || request.provider().getBaseUrl().isBlank()
            ? "https://generativelanguage.googleapis.com/v1beta"
            : request.provider().getBaseUrl();
    var body =
        Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", request.prompt())))));
    var response =
        restClient
            .post()
            .uri(baseUrl + "/models/" + request.model() + ":generateContent")
            .header("x-goog-api-key", request.plaintextSecret())
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
