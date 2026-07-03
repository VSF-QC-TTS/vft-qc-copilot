# VinFast QC Copilot - Auth & Permission Implementation

Version: `v0.1`  
Status: Backend Implementation Spec Draft  
Applies to: `apps/api` Spring Boot backend  
Aligned with: PRD v0.7, ERD v0.5, API Contract `02-auth-permission-contract.md`

---

## 1. Purpose

This document explains how to implement authentication, refresh token handling and permission checks in the Spring Boot backend.

It is written for AI coding agents and backend developers so generated code follows one consistent style.

This file covers:

```text
1. Auth package structure.
2. Access token and refresh token flow.
3. CurrentUser resolution.
4. Refresh token persistence/rotation.
5. Workspace permission resolver.
6. Controller/service implementation rules.
7. DTO, mapper, logging and transaction conventions for auth code.
8. Error handling and security hardening rules.
```

This file does not define the public API shape. The public API contract is already defined in `docs/api/02-auth-permission-contract.md`.

---

## 2. Design Principles

Auth implementation must follow these principles:

```text
1. Access token authenticates normal frontend-facing APIs.
2. Refresh token is stored only in an HttpOnly Secure cookie.
3. Refresh token is never returned in JSON response body.
4. Refresh token is never stored in localStorage/sessionStorage by frontend.
5. Backend stores only a hash of refresh tokens if persistence is enabled.
6. Plain password, plaintext token and secret values must never be logged.
7. Permission checks must happen before data mutation or sensitive reads.
8. PLATFORM_ADMIN bypasses most workspace checks except plaintext secret read.
9. USER permissions are resolved through ACTIVE workspace membership.
10. If user cannot access a workspace/project/resource, return 404 when existence hiding is required by API contract.
```

Backend language policy:

```text
1. Code, logs, error codes and default ProblemDetail titles use English.
2. Do not hardcode Vietnamese user-facing text in auth or permission code.
3. Frontend translates `code` or `messageKey`.
```

---

## 3. Package Layout

Recommended package layout:

```text
vn.vinfast.vfqc.api
  auth
    controller
      AuthController.java
    dto
      request
        LoginRequest.java
      response
        LoginResponse.java
        RefreshAccessTokenResponse.java
        CurrentUserResponse.java
        UserWorkspaceSummaryResponse.java
    entity
      AuthRefreshToken.java
    enums
      AuthRefreshTokenStatus.java
    mapper
      AuthMapper.java
    repository
      AuthRefreshTokenRepository.java
    service
      AuthService.java
      RefreshTokenService.java
      TokenService.java
      impl
        AuthServiceImpl.java
        RefreshTokenServiceImpl.java
        JwtTokenServiceImpl.java

  user
    entity
      User.java
    enums
      SystemRole.java
      UserStatus.java
    repository
      UserRepository.java
    mapper
      UserMapper.java
    service
      UserQueryService.java
      impl
        UserQueryServiceImpl.java

  common
    security
      CurrentUser.java
      SecurityUserPrincipal.java
      JwtAuthenticationFilter.java
      JwtAuthenticationConverter.java
      RunnerServiceAuthenticationFilter.java
      SecurityConfig.java
      CookieFactory.java
      PasswordHasher.java
    permission
      PermissionAction.java
      WorkspacePermissionResolver.java
      ProjectPermissionResolver.java
      ResourceAccessGuard.java
      WorkspaceAccess.java
      ProjectAccess.java
    error
      ApiException.java
      ErrorCode.java
      GlobalExceptionHandler.java
      ProblemDetailFactory.java
```

Rules:

```text
1. `auth` owns authentication use cases.
2. `user` owns user lookup and persistence.
3. `common/security` owns authentication infrastructure.
4. `common/permission` owns authorization decisions shared by feature modules.
5. Feature modules must not duplicate permission logic.
```

---

## 4. Authentication Flow

### 4.1. Login

Public endpoint:

```http
POST /api/v1/auth/login
```

Implementation flow:

```text
1. Validate LoginRequest.
2. Normalize email for lookup.
3. Load ACTIVE user by email.
4. Verify password using PasswordHasher.
5. If invalid, return INVALID_CREDENTIALS without revealing whether email or password is wrong.
6. Generate short-lived access token.
7. Generate refresh token.
8. Store refresh token hash if persistence/rotation is enabled.
9. Set refresh cookie using Set-Cookie.
10. Return LoginResponse with access token and user summary.
```

