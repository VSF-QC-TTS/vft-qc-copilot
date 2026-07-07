package vn.vinfast.vfqc.api.dataset.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vinfast.vfqc.api.dataset.enums.DatasetVersionStatus;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;

public record DatasetVersionResponse(
    UUID id,
    UUID datasetId,
    int version,
    UUID schemaId,
    int schemaVersion,
    int rowCount,
    DatasetVersionStatus status,
    UUID createdBy,
    UserSummaryResponse createdByUser,
    OffsetDateTime createdAt,
    OffsetDateTime publishedAt,
    OffsetDateTime discardedAt) {}
