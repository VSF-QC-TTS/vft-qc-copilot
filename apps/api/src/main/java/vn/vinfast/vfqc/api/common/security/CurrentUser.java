package vn.vinfast.vfqc.api.common.security;

import java.io.Serializable;
import java.util.UUID;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;

public record CurrentUser(
    UUID id, String email, String name, SystemRole systemRole, UserStatus status)
    implements Serializable {

  public boolean platformAdmin() {
    return systemRole == SystemRole.PLATFORM_ADMIN;
  }
}
