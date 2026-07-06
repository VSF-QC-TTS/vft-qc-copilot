package vn.vinfast.vfqc.api.provider.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import vn.vinfast.vfqc.api.provider.entity.LlmProvider;
import vn.vinfast.vfqc.api.provider.enums.ProviderType;

class OpenAiCompatibleProviderClientTest {

  @Test
  void test_Request_UsesBearerHeaderAndChatCompletionsEndpoint() {
    // Arrange
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var client = new OpenAiCompatibleProviderClient(builder);
    var provider =
        LlmProvider.builder()
            .id(UUID.randomUUID())
            .providerType(ProviderType.OPENAI_COMPATIBLE)
            .baseUrl("https://llm.example.test/v1")
            .build();
    server
        .expect(once(), requestTo("https://llm.example.test/v1/chat/completions"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer sk-plain-secret"))
        .andRespond(withSuccess("{\"id\":\"ok\"}", MediaType.APPLICATION_JSON));

    // Act
    client.test(
        new ProviderClientTestRequest(
            provider, "sk-plain-secret", "gpt-test", "Return ok", Map.of()));

    // Assert
    server.verify();
  }

  @Test
  void test_LongResponse_CapsPreviewAtFiveHundredCharacters() {
    // Arrange
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var client = new OpenAiCompatibleProviderClient(builder);
    var provider =
        LlmProvider.builder()
            .id(UUID.randomUUID())
            .providerType(ProviderType.OPENAI_COMPATIBLE)
            .baseUrl("https://llm.example.test/v1")
            .build();
    server
        .expect(once(), requestTo("https://llm.example.test/v1/chat/completions"))
        .andRespond(withSuccess("x".repeat(900), MediaType.APPLICATION_JSON));

    // Act
    var result =
        client.test(
            new ProviderClientTestRequest(
                provider, "sk-plain-secret", "gpt-test", "Return ok", Map.of()));

    // Assert
    assertThat(result.responsePreview()).hasSize(500);
    server.verify();
  }

  @Test
  void test_ErrorResponse_PropagatesRestClientException() {
    // Arrange
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var client = new OpenAiCompatibleProviderClient(builder);
    var provider =
        LlmProvider.builder()
            .id(UUID.randomUUID())
            .providerType(ProviderType.OPENAI_COMPATIBLE)
            .baseUrl("https://llm.example.test/v1")
            .build();
    server
        .expect(once(), requestTo("https://llm.example.test/v1/chat/completions"))
        .andRespond(
            withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"rate limited\"}}"));

    // Act / Assert
    assertThatThrownBy(
            () ->
                client.test(
                    new ProviderClientTestRequest(
                        provider, "sk-plain-secret", "gpt-test", "Return ok", Map.of())))
        .isInstanceOf(RestClientResponseException.class)
        .hasMessageContaining("429");
    server.verify();
  }
}
