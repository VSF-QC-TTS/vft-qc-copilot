package vn.vinfast.vfqc.api.exportjob.service.impl;

import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.exportjob.dto.ExportJobResponse;
import vn.vinfast.vfqc.api.exportjob.entity.ExportJob;

@Component
public class ExportJobMapper {

  public ExportJobResponse toResponse(ExportJob job) {
    return new ExportJobResponse(
        job.getId(),
        job.getJobType(),
        job.getFormat(),
        job.getStatus(),
        job.getDatasetVersionId(),
        job.getTestRunId(),
        job.getResultMode(),
        job.getFileName(),
        job.getFileSizeBytes(),
        job.getErrorCode(),
        job.getErrorMessage(),
        job.getCreatedBy(),
        job.getCreatedAt(),
        job.getStartedAt(),
        job.getFinishedAt(),
        job.getExpiresAt());
  }
}
