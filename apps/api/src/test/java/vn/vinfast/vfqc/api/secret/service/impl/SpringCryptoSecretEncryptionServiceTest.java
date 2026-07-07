package vn.vinfast.vfqc.api.secret.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;

class SpringCryptoSecretEncryptionServiceTest {

  private SpringCryptoSecretEncryptionService encryptionService;

  @BeforeEach
  void setUp() {
    String password = "dev-only-change-me-dev-only-change-me";
    String salt = "0123456789abcdef0123456789abcdef";

    BytesEncryptor encryptor = Encryptors.stronger(password, salt);
    encryptionService = new SpringCryptoSecretEncryptionService(encryptor);
  }

  @Test
  void encrypt_SamePlaintextTwice_UsesDifferentCiphertextsAndDecryptsOriginal() {
    var plaintext = "sk-live-secret-value";

    var first = encryptionService.encrypt(plaintext);
    var second = encryptionService.encrypt(plaintext);

    assertThat(first).startsWith("spring-crypto:aes-gcm:v1:");
    assertThat(second).startsWith("spring-crypto:aes-gcm:v1:");
    assertThat(first).doesNotContain(plaintext);
    assertThat(second).doesNotContain(plaintext);
    assertThat(first).isNotEqualTo(second);
    assertThat(encryptionService.decrypt(first)).isEqualTo(plaintext);
    assertThat(encryptionService.decrypt(second)).isEqualTo(plaintext);
  }

  @Test
  void encrypt_EmptyString_EncryptsAndDecrypts() {
    var encrypted = encryptionService.encrypt("");

    assertThat(encrypted).isNotBlank();
    assertThat(encryptionService.decrypt(encrypted)).isEmpty();
  }

  @Test
  void encrypt_UnicodeContent_PreservesCharacters() {
    var plaintext = "hihihi12312312321";

    var encrypted = encryptionService.encrypt(plaintext);

    assertThat(encryptionService.decrypt(encrypted)).isEqualTo(plaintext);
  }
}
