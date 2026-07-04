# VinFast QC Copilot - Auth & Permission Implementation

Version: `v0.2`  
Status: Spring Boot 4.0.7-first Implementation Spec  
Applies to: `apps/api`

---

## 1. Purpose

This document explains authentication, JWT, refresh token, runner token and permission implementation using Spring Security 7 features instead of custom framework code.

---

## 2. Design Principles

1. Use Spring Security features directly.
2. Use `PasswordEncoder` for email/password authentication.
3. Use OAuth2 Resource Server for bearer-token protected APIs.
4. Use separate `SecurityFilterChain`s for frontend API and runner internal API.
5. Use HttpOnly Secure cookie for refresh token if refresh token is implemented.
6. Store only refresh token hash if refresh-token persistence is implemented.
7. Permission checks are VFQC domain logic and live in `common/permission`.
8. Runner service token is not a user token.
9. Platform admin bypasses normal workspace checks except plaintext secret reads.
10. Return 404 when resource existence must be hidden.

---

## 3. Package Layout

```text
auth
  controller/AuthController.java
  dto/request/LoginRequest.java
  dto/response/LoginResponse.java
  dto/response/RefreshAccessTokenResponse.java
  dto/response/CurrentUserResponse.java
  entity/AuthRefreshToken.java
  enums/AuthRefreshTokenStatus.java
  repository/AuthRefreshTokenRepository.java
  mapper/AuthMapper.java
  service/AuthService.java
  service/RefreshTokenService.java
  service/JwtTokenService.java
  service/impl/AuthServiceImpl.java
  service/impl/RefreshTokenServiceImpl.java
  service/impl/JwtTokenServiceImpl.java

user
  entity/User.java
  enums/SystemRole.java
  enums/UserStatus.java
  repository/UserRepository.java
  mapper/UserMapper.java
  service/UserQueryService.java
  service/impl/UserQueryServiceImpl.java

common/security
  CurrentUser.java
  SecurityUserPrincipal.java
  SecurityChainsConfig.java
  JwtAuthenticationConverter.java
  RunnerServiceAuthenticationFilter.java
  CookieFactory.java

common/permission
  PermissionAction.java
  WorkspacePermissionService.java
  ProjectPermissionService.java
  ResourceAccessGuard.java
```

Explicitly forbidden:

```text
PasswordHasher.java
Custom user JwtAuthenticationFilter.java
JWT parsing inside controllers
Permission matrix duplicated in feature services
```

---

## 4. Password Handling

Use Spring Security `PasswordEncoder`.

Recommended bean:

```java
@Bean
PasswordEncoder passwordEncoder() {
  return PasswordEncoderFactories.createDelegatingPasswordEncoder();
}
```

Rules:

1. Store only encoded password in `users.password_hash`.
2. Use `passwordEncoder.matches(rawPassword, user.getPasswordHash())` for login.
3. Use `passwordEncoder.encode(rawPassword)` when creating/changing passwords.
4. If `passwordEncoder.upgradeEncoding(encodedPassword)` is true, re-encode after successful login.
5. Never log raw password.
6. Never return `passwordHash` in any DTO.
7. Do not create custom hashing helpers.

---

## 5. Security Filter Chains

Use two chains.

### 5.1. Runner chain

Applies to:

```text
/internal/runner/**
```

Rules:

1. Uses runner service token authentication.
2. Does not accept user JWT as authorization.
3. Is ordered before the normal API chain.
4. CSRF disabled because runner uses service-to-service bearer token.

### 5.2. User API chain

Applies to:

```text
/api/**
```

Rules:

1. Public endpoints: login, refresh, logout if contract says public.
2. Protected endpoints: all business APIs.
3. Uses OAuth2 Resource Server JWT validation for access tokens.
4. Maps JWT claims into `CurrentUser` through `JwtAuthenticationConverter`.
5. CSRF may be disabled for stateless bearer-token APIs; if cookie-authenticated mutations are added, review CSRF separately.

Recommended shape:

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
class SecurityChainsConfig {

  private final RunnerServiceAuthenticationFilter runnerServiceAuthenticationFilter;
  private final Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter;

