package vn.vinfast.vfqc.api.dataset.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import vn.vinfast.vfqc.api.dataset.enums.DatasetImportStatus;
import vn.vinfast.vfqc.api.dataset.enums.DatasetSourceType;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;

public record DatasetImportJobResponse(
    UUID id,
    UUID targetProjectId,
    UUID targetDatasetId,
    DatasetSourceType sourceType,
    String sourceFileName,
    DatasetImportStatus status,
    Map<String, Object> schemaCompatibility,
    Map<String, Object> preview,
    Map<String, Object> mapping,
    String errorMessage,
    UUID createdBy,
    UserSummaryResponse createdByUser,
    OffsetDateTime createdAt,
    OffsetDateTime finishedAt) {}
