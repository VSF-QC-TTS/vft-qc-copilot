package vn.vinfast.vfqc.api.project.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectResponse(
    UUID id,
    UUID workspaceId,
    String name,
    String description,
    UUID createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime archivedAt) {}
