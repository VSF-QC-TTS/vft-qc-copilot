package vn.vinfast.vfqc.api.target.client;

import java.net.URI;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import vn.vinfast.vfqc.api.target.validator.TargetUrlValidator;

@Component
public class RestClientHttpTargetClient implements HttpTargetClient {

  private static final int MAX_RESPONSE_CHARS = 262_144;
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
  private final RestClient restClient;
  private final TargetUrlValidator targetUrlValidator;

  public RestClientHttpTargetClient(RestClient.Builder builder, TargetUrlValidator targetUrlValidator) {
    this.restClient = builder.requestFactory(requestFactory()).build();
    this.targetUrlValidator = targetUrlValidator;
  }

  @Override
  public TargetHttpResult execute(TargetHttpRequest request) {
    targetUrlValidator.validateForOutboundRequest(request.url());
    var started = Instant.now();
    try {
      var response =
          restClient
              .method(HttpMethod.valueOf(request.method()))
              .uri(uri(request))
              .headers(headers -> request.headers().forEach(headers::add))
              .body(request.body() == null ? "" : request.body())
              .retrieve()
              .toEntity(String.class);
      return new TargetHttpResult(
          response.getStatusCode().value(),
          headers(response.getHeaders().toSingleValueMap()),
          limit(response.getBody()),
          latency(started),
          null);
    } catch (RestClientResponseException exception) {
      return new TargetHttpResult(
          exception.getStatusCode().value(),
          headers(exception.getResponseHeaders() == null ? Map.of() : exception.getResponseHeaders().toSingleValueMap()),
          limit(exception.getResponseBodyAsString()),
          latency(started),
          null);
    } catch (Exception exception) {
      return new TargetHttpResult(0, Map.of(), "", latency(started), exception.getMessage());
    }
  }

  private URI uri(TargetHttpRequest request) {
    var builder = new StringBuilder(request.url());
    if (!request.queryParams().isEmpty()) {
      builder.append(request.url().contains("?") ? "&" : "?");
      builder.append(
          request.queryParams().entrySet().stream()
              .map(entry -> entry.getKey() + "=" + entry.getValue())
              .collect(java.util.stream.Collectors.joining("&")));
    }
    return URI.create(builder.toString());
  }

  private Map<String, Object> headers(Map<String, String> headers) {
    return new LinkedHashMap<>(headers);
  }

  private String limit(String value) {
    if (value == null) {
      return "";
    }
    return value.length() <= MAX_RESPONSE_CHARS ? value : value.substring(0, MAX_RESPONSE_CHARS);
  }

  private int latency(Instant started) {
    return (int) Duration.between(started, Instant.now()).toMillis();
  }

  private SimpleClientHttpRequestFactory requestFactory() {
    var factory =
        new SimpleClientHttpRequestFactory() {
          @Override
          protected void prepareConnection(HttpURLConnection connection, String httpMethod)
              throws java.io.IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
          }
        };
    factory.setConnectTimeout(REQUEST_TIMEOUT);
    factory.setReadTimeout(REQUEST_TIMEOUT);
    return factory;
  }
}
