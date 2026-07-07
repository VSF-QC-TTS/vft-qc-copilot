package vn.vinfast.vfqc.api.user.dto;

import java.util.UUID;

public record UserSummaryResponse(UUID id, String email, String name, String avatarUrl) {}
