package vn.vinfast.vfqc.api.dataset.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vinfast.vfqc.api.dataset.enums.DatasetVersionStatus;

public record DatasetVersionResponse(
    UUID id,
    UUID datasetId,
    int version,
    UUID schemaId,
    int schemaVersion,
    int rowCount,
    DatasetVersionStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime publishedAt,
    OffsetDateTime discardedAt) {}
