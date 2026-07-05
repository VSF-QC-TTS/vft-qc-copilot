package vn.vinfast.vfqc.api.provider.client;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiCompatibleProviderClient implements LlmProviderClient {

  private final RestClient restClient;

  public OpenAiCompatibleProviderClient(RestClient.Builder builder) {
    this.restClient = builder.build();
  }

  @Override
  public ProviderClientTestResult test(ProviderClientTestRequest request) {
    var started = Instant.now();
    var baseUrl =
        request.provider().getBaseUrl() == null || request.provider().getBaseUrl().isBlank()
            ? "https://api.openai.com/v1"
            : request.provider().getBaseUrl();
    var body =
        Map.of(
            "model",
            request.model(),
            "messages",
            List.of(Map.of("role", "user", "content", request.prompt())));
    var response =
        restClient
            .post()
            .uri(baseUrl + "/chat/completions")
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
