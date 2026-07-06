package vn.vinfast.vfqc.api.secret.util;

import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.PredefinedAeadParameters;

/**
 * One-off utility to generate a Tink JSON keyset for the ENCRYPTION_KEY environment variable.
 *
 * <p>Usage: Run as a Java main class, copy the output JSON, and set it as the ENCRYPTION_KEY env
 * var in production.
 *
 * <p>Example output (single-line JSON):
 *
 * <pre>
 * {"primaryKeyId":123456,"key":[{"keyData":{"typeUrl":"type.googleapis.com/google.crypto.tink.AesGcmKey",...},...}]}
 * </pre>
 *
 * <p>This class is NOT used at runtime — it exists only as a developer tool.
 */
public final class TinkKeysetGenerator {

  private TinkKeysetGenerator() {}

  public static void main(String[] args) throws Exception {
    AeadConfig.register();
    var keysetHandle = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM);
    var json =
        TinkJsonProtoKeysetFormat.serializeKeyset(keysetHandle, InsecureSecretKeyAccess.get());

    System.out.println("=== Tink AES-256-GCM Keyset (JSON) ===");
    System.out.println();
    System.out.println(json);
    System.out.println();
    System.out.println("=== Usage ===");
    System.out.println("Set this JSON as the ENCRYPTION_KEY environment variable:");
    System.out.println("  export ENCRYPTION_KEY='" + json + "'");
  }
}
