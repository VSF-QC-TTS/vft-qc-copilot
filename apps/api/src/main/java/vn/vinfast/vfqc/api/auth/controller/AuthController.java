package vn.vinfast.vfqc.api.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vinfast.vfqc.api.auth.dto.AuthMessageResponse;
import vn.vinfast.vfqc.api.auth.dto.CurrentUserResponse;
import vn.vinfast.vfqc.api.auth.dto.ForgotPasswordRequest;
import vn.vinfast.vfqc.api.auth.dto.LoginRequest;
import vn.vinfast.vfqc.api.auth.dto.LoginResponse;
import vn.vinfast.vfqc.api.auth.dto.RefreshAccessTokenResponse;
import vn.vinfast.vfqc.api.auth.dto.RegisterRequest;
import vn.vinfast.vfqc.api.auth.dto.RegisterResponse;
import vn.vinfast.vfqc.api.auth.dto.ResetPasswordRequest;
import vn.vinfast.vfqc.api.auth.dto.VerifyEmailRequest;
import vn.vinfast.vfqc.api.auth.service.AuthService;
import vn.vinfast.vfqc.api.auth.service.RefreshTokenIssue;
import vn.vinfast.vfqc.api.auth.service.RefreshTokenService;
import vn.vinfast.vfqc.api.common.config.SecurityProperties;
import vn.vinfast.vfqc.api.common.security.CurrentUser;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication, refresh token, logout, and current user APIs")
public class AuthController {

  private static final String REFRESH_COOKIE = "vfqc_refresh_token";

  private final AuthService authService;
  private final RefreshTokenService refreshTokenService;
  private final SecurityProperties securityProperties;

  @GetMapping("/csrf")
  @Operation(summary = "Get CSRF token for browser clients")
  @ApiResponse(responseCode = "200", description = "CSRF token returned")
  public CsrfToken csrf(CsrfToken csrfToken) {
    return csrfToken;
  }

  @PostMapping("/register")
  @Operation(summary = "Register a local account when self-registration is enabled")
  @ApiResponse(
      responseCode = "200",
      description = "Registration accepted and verification email sent")
  @ApiResponse(responseCode = "403", description = "Self-registration is disabled")
  @ApiResponse(responseCode = "409", description = "Email already exists")
  public RegisterResponse register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
    return authService.register(
        request, servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"));
  }

  @PostMapping("/login")
  @Operation(summary = "Login with email and password")
  @ApiResponse(responseCode = "200", description = "Authenticated and refresh cookie set")
  @ApiResponse(responseCode = "401", description = "Invalid credentials")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    var result =
        authService.login(
            request, servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
        .body(result.response());
  }

  @PostMapping("/refresh")
  @Operation(summary = "Refresh access token")
  @ApiResponse(
      responseCode = "200",
      description = "Access token refreshed and refresh cookie rotated")
  @ApiResponse(responseCode = "401", description = "Refresh token missing, invalid, or expired")
  public ResponseEntity<RefreshAccessTokenResponse> refresh(HttpServletRequest request) {
    var result =
        authService.refresh(
            cookieValue(request), request.getRemoteAddr(), request.getHeader("User-Agent"));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
        .body(result.response());
  }

  @PostMapping("/verify-email")
  @Operation(summary = "Verify account email")
  @ApiResponse(responseCode = "200", description = "Email verified")
  @ApiResponse(responseCode = "400", description = "Token missing, invalid, or expired")
  public AuthMessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
    return authService.verifyEmail(request);
  }

  @PostMapping("/password/forgot")
  @Operation(summary = "Request a password reset email")
  @ApiResponse(responseCode = "200", description = "Request accepted")
  public AuthMessageResponse forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest servletRequest) {
    return authService.forgotPassword(
        request, servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"));
  }

  @PostMapping("/password/reset")
  @Operation(summary = "Reset password using a one-time token")
  @ApiResponse(responseCode = "200", description = "Password reset")
  @ApiResponse(responseCode = "400", description = "Token missing, invalid, or expired")
  public AuthMessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    return authService.resetPassword(request);
  }

  @PostMapping("/logout")
  @Operation(summary = "Logout and revoke refresh token")
  @ApiResponse(responseCode = "204", description = "Refresh token revoked and cookie cleared")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
    refreshTokenService.revoke(cookieValue(request));
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
        .build();
  }

  @GetMapping("/me")
  @Operation(summary = "Get current authenticated user")
  @ApiResponse(responseCode = "200", description = "Current user returned")
  @ApiResponse(responseCode = "401", description = "Authentication required")
  public CurrentUserResponse me(@AuthenticationPrincipal CurrentUser currentUser) {
    return authService.currentUser(currentUser);
  }

  private String cookieValue(HttpServletRequest request) {
    var cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (REFRESH_COOKIE.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  private ResponseCookie refreshCookie(RefreshTokenIssue token) {
    return ResponseCookie.from(REFRESH_COOKIE, token.token())
        .httpOnly(true)
        .secure(securityProperties.cookie().secure())
        .sameSite(securityProperties.cookie().sameSite())
        .path("/api/v1/auth")
        .maxAge(token.maxAgeSeconds())
        .build();
  }

  private ResponseCookie clearCookie() {
    return ResponseCookie.from(REFRESH_COOKIE, "")
        .httpOnly(true)
        .secure(securityProperties.cookie().secure())
        .sameSite(securityProperties.cookie().sameSite())
        .path("/api/v1/auth")
        .maxAge(0)
        .build();
  }
}
