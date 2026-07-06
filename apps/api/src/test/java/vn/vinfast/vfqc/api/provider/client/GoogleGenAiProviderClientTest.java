package vn.vinfast.vfqc.api.provider.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;
import vn.vinfast.vfqc.api.provider.enums.ProviderType;

class GoogleGenAiProviderClientTest {

  @Test
  void test_Request_UsesApiKeyHeaderInsteadOfQueryString() {
    // Arrange
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var client = new GoogleGenAiProviderClient(builder);
    var provider =
        LlmProvider.builder()
            .id(UUID.randomUUID())
            .providerType(ProviderType.GOOGLE_GENAI)
            .baseUrl("https://google.example.test/v1beta")
            .build();
    server
        .expect(once(), requestTo("https://google.example.test/v1beta/models/gemini-test:generateContent"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("x-goog-api-key", "AIza-plain-secret"))
        .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

    // Act
    client.test(
        new ProviderClientTestRequest(
            provider, "AIza-plain-secret", "gemini-test", "Return ok", Map.of()));

    // Assert
    server.verify();
  }

  @Test
  void test_SuccessResponse_ReturnsRawPreview() {
    // Arrange
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var client = new GoogleGenAiProviderClient(builder);
    var provider =
        LlmProvider.builder()
            .id(UUID.randomUUID())
            .providerType(ProviderType.GOOGLE_GENAI)
            .baseUrl("https://google.example.test/v1beta")
            .build();
    server
        .expect(once(), requestTo("https://google.example.test/v1beta/models/gemini-test:generateContent"))
        .andRespond(withSuccess("{\"candidates\":[{\"content\":\"ok\"}]}", MediaType.APPLICATION_JSON));

    // Act
    var result =
        client.test(
            new ProviderClientTestRequest(
                provider, "AIza-plain-secret", "gemini-test", "Return ok", Map.of()));

    // Assert
    assertThat(result.responsePreview()).isEqualTo("{\"candidates\":[{\"content\":\"ok\"}]}");
    server.verify();
  }
}
