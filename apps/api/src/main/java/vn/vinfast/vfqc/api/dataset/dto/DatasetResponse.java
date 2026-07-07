package vn.vinfast.vfqc.api.dataset.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vinfast.vfqc.api.dataset.enums.DatasetScope;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;

public record DatasetResponse(
    UUID id,
    DatasetScope scope,
    UUID projectId,
    UUID currentVersionId,
    String name,
    String description,
    UUID createdBy,
    UserSummaryResponse createdByUser,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
