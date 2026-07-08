package vn.vinfast.vfqc.api.exportjob.model;

import java.util.UUID;
import vn.vinfast.vfqc.api.exportjob.enums.ExportFormat;
import vn.vinfast.vfqc.api.exportjob.enums.ExportJobType;
import vn.vinfast.vfqc.api.exportjob.enums.ExportResultMode;

public record ExportJobCreateCommand(
    ExportJobType jobType,
    ExportFormat format,
    UUID datasetVersionId,
    UUID testRunId,
    ExportResultMode resultMode,
    UUID createdBy) {}
