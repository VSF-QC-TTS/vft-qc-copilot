package vn.vinfast.vfqc.api.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vfqc.client")
public record ClientProperties(String baseUrl) {

  public ClientProperties {
    if (baseUrl == null || baseUrl.isBlank() || baseUrl.startsWith("${")) {
      baseUrl = "http://localhost:3000";
    }
  }
}
