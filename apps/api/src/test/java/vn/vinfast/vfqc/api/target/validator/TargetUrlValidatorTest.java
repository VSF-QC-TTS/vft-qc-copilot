package vn.vinfast.vfqc.api.target.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import vn.vinfast.vfqc.api.common.error.ApiException;
import vn.vinfast.vfqc.api.common.error.ErrorCode;

class TargetUrlValidatorTest {

  private final TargetUrlValidator validator = new TargetUrlValidator();

  @Test
  void validateForOutboundRequest_PublicHttpsUrl_IsAllowed() {
    assertThatCode(() -> validator.validateForOutboundRequest("https://chatbot.example.test/api"))
        .doesNotThrowAnyException();
  }

  @Test
  void validateForOutboundRequest_LocalhostPrivateAndMetadataUrls_AreRejected() {
    assertBlocked("http://localhost:8080/api");
    assertBlocked("http://127.0.0.1:8080/api");
    assertBlocked("http://10.1.2.3/api");
    assertBlocked("http://172.16.1.3/api");
    assertBlocked("http://192.168.1.3/api");
    assertBlocked("http://169.254.169.254/latest/meta-data");
    assertBlocked("https://user:pass@chatbot.example.test/api");
  }

  private void assertBlocked(String url) {
    assertThatThrownBy(() -> validator.validateForOutboundRequest(url))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> {
              var apiException = (ApiException) exception;
              org.assertj.core.api.Assertions.assertThat(apiException.getErrorCode())
                  .isEqualTo(ErrorCode.VALIDATION_ERROR);
            });
  }
}
