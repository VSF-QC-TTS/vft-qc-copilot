package vn.vinfast.vfqc.api.provider.dto;

import java.util.UUID;
import vn.vinfast.vfqc.api.provider.enums.ProviderScope;
import vn.vinfast.vfqc.api.provider.enums.ProviderStatus;
import vn.vinfast.vfqc.api.provider.enums.ProviderType;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;

public record LlmProviderSummaryResponse(
    UUID id,
    ProviderScope scope,
    UUID ownerUserId,
    UserSummaryResponse ownerUser,
    UUID workspaceId,
    String name,
    ProviderType providerType,
    String defaultModel,
    ProviderStatus status) {}
