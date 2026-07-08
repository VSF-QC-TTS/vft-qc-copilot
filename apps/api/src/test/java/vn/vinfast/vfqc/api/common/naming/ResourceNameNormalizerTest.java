package vn.vinfast.vfqc.api.common.naming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import vn.vinfast.vfqc.api.common.error.ApiException;
import vn.vinfast.vfqc.api.common.error.ErrorCode;

class ResourceNameNormalizerTest {

  private final ResourceNameNormalizer normalizer = new ResourceNameNormalizer();

  @Test
  void normalize_TrimsCollapsesWhitespaceAndBuildsLookupName() {
    var normalized = normalizer.normalize("  VFQC   Regression   Suite  ", 120);

    assertThat(normalized.displayName()).isEqualTo("VFQC Regression Suite");
    assertThat(normalized.lookupName()).isEqualTo("vfqc regression suite");
  }

  @Test
  void normalize_RejectsBlankTooLongAndUnsupportedNames() {
    assertInvalid("   ", 120, "Name must not be blank.");
    assertInvalid("abc/def", 120, "Name contains unsupported characters.");
    assertInvalid("abc\\def", 120, "Name contains unsupported characters.");
    assertInvalid("a".repeat(121), 120, "Name is too long.");
  }

  @Test
  void normalize_RejectsNullName() {
    assertThatThrownBy(() -> normalizer.normalize(null, 120))
        .isInstanceOf(ApiException.class)
        .hasMessage("Name is required.")
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_RESOURCE_NAME);
  }

  private void assertInvalid(String input, int maxLength, String message) {
    assertThatThrownBy(() -> normalizer.normalize(input, maxLength))
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_RESOURCE_NAME);
  }
}
