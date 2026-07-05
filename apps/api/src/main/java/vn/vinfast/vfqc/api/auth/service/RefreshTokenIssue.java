package vn.vinfast.vfqc.api.auth.service;

public record RefreshTokenIssue(String token, long maxAgeSeconds) {}
