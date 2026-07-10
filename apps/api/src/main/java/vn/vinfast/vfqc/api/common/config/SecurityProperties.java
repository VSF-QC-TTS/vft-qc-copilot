package vn.vinfast.vfqc.api.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vfqc.security")
public record SecurityProperties(
    long accessExpirationMinutes,
    long refreshExpirationMinutes,
    Jwt jwt,
    String encryptionKey,
    String encryptionSalt,
    Cookie cookie,
    String runnerToken) {

  public SecurityProperties {
    if (accessExpirationMinutes <= 0) {
      accessExpirationMinutes = 15;
    }
    if (refreshExpirationMinutes <= 0) {
      refreshExpirationMinutes = 10080;
    }
    if (jwt == null) {
      jwt = new Jwt("");
    }
    if (cookie == null) {
      cookie = new Cookie(false, "lax");
    }
    if (runnerToken == null || runnerToken.isBlank()) {
      runnerToken = "dev-runner-token";
    }
  }

  public record Jwt(String secretKey) {}

  public record Cookie(boolean secure, String sameSite) {}
}
