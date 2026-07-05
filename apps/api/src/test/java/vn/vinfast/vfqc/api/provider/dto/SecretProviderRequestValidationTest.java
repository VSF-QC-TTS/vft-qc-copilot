package vn.vinfast.vfqc.api.provider.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.vinfast.vfqc.api.secret.dto.CreateSecretRequest;

class SecretProviderRequestValidationTest {

  @Test
  void createSecretRequest_BlankFields_HasValidationViolations() {
    // Arrange
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      var request = new CreateSecretRequest("", "");

      // Act
      var violations = validator.validate(request);

      // Assert
      assertThat(violations).extracting("propertyPath").map(Object::toString).contains("name", "value");
    }
  }

  @Test
  void createLlmProviderRequest_MissingRequiredFields_HasValidationViolations() {
    // Arrange
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      var request = new CreateLlmProviderRequest("", null, null, null, "", Map.of());

      // Act
      var violations = validator.validate(request);

      // Assert
      assertThat(violations)
          .extracting("propertyPath")
          .map(Object::toString)
          .contains("name", "providerType", "secretId", "defaultModel");
    }
  }
}
