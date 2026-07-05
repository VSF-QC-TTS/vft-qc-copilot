package vn.vinfast.vfqc.api.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vfqc.auth")
public record AuthProperties(Registration registration, EmailToken emailToken) {

  public AuthProperties {
    if (registration == null) {
      registration = new Registration(RegistrationMode.ADMIN_ONLY);
    }
    if (emailToken == null) {
      emailToken = new EmailToken(1440, 15);
    }
  }

  public record Registration(RegistrationMode mode) {

    public Registration {
      if (mode == null) {
        mode = RegistrationMode.ADMIN_ONLY;
      }
    }
  }

  public record EmailToken(long verificationExpirationMinutes, long passwordResetExpirationMinutes) {

    public EmailToken {
      if (verificationExpirationMinutes <= 0) {
        verificationExpirationMinutes = 1440;
      }
      if (passwordResetExpirationMinutes <= 0) {
        passwordResetExpirationMinutes = 15;
      }
    }
  }

  public enum RegistrationMode {
    ADMIN_ONLY,
    SELF_VERIFY
  }
}