Security rules:

```text
1. Do not log password.
2. Do not log access token.
3. Do not log refresh token.
4. Do not return refresh token in JSON.
5. Use the same generic error for wrong email and wrong password.
```

Service interface example:

```java
public interface AuthService {

  /**
   * Authenticates a user with email and password and creates a new login session.
   *
   * @param request login payload containing email and plaintext password from the current request
   * @param clientContext request metadata such as IP address and user agent for refresh-token audit
   * @return login result containing access token, user summary and refresh cookie metadata
   * @throws ApiException with INVALID_CREDENTIALS if credentials are invalid
   * @throws ApiException with USER_DISABLED if the user exists but is not active
   */
  LoginResult login(LoginRequest request, ClientContext clientContext);
}
```

Implementation style:

```java
@Slf4j
@Service
@RequiredArgsConstructor
class AuthServiceImpl implements AuthService {

  private final UserRepository userRepository;
  private final PasswordHasher passwordHasher;
  private final TokenService tokenService;
  private final RefreshTokenService refreshTokenService;
  private final AuthMapper authMapper;

  @Override
  @Transactional
  public LoginResult login(LoginRequest request, ClientContext clientContext) {
    String normalizedEmail = normalizeEmail(request.email());
    log.info("Login attempt: emailHash={}", HashLog.safeHash(normalizedEmail));

    User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)
        .orElseThrow(() -> ErrorCode.INVALID_CREDENTIALS.exception());

    if (!user.isActive()) {
      log.warn("Login rejected for disabled user: userId={}", user.getId());
      throw ErrorCode.USER_DISABLED.exception();
    }

    if (!passwordHasher.matches(request.password(), user.getPasswordHash())) {
      log.warn("Login rejected due to invalid credentials: userId={}", user.getId());
      throw ErrorCode.INVALID_CREDENTIALS.exception();
    }

    AccessToken accessToken = tokenService.createAccessToken(user);
    RefreshTokenIssue refreshToken = refreshTokenService.issue(user, clientContext);

    log.info("Login successful: userId={}", user.getId());
    return new LoginResult(accessToken, refreshToken, authMapper.toCurrentUserResponse(user));
  }
}
```

Notes:

```text
1. `HashLog.safeHash(...)` is illustrative. Use any safe non-reversible identifier strategy if logging email correlation is needed.
2. Do not create a custom hash helper if not needed; logging userId after lookup is usually enough.
3. Avoid logging raw email if organization policy treats email as personal data.
```

---

### 4.2. Refresh Access Token

Public endpoint:

```http
POST /api/v1/auth/refresh
```

Implementation flow with refresh rotation:

```text
1. Read refresh token from HttpOnly cookie.
2. If missing, clear cookie and return REFRESH_TOKEN_MISSING.
3. Hash incoming refresh token.
4. Load ACTIVE refresh token row by hash.
5. Validate expiry, user status and token family status.
6. Revoke/rotate old refresh token row.
7. Issue new refresh token row and Set-Cookie header.
8. Generate a new access token.
9. Return RefreshAccessTokenResponse with access token only.
```

Implementation flow with stateless refresh JWT:

```text
1. Read refresh token from HttpOnly cookie.
2. Validate token signature/expiry/type.
3. Validate user status.
4. Generate a new access token.
5. Optionally issue a new refresh JWT if rotation is supported.
```

MVP recommendation:

```text
Use persisted refresh token hashes if the team wants logout, revoke and reuse-detection to work reliably.
If implementation time is tight, stateless refresh JWT is acceptable only if docs and code clearly disable rotation/reuse-detection requirements.
```

Service interface example:

```java
public interface RefreshTokenService {

  /**
   * Issues a new refresh token for the user and stores only its hash when persistence is enabled.
   *
   * @param user active authenticated user receiving the refresh token
   * @param clientContext request metadata for audit and session diagnostics
   * @return plaintext refresh token value plus cookie metadata; plaintext must not be logged or persisted
   */
  RefreshTokenIssue issue(User user, ClientContext clientContext);

  /**
   * Rotates an existing refresh token and creates a new token in the same token family.
   *
   * @param rawRefreshToken plaintext refresh token read from the HttpOnly cookie
   * @param clientContext request metadata for audit and reuse detection
   * @return refresh result containing the user and a newly issued refresh token
   * @throws ApiException if the refresh token is missing, invalid, expired, revoked or reused
   */
  RefreshTokenRotationResult rotate(String rawRefreshToken, ClientContext clientContext);

  /**
   * Revokes the refresh token associated with the current logout request.
   *
   * @param rawRefreshToken plaintext refresh token read from the HttpOnly cookie; may be null
   * @param clientContext request metadata for audit
   */
  void revokeCurrent(String rawRefreshToken, ClientContext clientContext);
}
```

