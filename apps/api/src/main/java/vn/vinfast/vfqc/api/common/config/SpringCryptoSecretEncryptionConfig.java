package vn.vinfast.vfqc.api.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;

/**
 * Spring Security Crypto configuration for secret encryption.
 *
 * @author nghlong3004 (Long Nguyen Hoang)
 * @since 7/7/2026
 */
@Configuration
public class SpringCryptoSecretEncryptionConfig {

  @Bean
  public BytesEncryptor secretBytesEncryptor(SecurityProperties securityProperties) {
    String encryptionKey = securityProperties.encryptionKey();
    String encryptionSalt = securityProperties.encryptionSalt();

    if (encryptionKey == null || encryptionKey.isBlank()) {
      throw new IllegalStateException("vfqc.security.encryption-key must be configured");
    }

    if (encryptionSalt == null || encryptionSalt.isBlank()) {
      throw new IllegalStateException("vfqc.security.encryption-salt must be configured");
    }

    return Encryptors.stronger(encryptionKey, encryptionSalt);
  }
}