  @Bean
  @Order(1)
  SecurityFilterChain runnerSecurity(HttpSecurity http) throws Exception {
    return http
        .securityMatcher("/internal/runner/**")
        .csrf(csrf -> csrf.disable())
        .addFilterBefore(runnerServiceAuthenticationFilter, BearerTokenAuthenticationFilter.class)
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
    return http
        .securityMatcher("/api/**")
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .build();
  }
}
```

Adjust class names/imports to Spring Security 7 compile API during implementation.

---

## 6. Login Flow

Endpoint:

```http
POST /api/v1/auth/login
```

Flow:

1. Validate `LoginRequest` with Jakarta Bean Validation.
2. Normalize email for lookup.
3. Load active user by email.
4. Verify password using `PasswordEncoder`.
5. Return generic invalid-credentials error for wrong email/password.
6. Generate access token through `JwtTokenService`.
7. Generate refresh token if refresh flow is enabled.
8. Persist refresh token hash if refresh-token persistence is enabled.
9. Set refresh token as HttpOnly Secure cookie.
10. Return access token and current user summary.

Service example:

```java
@Service
@RequiredArgsConstructor
class AuthServiceImpl implements AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService jwtTokenService;
  private final RefreshTokenService refreshTokenService;
  private final AuthMapper authMapper;

  @Transactional
  public LoginResult login(LoginRequest request, ClientContext clientContext) {
    User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email().trim())
        .orElseThrow(() -> ErrorCode.INVALID_CREDENTIALS.exception());

    if (!user.isActive()) {
      throw ErrorCode.USER_DISABLED.exception();
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw ErrorCode.INVALID_CREDENTIALS.exception();
    }

    if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
      user.setPasswordHash(passwordEncoder.encode(request.password()));
    }

    AccessToken accessToken = jwtTokenService.issueAccessToken(user);
    RefreshTokenIssue refreshToken = refreshTokenService.issue(user, clientContext);

    return new LoginResult(accessToken, refreshToken, authMapper.toCurrentUserResponse(user));
  }
}
```

---

## 7. Refresh Token Flow

Endpoint:

```http
POST /api/v1/auth/refresh
```

Recommended MVP:

1. Persist refresh token hash.
2. Rotate refresh token on every refresh.
3. Revoke old token row.
4. Store token family id for reuse detection.

Rules:

1. Refresh token plaintext exists only in cookie/request memory.
2. Store only hash.
3. Clear cookie on missing/invalid/expired/reused token.
4. Do not return refresh token in JSON.
5. Logout revokes current token/session.

If the team wants faster MVP, stateless refresh JWT is allowed only if logout/reuse-detection limitations are documented.

---

## 8. Current User

Use `@AuthenticationPrincipal CurrentUser currentUser` in controllers after security conversion.

`CurrentUser` should contain:

```text
id
email
name
systemRole
status
```

Rules:

1. `CurrentUser` is created from authenticated principal, never from request body.
2. Disabled/deleted users cannot access APIs.
3. Feature services may reload user if they need fresh status.

---

## 9. Permission Model

Spring Security authenticates the user. VFQC permission services authorize domain actions.

Workspace roles:

```text
OWNER
ADMIN
MEMBER
VIEWER
```

Project actions:

```text
PROJECT_VIEW
PROJECT_UPDATE
PROJECT_ARCHIVE
TARGET_MANAGE
DATASET_MANAGE
EVALUATOR_MANAGE
RUN_START
RUN_CANCEL
RESULT_REVIEW
EXPORT_CREATE
```

Rules:

1. Permission services check system role + workspace membership + project ownership.
2. Controllers do not implement permission matrix.
3. Services call permission guard before sensitive reads/mutations.
4. Return 404 instead of 403 when existence hiding is required.
5. Plaintext secret reads are forbidden for every user-facing role.

---

## 10. Method Security

Method security may be enabled for simple checks:

```java
@EnableMethodSecurity
```

Use it for coarse checks only. Complex workspace/project checks should stay in permission services because they need DB lookups and existence-hiding behavior.

---

## 11. Cookie Rules

Refresh cookie:

1. `HttpOnly=true`.
2. `Secure=true` in production.
3. `SameSite=Lax` or `Strict` depending frontend flow.
4. Path restricted to auth endpoints if possible.
5. Max age matches refresh token expiry.

Do not store access token in cookie unless CSRF strategy is explicitly designed.

---

## 12. Error Codes

Recommended auth/security errors:

```text
INVALID_CREDENTIALS
USER_DISABLED
ACCESS_TOKEN_INVALID
ACCESS_TOKEN_EXPIRED
REFRESH_TOKEN_MISSING
REFRESH_TOKEN_INVALID
REFRESH_TOKEN_EXPIRED
REFRESH_TOKEN_REUSED
RUNNER_TOKEN_INVALID
PERMISSION_DENIED
RESOURCE_NOT_FOUND
```

Errors use `ProblemDetail` with stable `code` and `messageKey`.

---

## 13. Tests

Required tests:

1. Login success uses `PasswordEncoder.matches`.
2. Wrong email/password returns same error.
3. Disabled user cannot login.
4. Refresh token rotation works.
5. Reused refresh token is rejected.
6. User JWT cannot call `/internal/runner/**`.
7. Runner token cannot call `/api/v1/projects/**`.
8. Workspace member access matrix.
9. 404 existence hiding for inaccessible resources.
10. Password hash never appears in response JSON.
