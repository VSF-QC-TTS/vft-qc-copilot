package vn.vinfast.vfqc.api.secret.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import vn.vinfast.vfqc.api.common.config.SecurityProperties;

class JceSecretEncryptionServiceTest {

  private final JceSecretEncryptionService encryptionService =
      new JceSecretEncryptionService(
          new SecurityProperties(
              15,
              10080,
              new SecurityProperties.Jwt("dev-only-change-me-dev-only-change-me"),
              "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
              new SecurityProperties.Cookie(false, "lax"),
              "dev-runner-token"));

  @Test
  void encrypt_SamePlaintextTwice_UsesDifferentCiphertextsAndDecryptsOriginal() {
    // Arrange
    var plaintext = "sk-live-secret-value";

    // Act
    var first = encryptionService.encrypt(plaintext);
    var second = encryptionService.encrypt(plaintext);

    // Assert
    assertThat(first).startsWith("v1:").doesNotContain(plaintext);
    assertThat(second).startsWith("v1:").doesNotContain(plaintext);
    assertThat(first).isNotEqualTo(second);
    assertThat(encryptionService.decrypt(first)).isEqualTo(plaintext);
    assertThat(encryptionService.decrypt(second)).isEqualTo(plaintext);
  }
}
