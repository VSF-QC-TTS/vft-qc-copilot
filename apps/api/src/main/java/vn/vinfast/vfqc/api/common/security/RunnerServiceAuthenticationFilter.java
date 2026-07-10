package vn.vinfast.vfqc.api.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.vinfast.vfqc.api.common.config.SecurityProperties;

@Component
@RequiredArgsConstructor
public class RunnerServiceAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String RUNNER_PRINCIPAL = "runner";
  private static final String RUNNER_AUTHORITY = "ROLE_RUNNER";

  private final SecurityProperties properties;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    var authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (validRunnerToken(authorization)) {
      var authentication =
          UsernamePasswordAuthenticationToken.authenticated(
              RUNNER_PRINCIPAL, null, List.of(new SimpleGrantedAuthority(RUNNER_AUTHORITY)));

      var context = SecurityContextHolder.createEmptyContext();

      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
    } else if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
      var authentication =
          UsernamePasswordAuthenticationToken.authenticated("unknown", null, List.of());

      var context = SecurityContextHolder.createEmptyContext();

      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
    }

    filterChain.doFilter(request, response);
  }

  private boolean validRunnerToken(String authorization) {
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      return false;
    }

    var presentedToken = authorization.substring(BEARER_PREFIX.length());

    var expectedToken = properties.runnerToken();

    if (expectedToken == null || expectedToken.isBlank() || presentedToken.isBlank()) {
      return false;
    }

    return MessageDigest.isEqual(
        presentedToken.getBytes(StandardCharsets.UTF_8),
        expectedToken.getBytes(StandardCharsets.UTF_8));
  }
}
