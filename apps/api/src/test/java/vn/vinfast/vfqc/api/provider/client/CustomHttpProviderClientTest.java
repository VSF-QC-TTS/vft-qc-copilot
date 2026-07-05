package vn.vinfast.vfqc.api.provider.client;

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

class CustomHttpProviderClientTest {

  @Test
  void test_Request_UsesConfiguredUrlAndBearerHeader() {
    // Arrange
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    var client = new CustomHttpProviderClient(builder);
    var provider =
        LlmProvider.builder()
            .id(UUID.randomUUID())
            .providerType(ProviderType.CUSTOM_HTTP)
            .baseUrl("https://custom.example.test/test")
            .build();
    server
        .expect(once(), requestTo("https://custom.example.test/test"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer custom-secret"))
        .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

    // Act
    client.test(
        new ProviderClientTestRequest(
            provider, "custom-secret", "custom-model", "Return ok", Map.of()));

    // Assert
    server.verify();
  }
}
