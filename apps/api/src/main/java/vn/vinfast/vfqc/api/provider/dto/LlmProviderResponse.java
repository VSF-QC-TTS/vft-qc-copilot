package vn.vinfast.vfqc.api.provider.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import vn.vinfast.vfqc.api.provider.enums.ProviderScope;
import vn.vinfast.vfqc.api.provider.enums.ProviderStatus;
import vn.vinfast.vfqc.api.provider.enums.ProviderType;
import vn.vinfast.vfqc.api.secret.dto.SecretSummaryResponse;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;

public record LlmProviderResponse(
    UUID id,
    ProviderScope scope,
    UUID ownerUserId,
    UserSummaryResponse ownerUser,
    UUID workspaceId,
    String name,
    ProviderType providerType,
    SecretSummaryResponse secret,
    String baseUrl,
    String defaultModel,
    Map<String, Object> availableModels,
    Map<String, Object> config,
    ProviderStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
