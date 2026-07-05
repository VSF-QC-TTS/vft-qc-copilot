package vn.vinfast.vfqc.api.auth.service;

import java.time.OffsetDateTime;

public record AuthEmailTokenIssue(String token, OffsetDateTime expiresAt) {}
