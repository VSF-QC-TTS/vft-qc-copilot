package vn.vinfast.vfqc.api.secret.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import vn.vinfast.vfqc.api.common.config.SecurityProperties;

class TinkSecretEncryptionServiceTest {

  private final TinkSecretEncryptionService encryptionService =
      new TinkSecretEncryptionService(
          new SecurityProperties(
              15,
              10080,
              new SecurityProperties.Jwt("dev-only-change-me-dev-only-change-me"),
              "generate",
              new SecurityProperties.Cookie(false, "lax"),
              "dev-runner-token"));

  @Test
  void encrypt_SamePlaintextTwice_UsesDifferentCiphertextsAndDecryptsOriginal() {
    // Arrange
    var plaintext = "sk-live-secret-value";

    // Act
    var first = encryptionService.encrypt(plaintext);
    var second = encryptionService.encrypt(plaintext);

    // Assert — Tink produces different ciphertexts due to random IV
    assertThat(first).doesNotContain(plaintext);
    assertThat(second).doesNotContain(plaintext);
    assertThat(first).isNotEqualTo(second);
    assertThat(encryptionService.decrypt(first)).isEqualTo(plaintext);
    assertThat(encryptionService.decrypt(second)).isEqualTo(plaintext);
  }

  @Test
  void encrypt_EmptyString_EncryptsAndDecrypts() {
    // Act
    var encrypted = encryptionService.encrypt("");

    // Assert
    assertThat(encrypted).isNotBlank();
    assertThat(encryptionService.decrypt(encrypted)).isEmpty();
  }

  @Test
  void encrypt_UnicodeContent_PreservesCharacters() {
    // Arrange
    var plaintext = "bí mật 🔐 tiếng Việt";

    // Act
    var encrypted = encryptionService.encrypt(plaintext);

    // Assert
    assertThat(encryptionService.decrypt(encrypted)).isEqualTo(plaintext);
  }
}