Transaction rules:

```text
1. issue(): @Transactional
2. rotate(): @Transactional
3. revokeCurrent(): @Transactional
4. token signature validation can happen outside a transaction, but DB status updates require a transaction.
```

---

### 4.3. Logout

Public endpoint:

```http
POST /api/v1/auth/logout
```

Implementation flow:

```text
1. Read refresh token cookie if present.
2. Revoke current refresh token/session if persistence exists.
3. Clear refresh cookie through Set-Cookie Max-Age=0.
4. Return 204 No Content.
```

Rules:

```text
1. Logout must be safe when cookie is missing.
2. Do not fail logout just because the token is already expired/revoked.
3. Frontend clears in-memory access token after successful logout.
```

---

### 4.4. Get Current User

Public endpoint:

```http
GET /api/v1/auth/me
```

Implementation flow:

```text
1. Resolve CurrentUser from SecurityContext.
2. Load user summary if needed.
3. Load visible workspace summaries.
4. Return CurrentUserResponse.
```

Rules:

```text
1. Do not return passwordHash.
2. Do not return refresh token/session data.
3. Workspaces must include only visible workspaces for normal USER.
4. PLATFORM_ADMIN behavior may return admin-visible workspaces depending on product policy.
```

---

## 5. Controller Implementation

Use thin controllers. Controllers should validate request shape and delegate use cases to services.

Example:

```java
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication and current user APIs")
class AuthController {

  private final AuthService authService;
  private final RefreshCookieWriter refreshCookieWriter;

  @Operation(summary = "Login and issue access token")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Login successful"),
      @ApiResponse(responseCode = "401", description = "Invalid credentials")
  })
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {

    LoginResult result = authService.login(request, ClientContext.from(servletRequest));
    refreshCookieWriter.write(servletResponse, result.refreshTokenIssue());
    return ResponseEntity.ok(result.toResponse());
  }
}
```

Controller rules:

```text
1. Use `@Valid` for request records.
2. Use Swagger annotations on controller methods and DTO fields.
3. Do not implement password verification in controller.
4. Do not build JWT manually in controller.
5. Do not perform workspace permission rules in controller unless using a dedicated guard method.
6. Do not catch domain exceptions just to return custom maps; use GlobalExceptionHandler.
```

---

## 6. DTO Implementation

Use Java records for request/response DTOs.

### 6.1. LoginRequest

```java
public record LoginRequest(
    @NotBlank
    @Email
    @Size(max = 320)
    @Schema(description = "User email address", example = "qc.member@example.com")
    String email,

    @NotBlank
    @Size(min = 8, max = 200)
    @Schema(description = "User password", example = "correct-horse-battery-staple", accessMode = Schema.AccessMode.WRITE_ONLY)
    String password) {}
```

Rules:

```text
1. Password field must be WRITE_ONLY in OpenAPI schema.
2. Never include password in response DTO.
3. Validation examples are examples only, not real secrets.
```

### 6.2. LoginResponse

```java
public record LoginResponse(
    @Schema(description = "Short-lived access token", example = "eyJhbGciOi...")
    String accessToken,

    @Schema(description = "Token type", example = "Bearer")
    String tokenType,

    @Schema(description = "Access token lifetime in seconds", example = "900")
    long expiresIn,

    @Schema(description = "Authenticated user summary")
    CurrentUserResponse user) {}
```

Rules:

```text
1. Do not add refreshToken field.
2. tokenType should be `Bearer`.
3. expiresIn should match actual token settings.
```

### 6.3. CurrentUserResponse

```java
public record CurrentUserResponse(
    UUID id,
    String email,
    String name,
    String avatarUrl,
    SystemRole systemRole,
    UserStatus status,
    List<UserWorkspaceSummaryResponse> workspaces) {}
```

---

