package vn.vinfast.vfqc.api.dataset.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import vn.vinfast.vfqc.api.dataset.enums.DatasetSchemaScope;

public record DatasetSchemaResponse(
    UUID id,
    DatasetSchemaScope scope,
    UUID projectId,
    int version,
    String name,
    List<DatasetSchemaColumnResponse> columns,
    OffsetDateTime createdAt) {}
