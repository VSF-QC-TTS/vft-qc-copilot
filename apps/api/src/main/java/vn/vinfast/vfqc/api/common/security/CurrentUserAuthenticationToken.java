package vn.vinfast.vfqc.api.common.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class CurrentUserAuthenticationToken extends AbstractAuthenticationToken {

  private final Jwt jwt;
  private final CurrentUser currentUser;

  public CurrentUserAuthenticationToken(
      Jwt jwt, CurrentUser currentUser, Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.jwt = jwt;
    this.currentUser = currentUser;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return jwt.getTokenValue();
  }

  @Override
  public Object getPrincipal() {
    return currentUser;
  }
}
