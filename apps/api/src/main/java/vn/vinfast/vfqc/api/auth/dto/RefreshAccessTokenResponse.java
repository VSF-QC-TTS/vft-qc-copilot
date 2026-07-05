package vn.vinfast.vfqc.api.auth.dto;

public record RefreshAccessTokenResponse(String accessToken, String tokenType, long expiresIn) {}
