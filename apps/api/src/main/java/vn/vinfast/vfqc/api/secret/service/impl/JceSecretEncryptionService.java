package vn.vinfast.vfqc.api.secret.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.common.config.SecurityProperties;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.secret.service.SecretEncryptionService;

@Service
@RequiredArgsConstructor
public class JceSecretEncryptionService implements SecretEncryptionService {

  private static final String VERSION = "v1";
  private static final String CIPHER = "AES/GCM/NoPadding";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecurityProperties securityProperties;
  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  public String encrypt(String plaintext) {
    try {
      var iv = new byte[IV_BYTES];
      secureRandom.nextBytes(iv);
      var cipher = Cipher.getInstance(CIPHER);
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
      var encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return VERSION
          + ":"
          + Base64.getEncoder().encodeToString(iv)
          + ":"
          + Base64.getEncoder().encodeToString(encrypted);
    } catch (GeneralSecurityException exception) {
      throw ErrorCode.INTERNAL_ERROR.throwIt("Secret encryption failed");
    }
  }

  @Override
  public String decrypt(String encryptedValue) {
    try {
      var parts = encryptedValue == null ? new String[0] : encryptedValue.split(":", 3);
      if (parts.length != 3 || !VERSION.equals(parts[0])) {
        throw ErrorCode.INTERNAL_ERROR.throwIt("Secret payload version is invalid");
      }
      var iv = Base64.getDecoder().decode(parts[1]);
      var encrypted = Base64.getDecoder().decode(parts[2]);
      var cipher = Cipher.getInstance(CIPHER);
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException | GeneralSecurityException exception) {
      throw ErrorCode.INTERNAL_ERROR.throwIt("Secret decryption failed");
    }
  }

  private SecretKeySpec key() {
    var hex = securityProperties.encryptionKey();
    if (hex == null || !hex.matches("[0-9a-fA-F]{64}")) {
      throw ErrorCode.INTERNAL_ERROR.throwIt("vfqc.security.encryption-key must be 64 hex chars");
    }
    var bytes = new byte[32];
    for (var i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
    }
    return new SecretKeySpec(bytes, "AES");
  }
}
