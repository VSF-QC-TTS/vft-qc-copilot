package vn.vinfast.vfqc.api.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vfqc.mail")
public record MailProperties(Boolean enabled, String from) {

  public MailProperties {
    if (enabled == null) {
      enabled = true;
    }
    if (from == null || from.isBlank() || from.startsWith("${")) {
      from = "no-reply@localhost";
    }
  }
}
