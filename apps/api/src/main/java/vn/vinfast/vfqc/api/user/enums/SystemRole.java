package vn.vinfast.vfqc.api.user.enums;

import org.springframework.security.core.GrantedAuthority;

public enum SystemRole implements GrantedAuthority {
  PLATFORM_ADMIN,
  USER;

  @Override
  public String getAuthority() {
    return "ROLE_" + name();
  }
}
