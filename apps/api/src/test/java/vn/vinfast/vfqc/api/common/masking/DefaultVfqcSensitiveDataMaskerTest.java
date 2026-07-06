package vn.vinfast.vfqc.api.common.masking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DefaultVfqcSensitiveDataMaskerTest {

  private final DefaultVfqcSensitiveDataMasker masker = new DefaultVfqcSensitiveDataMasker();

  @Test
  void sanitizeCurl_MasksHeadersQueryAndBodySecrets() {
    var rawCurl =
        """
        curl 'https://chatbot.example.test/api?api_key=sk-query-secret' \\
          -H 'Authorization: Bearer eyJsecret' \\
          -H 'Cookie: session=secret-cookie' \\
          --data '{"password":"p@ss","prompt":"hello"}'
        """;

    var result = masker.sanitizeCurl(rawCurl);

    assertThat(result.value())
        .contains("Authorization: Bearer ****")
        .contains("Cookie: ****")
        .contains("api_key=****")
        .contains("\"password\":\"****\"")
        .doesNotContain("sk-query-secret", "eyJsecret", "secret-cookie", "p@ss");
    assertThat(result.findings()).contains("TOKEN_LIKE_VALUE", "SENSITIVE_FIELD");
  }
}
