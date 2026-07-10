package vn.vinfast.vfqc.api.common.security;

import java.io.Serial;
import java.util.Collection;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUserAuthenticationToken extends AbstractAuthenticationToken {

  @Serial private static final long serialVersionUID = 1L;

  @Getter
  private final Jwt jwt;
  private final CurrentUser currentUser;

  CurrentUserAuthenticationToken(
      Jwt jwt, CurrentUser currentUser, Collection<? extends GrantedAuthority> authorities) {

    super(authorities);
    this.jwt = jwt;
    this.currentUser = currentUser;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return jwt;
  }

  @Override
  public CurrentUser getPrincipal() {
    return currentUser;
  }

  @Override
  public @NonNull String getName() {
    return currentUser.id().toString();
  }

}
