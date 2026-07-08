package vn.vinfast.vfqc.api.common.storage;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vfqc.export")
public record StorageProperties(Path storageRoot, Duration expiration) {

  public StorageProperties {
    if (storageRoot == null) {
      storageRoot = Path.of(System.getProperty("java.io.tmpdir"), "vfqc-exports");
    }
    if (expiration == null) {
      expiration = Duration.ofDays(7);
    }
  }
}
