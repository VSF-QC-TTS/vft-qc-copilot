package vn.vinfast.vfqc.api.target.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import vn.vinfast.vfqc.api.common.error.ApiException;

class CurlParserTest {

  private final CurlParser parser = new CurlParser();

  @Test
  void parseCurl_HttpBasics_ReturnsStructuredRequest() {
    // Arrange
    var rawCurl =
        "curl 'https://chatbot.example.test/api?lang=vi' "
            + "-X POST "
            + "-H 'Content-Type: application/json' "
            + "-H 'X-API-Key: sk-secret' "
            + "--data-raw '{\"question\":\"$dataset.question\"}'";

    // Act
    var parsed = parser.parse(rawCurl);

    // Assert
    assertThat(parsed.method()).isEqualTo("POST");
    assertThat(parsed.url()).isEqualTo("https://chatbot.example.test/api");
    assertThat(parsed.headers()).containsEntry("Content-Type", "application/json");
    assertThat(parsed.headers()).containsEntry("X-API-Key", "sk-secret");
    assertThat(parsed.queryParams()).containsEntry("lang", "vi");
    assertThat(parsed.body()).isEqualTo("{\"question\":\"$dataset.question\"}");
  }

  @Test
  void parseCurl_FileReference_RejectsInput() {
    // Arrange
    var rawCurl = "curl https://chatbot.example.test/api --data @payload.json";

    // Act / Assert
    assertThatThrownBy(() -> parser.parse(rawCurl)).isInstanceOf(ApiException.class);
  }
}
