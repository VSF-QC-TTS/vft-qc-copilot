package vn.vinfast.vfqc.api.exportjob.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import vn.vinfast.vfqc.api.exportjob.enums.ExportFormat;
import vn.vinfast.vfqc.api.exportjob.enums.ExportJobStatus;
import vn.vinfast.vfqc.api.exportjob.enums.ExportJobType;
import vn.vinfast.vfqc.api.exportjob.enums.ExportResultMode;

public record ExportJobResponse(
    UUID id,
    ExportJobType jobType,
    ExportFormat format,
    ExportJobStatus status,
    UUID datasetVersionId,
    UUID testRunId,
    ExportResultMode resultMode,
    String fileName,
    Long fileSizeBytes,
    String errorCode,
    String errorMessage,
    UUID createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime expiresAt) {}
