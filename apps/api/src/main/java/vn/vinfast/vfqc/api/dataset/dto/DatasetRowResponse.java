package vn.vinfast.vfqc.api.dataset.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record DatasetRowResponse(
    Long id,
    UUID datasetVersionId,
    int rowIndex,
    Map<String, Object> cells,
    int revision,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
