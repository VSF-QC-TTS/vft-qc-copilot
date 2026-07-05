package vn.vinfast.vfqc.api.common.security;

import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;

@Component
public class JwtCurrentUserAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    var role = SystemRole.valueOf(jwt.getClaimAsString("systemRole"));
    var user =
        new CurrentUser(
            UUID.fromString(jwt.getClaimAsString("uid")),
            jwt.getClaimAsString("email"),
            jwt.getClaimAsString("name"),
            role,
            UserStatus.valueOf(jwt.getClaimAsString("status")));
    return new CurrentUserAuthenticationToken(
        jwt, user, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
  }
}
