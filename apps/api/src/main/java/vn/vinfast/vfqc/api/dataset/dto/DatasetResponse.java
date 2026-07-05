package vn.vinfast.vfqc.api.dataset.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vinfast.vfqc.api.dataset.enums.DatasetScope;

public record DatasetResponse(
    UUID id,
    DatasetScope scope,
    UUID projectId,
    UUID currentVersionId,
    String name,
    String description,
    UUID createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
