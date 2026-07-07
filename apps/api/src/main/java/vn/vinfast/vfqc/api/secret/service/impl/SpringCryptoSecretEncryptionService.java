package vn.vinfast.vfqc.api.secret.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.secret.service.SecretEncryptionService;

/**
 * Spring Security Crypto-based encryption service for secret material.
 *
 * <p>Uses AES-256-GCM via {@link Encryptors#stronger}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SpringCryptoSecretEncryptionService implements SecretEncryptionService {

  private static final String PAYLOAD_PREFIX = "spring-crypto:aes-gcm:v1:";

  private final BytesEncryptor secretBytesEncryptor;

  @Override
  public String encrypt(String plaintext) {
    if (plaintext == null) {
      throw ErrorCode.INTERNAL_ERROR.throwIt("Secret plaintext is null");
    }

    try {
      byte[] ciphertext = secretBytesEncryptor.encrypt(plaintext.getBytes(StandardCharsets.UTF_8));

      return PAYLOAD_PREFIX + Base64.getEncoder().encodeToString(ciphertext);
    } catch (RuntimeException exception) {
      log.error("Secret encryption failed", exception);
      throw ErrorCode.INTERNAL_ERROR.throwIt("Secret encryption failed");
    }
  }

  @Override
  public String decrypt(String encryptedValue) {
    if (encryptedValue == null || encryptedValue.isBlank()) {
      throw ErrorCode.INTERNAL_ERROR.throwIt("Encrypted value is empty");
    }

    if (!encryptedValue.startsWith(PAYLOAD_PREFIX)) {
      throw ErrorCode.INTERNAL_ERROR.throwIt("Unsupported encrypted secret payload format");
    }

    try {
      String encodedCiphertext = encryptedValue.substring(PAYLOAD_PREFIX.length());
      byte[] ciphertext = Base64.getDecoder().decode(encodedCiphertext);

      byte[] plaintext = secretBytesEncryptor.decrypt(ciphertext);

      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (RuntimeException exception) {
      log.warn("Secret decryption failed: {}", exception.getClass().getSimpleName());
      throw ErrorCode.INTERNAL_ERROR.throwIt("Secret decryption failed");
    }
  }
}
