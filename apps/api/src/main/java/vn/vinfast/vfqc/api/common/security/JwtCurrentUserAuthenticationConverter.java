package vn.vinfast.vfqc.api.common.security;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.user.enums.SystemRole;
import vn.vinfast.vfqc.api.user.enums.UserStatus;

import static vn.vinfast.vfqc.api.common.constant.JwtConstant.*;

@Component
public class JwtCurrentUserAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
    try {
      var role = SystemRole.valueOf(requiredClaim(jwt, SYSTEM_ROLE_CLAIM));

      var status = UserStatus.valueOf(requiredClaim(jwt, STATUS_CLAIM));

      var currentUser =
          new CurrentUser(
              UUID.fromString(requiredSubject(jwt)),
              requiredClaim(jwt, EMAIL_CLAIM),
              requiredClaim(jwt, NAME_CLAIM),
              role,
              status);

      return new CurrentUserAuthenticationToken(jwt, currentUser, List.of(role));
    } catch (IllegalArgumentException exception) {
      throw new InvalidBearerTokenException("Access token contains invalid claims", exception);
    }
  }

  private String requiredSubject(Jwt jwt) {
    var subject = jwt.getSubject();

    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("Missing JWT subject");
    }

    return subject;
  }

  private String requiredClaim(Jwt jwt, String claimName) {
    var value = jwt.getClaimAsString(claimName);

    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing JWT claim: " + claimName);
    }

    return value;
  }
}