## 7. MapStruct Rules

Use MapStruct for user/auth response mapping.

Example:

```java
@Mapper(componentModel = "spring")
public interface AuthMapper {
  CurrentUserResponse toCurrentUserResponse(User user);
}
```

For `CurrentUserResponse` that includes workspace summaries, use one of these approaches:

```text
1. Map basic user with MapStruct, then use an assembler for workspace summaries.
2. Create a dedicated CurrentUserAssembler that combines User + List<WorkspaceMember>.
```

Do not write mapping helpers inside `AuthServiceImpl`.

Allowed:

```text
CurrentUserAssembler.toResponse(user, workspaces)
```

Avoid:

```java
private CurrentUserResponse toResponse(User user) { ... }
```

---

## 8. Spring Security Implementation

### 8.1. SecurityConfig

Security configuration should enforce:

```text
1. `/api/v1/auth/login` is public.
2. `/api/v1/auth/refresh` is public but protected by refresh cookie validation inside AuthService.
3. `/api/v1/auth/logout` may be public-safe because it clears cookie, but it should still validate Origin/Referer for browser requests.
4. Other `/api/v1/**` endpoints require user access token.
5. `/internal/runner/**` requires runner service authentication, not user JWT.
6. CSRF may be disabled for bearer-token APIs, but refresh/logout need cookie-specific Origin/Referer checks.
7. CORS must allow credentials only for explicit trusted origins, never wildcard with credentials.
```

Example high-level config shape:

```java
@Bean
SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
  return http
      .csrf(csrf -> csrf.disable())
      .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
          .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
          .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
          .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
          .requestMatchers("/internal/runner/**").hasAuthority("RUNNER_SERVICE")
          .requestMatchers("/api/v1/**").authenticated()
          .anyRequest().denyAll())
      .build();
}
```

Implementation note:

```text
Use actual Spring Security filter ordering carefully. Do not let runner service tokens authenticate as normal users.
```

---

### 8.2. CurrentUser

Use a small immutable current user object.

```java
public record CurrentUser(
    UUID id,
    String email,
    String name,
    SystemRole systemRole,
    UserStatus status) {

  public boolean isPlatformAdmin() {
    return systemRole == SystemRole.PLATFORM_ADMIN;
  }

  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }
}
```

Controller can receive it by:

```text
1. `@AuthenticationPrincipal SecurityUserPrincipal principal`, then convert to CurrentUser.
2. A custom `@CurrentUser` argument resolver.
```

Pick one approach and use it consistently.

---

## 9. Permission Model Implementation

### 9.1. PermissionAction enum

Create stable action constants.

```java
public enum PermissionAction {
  VIEW_WORKSPACE,
  UPDATE_WORKSPACE,
  DELETE_WORKSPACE,
  MANAGE_WORKSPACE_MEMBERS,

  CREATE_PROJECT,
  VIEW_PROJECT,
  UPDATE_PROJECT,
  DELETE_PROJECT,

  IMPORT_DATASET,
  CONFIGURE_CHATBOT_TARGET,
  CONFIGURE_EVALUATOR,
  RUN_TEST,
  CANCEL_RUN,
  REVIEW_TEST_RESULT,
  EXPORT_TEST_RESULT,

  MANAGE_WORKSPACE_SECRET,
  MANAGE_WORKSPACE_PROVIDER,
  MANAGE_PERSONAL_SECRET,
  MANAGE_PERSONAL_PROVIDER,

  READ_PLAINTEXT_SECRET
}
```

Rules:

```text
1. READ_PLAINTEXT_SECRET must always be denied.
2. Add new actions explicitly. Do not use string literals scattered in services.
3. PermissionAction is internal backend code, not API enum.
```

---

### 9.2. WorkspacePermissionResolver

Service interface example:

```java
public interface WorkspacePermissionResolver {

  /**
   * Checks whether the current user can perform an action inside a workspace.
   *
   * @param currentUser authenticated user performing the action
   * @param workspaceId target workspace id
   * @param action permission action to evaluate
   * @return permission decision including workspace role when available
   */
  WorkspaceAccess check(CurrentUser currentUser, UUID workspaceId, PermissionAction action);

  /**
   * Requires permission for a workspace action and throws an API exception when denied.
   *
   * @param currentUser authenticated user performing the action
   * @param workspaceId target workspace id
   * @param action permission action to require
   * @return granted workspace access metadata
   * @throws ApiException with PERMISSION_DENIED when the user is authenticated but not allowed
   * @throws ApiException with WORKSPACE_NOT_FOUND when existence must be hidden from unauthorized users
   */
  WorkspaceAccess require(CurrentUser currentUser, UUID workspaceId, PermissionAction action);
}
```

Implementation rules:

```text
1. If action is READ_PLAINTEXT_SECRET, always deny.
2. If user is not ACTIVE, deny with USER_DISABLED or AUTHENTICATION_REQUIRED depending on context.
3. If PLATFORM_ADMIN and action is not READ_PLAINTEXT_SECRET, allow.
4. For normal USER, load ACTIVE workspace_members row.
5. If no active membership, deny.
6. Evaluate workspace role against permission matrix.
7. Return role/access metadata for response enrichment when useful.
```

Recommended access record:

```java
public record WorkspaceAccess(
    UUID workspaceId,
    UUID userId,
    SystemRole systemRole,
    WorkspaceRole workspaceRole,
    boolean platformAdmin,
    boolean allowed) {}
