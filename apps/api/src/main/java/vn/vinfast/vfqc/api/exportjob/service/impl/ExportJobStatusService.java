package vn.vinfast.vfqc.api.exportjob.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.exportjob.entity.ExportJob;
import vn.vinfast.vfqc.api.exportjob.enums.ExportJobStatus;
import vn.vinfast.vfqc.api.exportjob.repository.ExportJobRepository;

@Service
@RequiredArgsConstructor
class ExportJobStatusService {

  private final ExportJobRepository repository;
  private final Clock clock;

  @Transactional
  public ExportJob markRunning(UUID jobId) {
    var job = repository.findById(jobId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    if (job.getStatus() != ExportJobStatus.PENDING) {
      throw ErrorCode.INVALID_STATE.throwIt("Export job is not pending");
    }
    job.setStatus(ExportJobStatus.RUNNING);
    job.setStartedAt(OffsetDateTime.now(clock));
    return job;
  }

  @Transactional
  public void markCompleted(
      UUID jobId,
      String filePath,
      String fileName,
      long fileSizeBytes,
      OffsetDateTime expiresAt) {
    var job = repository.findById(jobId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    job.setStatus(ExportJobStatus.COMPLETED);
    job.setFilePath(filePath);
    job.setFileName(fileName);
    job.setFileSizeBytes(fileSizeBytes);
    job.setFinishedAt(OffsetDateTime.now(clock));
    job.setExpiresAt(expiresAt);
  }

  @Transactional
  public void markFailed(UUID jobId, String errorCode, String errorMessage) {
    var job = repository.findById(jobId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    job.setStatus(ExportJobStatus.FAILED);
    job.setErrorCode(errorCode);
    job.setErrorMessage(errorMessage);
    job.setFinishedAt(OffsetDateTime.now(clock));
  }
}
