package vn.vinfast.vfqc.api.common.security;

import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import vn.vinfast.vfqc.api.common.config.ClientProperties;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityChainsConfig {

  private static final String INTERNAL_RUNNER_PATH = "/internal/runner/**";
  private static final String API_PATH = "/api/**";
  private static final String ADMIN_PATH = "/api/v1/admin/**";
  private static final String CSRF_ENDPOINT = "/api/v1/auth/csrf";

  private static final List<String> PUBLIC_AUTH_POST_ENDPOINTS =
      List.of(
          "/api/v1/auth/register",
          "/api/v1/auth/login",
          "/api/v1/auth/refresh",
          "/api/v1/auth/logout",
          "/api/v1/auth/verify-email",
          "/api/v1/auth/password/forgot",
          "/api/v1/auth/password/reset");

  @Bean
  public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  /**
   * Prevents Spring Boot from registering the runner filter directly with the servlet container.
   *
   * <p>The filter must run only inside the runner SecurityFilterChain.
   */
  @Bean
  public FilterRegistrationBean<RunnerServiceAuthenticationFilter> runnerFilterRegistration(
      RunnerServiceAuthenticationFilter runnerFilter) {
    var registration = new FilterRegistrationBean<>(runnerFilter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  public BearerTokenResolver bearerTokenResolver() {
    var defaultResolver = new DefaultBearerTokenResolver();

    return request -> {
      var method = request.getMethod();
      var path = request.getServletPath();

      if (HttpMethod.GET.matches(method) && CSRF_ENDPOINT.equals(path)) {
        return null;
      }

      if (HttpMethod.POST.matches(method) && PUBLIC_AUTH_POST_ENDPOINTS.contains(path)) {
        return null;
      }

      return defaultResolver.resolve(request);
    };
  }

  @Bean
  public BearerTokenAuthenticationEntryPoint bearerTokenAuthenticationEntryPoint() {
    return new BearerTokenAuthenticationEntryPoint();
  }

  @Bean
  public BearerTokenAccessDeniedHandler bearerTokenAccessDeniedHandler() {
    return new BearerTokenAccessDeniedHandler();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(ClientProperties clientProperties) {

    var configuration = new CorsConfiguration();

    configuration.setAllowedOrigins(List.of(clientProperties.baseUrl()));
    configuration.setAllowedMethods(
        List.of(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name()));
    configuration.setAllowedHeaders(
        List.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.ACCEPT,
            "X-XSRF-TOKEN",
            "X-Request-Id"));
    configuration.setExposedHeaders(List.of("X-Request-Id"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration(API_PATH, configuration);
    return source;
  }

  @Bean
  @Order(1)
  public SecurityFilterChain runnerSecurity(
      HttpSecurity http,
      RunnerServiceAuthenticationFilter runnerFilter,
      BearerTokenAuthenticationEntryPoint authenticationEntryPoint) {

    return http.securityMatcher(INTERNAL_RUNNER_PATH)
        .csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint))
        .addFilterBefore(runnerFilter, BearerTokenAuthenticationFilter.class)
        .authorizeHttpRequests(authorization -> authorization.anyRequest().hasRole("RUNNER"))
        .build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain apiSecurity(
      HttpSecurity http,
      Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
      CorsConfigurationSource corsConfigurationSource,
      BearerTokenResolver bearerTokenResolver,
      BearerTokenAuthenticationEntryPoint authenticationEntryPoint,
      BearerTokenAccessDeniedHandler accessDeniedHandler) {

    var publicPostEndpoints = PUBLIC_AUTH_POST_ENDPOINTS.toArray(String[]::new);

    return http.securityMatcher(API_PATH)
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .csrf(CsrfConfigurer::spa)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .authorizeHttpRequests(
            authorization ->
                authorization
                    .requestMatchers(HttpMethod.GET, CSRF_ENDPOINT)
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, publicPostEndpoints)
                    .permitAll()
                    .requestMatchers(ADMIN_PATH)
                    .hasRole("PLATFORM_ADMIN")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .bearerTokenResolver(bearerTokenResolver)
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .build();
  }

  @Bean
  @Order(3)
  public SecurityFilterChain fallbackSecurity(HttpSecurity http) {

    return http.csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorization ->
                authorization
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/actuator/health",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness",
                        "/v3/api-docs",
                        "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .build();
  }
}