```

---

### 9.3. ProjectPermissionResolver

Project permission is derived from project -> workspace.

Service interface example:

```java
public interface ProjectPermissionResolver {

  /**
   * Requires permission for an action on a project and returns project access metadata.
   *
   * @param currentUser authenticated user performing the action
   * @param projectId target project id
   * @param action permission action to require
   * @return granted project access containing project id, workspace id and workspace role
   * @throws ApiException with PROJECT_NOT_FOUND if the project is missing or hidden by access policy
   * @throws ApiException with PERMISSION_DENIED if the project is visible but action is not allowed
   */
  ProjectAccess require(CurrentUser currentUser, UUID projectId, PermissionAction action);
}
```

Implementation rules:

```text
1. Load non-deleted project by id.
2. If not found, throw PROJECT_NOT_FOUND.
3. Use workspace resolver for project.workspaceId.
4. If user cannot view workspace, return PROJECT_NOT_FOUND to avoid leaking project existence.
5. If user can view but cannot perform action, return PERMISSION_DENIED.
```

Recommended access record:

```java
public record ProjectAccess(
    UUID projectId,
    UUID workspaceId,
    UUID userId,
    WorkspaceRole workspaceRole,
    boolean platformAdmin) {}
```

---

## 10. Permission Matrix

MVP workspace role matrix:

| Action | OWNER | ADMIN | MEMBER | VIEWER |
| ------ | :---: | :---: | :----: | :----: |
| View workspace/project/dashboard/result | Yes | Yes | Yes | Yes |
| Update workspace | Yes | Yes | No | No |
| Delete/archive workspace | Yes | No | No | No |
| Manage members | Yes | Yes | No | No |
| Create/update/delete project | Yes | Yes | Yes | No |
| Import/reuse/export dataset | Yes | Yes | Yes | No |
| Configure target/evaluator | Yes | Yes | Yes | No |
| Run/cancel test | Yes | Yes | Yes | No |
| Review/override result | Yes | Yes | Yes | No |
| Manage workspace secret/provider | Yes | Yes | No | No |
| Manage personal secret/provider | Yes | Yes | Yes | No |
| Read plaintext secret | No | No | No | No |

Implementation rule:

```text
Do not duplicate this matrix in every service. Implement it once in WorkspacePermissionResolver or a dedicated WorkspacePermissionMatrix component.
```

---

## 11. Error Handling

Recommended auth/permission error codes:

```text
AUTHENTICATION_REQUIRED
ACCESS_TOKEN_INVALID
ACCESS_TOKEN_EXPIRED
REFRESH_TOKEN_MISSING
REFRESH_TOKEN_INVALID
REFRESH_TOKEN_EXPIRED
INVALID_CREDENTIALS
USER_DISABLED
PERMISSION_DENIED
WORKSPACE_NOT_FOUND
PROJECT_NOT_FOUND
```

Rules:

```text
1. Use 401 for missing/invalid authentication.
2. Use 403 for authenticated but forbidden actions.
3. Use 404 to hide resources when the API contract requires existence hiding.
4. Use 409 for state conflicts, not permission denial.
5. ProblemDetail must include stable `code` and `messageKey`.
6. Do not return raw exception messages.
```

Example:

```json
{
  "type": "https://vfqc.vinfast.vn/problems/permission-denied",
  "title": "Permission denied",
  "status": 403,
  "detail": "You do not have permission to perform this action.",
  "code": "PERMISSION_DENIED",
  "messageKey": "error.permission.denied",
  "traceId": "01J0Z7Y4Y4F9E1S2Y9W8Q7P6A5"
}
```

---

## 12. Logging Rules

Auth logs must be useful but safe.

Allowed logs:

```java
log.info("Login successful: userId={}", user.getId());
log.warn("Login rejected for disabled user: userId={}", user.getId());
log.info("Refresh token rotated: userId={}, familyId={}", user.getId(), familyId);
log.warn("Permission denied: userId={}, workspaceId={}, action={}", userId, workspaceId, action);
```

Forbidden logs:

```java
log.info("Password: {}", request.password());
log.info("Access token: {}", accessToken.value());
log.info("Refresh token: {}", refreshToken.value());
log.info("Cookie header: {}", request.getHeader("Cookie"));
```

Log levels:

```text
1. Login success: info.
2. Invalid credentials: warn, but avoid noisy logs under brute-force attacks; consider rate-limited logging later.
3. Permission denied: warn for mutation attempts, debug for normal hidden-resource reads if too noisy.
4. Token validation failure: debug/warn depending on severity.
```

---

## 13. Transactions

Transaction boundary belongs in service implementation methods.

Recommended:

```text
AuthService.login: @Transactional
AuthService.refresh: @Transactional
AuthService.logout: @Transactional
AuthService.getCurrentUser: @Transactional(readOnly = true)
WorkspacePermissionResolver.check: @Transactional(readOnly = true) if it reads membership
ProjectPermissionResolver.require: @Transactional(readOnly = true) if it reads project/workspace membership
```

Do not put `@Transactional` on controllers.

---

## 14. Async and Redis Notes

This auth/permission implementation does not require Redis for normal request handling.

Rules:

```text
1. Do not store refresh tokens in Redis as the only source of truth unless product explicitly accepts losing revocation data.
2. Redis may be used later for rate limiting or login attempt throttling, but it is not part of MVP auth source of truth.
3. @Async is not needed for login/refresh/logout.
4. If audit logging is made async later, never pass plaintext password/tokens into async payloads.
```

Runner service authentication for `/internal/runner/**` may use a runner service token from configuration or a service credential table. That implementation belongs to `10-runner-internal-api.md` and runner backend docs, but it must stay separate from user JWT authentication.

---

## 15. Testing Checklist

Auth implementation tests should cover:

```text
1. Login success returns access token and Set-Cookie refresh token.
2. Login response body does not contain refresh token.
3. Wrong password returns INVALID_CREDENTIALS.
4. Unknown email returns INVALID_CREDENTIALS.
5. Disabled user returns USER_DISABLED.
6. Refresh with missing cookie returns REFRESH_TOKEN_MISSING and clears cookie.
7. Refresh with expired/revoked token returns proper error and clears cookie.
8. Refresh rotation revokes old token and issues a new one.
9. Logout clears cookie and revokes current token if present.
10. /auth/me returns only visible workspaces.
11. VIEWER cannot create/update/delete project.
12. MEMBER can create project but cannot manage workspace secrets.
13. PLATFORM_ADMIN cannot read plaintext secret.
14. User outside workspace receives hidden-resource behavior where required.
15. ProblemDetail contains code/messageKey/traceId.
```

---

## 16. AI Code Generation Checklist

Before generating auth/permission code, AI agents must check:

```text
1. Existing root package and build dependencies.
2. Existing Spring Security version/config style.
3. Whether refresh token persistence is implemented or stateless refresh JWT is chosen.
4. Existing ErrorCode/ApiException conventions.
5. Existing CurrentUser/Security principal pattern.
6. Whether MapStruct and Lombok are already configured.
```

When generating code:

```text
1. Use service interfaces with Javadoc.
2. Put implementations under service/impl.
3. Use @Slf4j on implementations.
4. Use @Transactional on service implementation methods.
5. Use records for DTOs.
6. Use validation and @Schema examples on request records.
7. Use MapStruct or assembler for responses.
8. Do not log credentials, cookies or tokens.
9. Do not hardcode Vietnamese text.
10. Keep runner service auth separate from user auth.
```
