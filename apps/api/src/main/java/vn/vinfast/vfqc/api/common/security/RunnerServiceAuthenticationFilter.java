package vn.vinfast.vfqc.api.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.vinfast.vfqc.api.common.config.SecurityProperties;

@Component
@RequiredArgsConstructor
public class RunnerServiceAuthenticationFilter extends OncePerRequestFilter {

  private final SecurityProperties properties;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    var authorization = request.getHeader("Authorization");
    var expected = "Bearer " + properties.runnerToken();
    if (expected.equals(authorization)) {
      var authentication =
          new UsernamePasswordAuthenticationToken(
              "runner", null, List.of(new SimpleGrantedAuthority("ROLE_RUNNER")));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    filterChain.doFilter(request, response);
  }
}
