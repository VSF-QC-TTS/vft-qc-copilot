package vn.vinfast.vfqc.api.auth.dto;

public record LoginResponse(
    String accessToken, String tokenType, long expiresIn, CurrentUserResponse user) {}
