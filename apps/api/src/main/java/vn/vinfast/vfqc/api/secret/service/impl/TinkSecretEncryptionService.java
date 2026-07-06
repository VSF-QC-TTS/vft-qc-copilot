package vn.vinfast.vfqc.api.secret.service.impl;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.common.config.SecurityProperties;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.secret.service.SecretEncryptionService;

/**
 * Tink-based encryption service for secret material. Uses AES-256-GCM via Google Tink's AEAD
 * primitive, which provides:
 *
 * <ul>
 *   <li>Automatic IV/nonce management (prevents nonce reuse)
 *   <li>Built-in key rotation via Keyset (decrypts with correct key automatically)
 *   <li>Misuse-resistant API (hard to configure incorrectly)
 * </ul>
 *
 * <p>Key provisioning supports two modes:
 *
 * <ul>
 *   <li><b>Tink JSON keyset</b> (starts with '{'): production-ready, supports multi-key rotation.
 *       Generate with: {@code tinkey create-keyset --key-template AES256_GCM --out keyset.json
 *       --out-format json}
 *   <li><b>"generate"</b> keyword: development convenience, generates a fresh keyset at startup
 *       (data does not survive restarts)
 * </ul>
 */
@Service
@Slf4j
public class TinkSecretEncryptionService implements SecretEncryptionService {

  private static final byte[] EMPTY_ASSOCIATED_DATA = new byte[0];

  private final Aead aead;

  public TinkSecretEncryptionService(SecurityProperties securityProperties) {
    try {
      AeadConfig.register();
      var keysetHandle = resolveKeyset(securityProperties.encryptionKey());
      this.aead = keysetHandle.getPrimitive(Aead.class);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Failed to initialize Tink AEAD encryption", exception);
    }
  }

  @Override
  public String encrypt(String plaintext) {
    try {
      var ciphertext =
          aead.encrypt(plaintext.getBytes(StandardCharsets.UTF_8), EMPTY_ASSOCIATED_DATA);
      return Base64.getEncoder().encodeToString(ciphertext);
    } catch (GeneralSecurityException exception) {
      log.error("Secret encryption failed", exception);
      throw ErrorCode.INTERNAL_ERROR.throwIt("Secret encryption failed");
    }
  }

  @Override
  public String decrypt(String encryptedValue) {
    try {
      if (encryptedValue == null || encryptedValue.isBlank()) {
        throw ErrorCode.INTERNAL_ERROR.throwIt("Encrypted value is empty");
      }
      var ciphertext = Base64.getDecoder().decode(encryptedValue);
      var plaintext = aead.decrypt(ciphertext, EMPTY_ASSOCIATED_DATA);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException | GeneralSecurityException exception) {
      log.error("Secret decryption failed", exception);
      throw ErrorCode.INTERNAL_ERROR.throwIt("Secret decryption failed");
    }
  }

  private static KeysetHandle resolveKeyset(String encryptionKey) throws GeneralSecurityException {
    if (encryptionKey == null || encryptionKey.isBlank()) {
      throw new IllegalStateException("vfqc.security.encryption-key must be configured");
    }
    var trimmed = encryptionKey.trim();
    if (trimmed.startsWith("{")) {
      log.info("Loading Tink keyset from JSON configuration");
      return TinkJsonProtoKeysetFormat.parseKeyset(trimmed, InsecureSecretKeyAccess.get());
    }
    if ("generate".equalsIgnoreCase(trimmed)) {
      log.warn(
          "Generating ephemeral encryption keyset — encrypted data will NOT survive restarts. "
              + "Use a Tink JSON keyset in production.");
      return KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM);
    }
    throw new IllegalStateException(
        "vfqc.security.encryption-key must be a Tink JSON keyset (starts with '{') "
            + "or 'generate' for development. "
            + "Generate a keyset with: tinkey create-keyset --key-template AES256_GCM "
            + "--out-format json");
  }
}
