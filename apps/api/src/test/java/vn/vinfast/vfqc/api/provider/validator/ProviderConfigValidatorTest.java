package vn.vinfast.vfqc.api.provider.validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.vinfast.vfqc.api.common.masking.DefaultVfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.provider.enums.ProviderType;

class ProviderConfigValidatorTest {

  private final ProviderConfigValidator validator =
      new ProviderConfigValidator(new DefaultVfqcSensitiveDataMasker());

  @Test
  void validateAndSanitize_ConfigWithSensitiveFields_MasksBeforePersistence() {
    // Arrange
    Map<String, Object> config =
        Map.of(
            "temperature",
            0,
            "apiKey",
            "sk-plain-secret",
            "headers",
            Map.of("Authorization", "Bearer eyJsecret", "Content-Type", "application/json"));

    // Act
    var result = validator.validateAndSanitize(ProviderType.OPENAI_COMPATIBLE, config);

    // Assert
    assertThat(result.value())
        .containsEntry("temperature", 0)
        .containsEntry("apiKey", "****");
    assertThat(result.value()).doesNotContainValue("sk-plain-secret");
    assertThat(result.findings()).contains("SENSITIVE_FIELD");
    @SuppressWarnings("unchecked")
    var headers = (Map<String, Object>) result.value().get("headers");
    assertThat(headers)
        .containsEntry("Authorization", "****")
        .containsEntry("Content-Type", "application/json");
  }
}
