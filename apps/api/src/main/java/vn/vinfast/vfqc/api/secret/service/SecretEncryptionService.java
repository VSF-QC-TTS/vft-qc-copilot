package vn.vinfast.vfqc.api.secret.service;

public interface SecretEncryptionService {

  /**
   * Encrypts plaintext secret material for persistence.
   *
   * @param plaintext raw secret value supplied by a trusted caller
   * @return versioned encoded encrypted payload safe for database storage
   */
  String encrypt(String plaintext);

  /**
   * Decrypts a versioned encrypted payload for runtime-only use.
   *
   * @param encryptedValue value previously returned by {@link #encrypt(String)}
   * @return plaintext secret value for immediate in-memory use
   */
  String decrypt(String encryptedValue);
}
