package vn.vinfast.vfqc.api.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vfqc.user")
public record UserProperties(String defaultAvatarUrl) {

  public UserProperties {
    if (defaultAvatarUrl == null || defaultAvatarUrl.isBlank()) {
      defaultAvatarUrl =
          "https://ui-avatars.com/api/?name={name}&background=random&color=fff&size=128";
    }
  }
}
