package vn.vinfast.vfqc.api.exportjob.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.common.error.ApiException;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.common.storage.StorageProperties;
import vn.vinfast.vfqc.api.common.storage.StorageService;
import vn.vinfast.vfqc.api.dataset.service.DatasetExportService;
import vn.vinfast.vfqc.api.exportjob.entity.ExportJob;
import vn.vinfast.vfqc.api.exportjob.enums.ExportJobType;
import vn.vinfast.vfqc.api.exportjob.model.ExportFileWriteResult;
import vn.vinfast.vfqc.api.exportjob.service.ExportFileNamePolicy;
import vn.vinfast.vfqc.api.exportjob.service.ExportFileWriter;
import vn.vinfast.vfqc.api.exportjob.service.ExportJobWorker;
import vn.vinfast.vfqc.api.testrun.service.TestRunExportService;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportJobWorkerImpl implements ExportJobWorker {

  private final ExportJobStatusService statusService;
  private final DatasetExportService datasetExportService;
  private final TestRunExportService testRunExportService;
  private final ExportFileWriter writer;
  private final ExportFileNamePolicy fileNamePolicy;
  private final StorageService storageService;
  private final StorageProperties storageProperties;
  private final VfqcSensitiveDataMasker masker;
  private final Clock clock;

  @Override
  public void run(UUID jobId) {
    ExportJob job = null;
    try {
      job = statusService.markRunning(jobId);
      var file = buildFile(job);
      var fileName = fileNamePolicy.fileNameFor(job);
      var stored = storageService.store(fileNamePolicy.storagePathFor(job, fileName), file.content());
      statusService.markCompleted(
          jobId,
          stored.relativePath(),
          fileName,
          stored.sizeBytes(),
          OffsetDateTime.now(clock).plus(storageProperties.expiration()));
      log.info("Export job completed: exportJobId={} type={}", jobId, job.getJobType());
    } catch (Exception exception) {
      var errorCode = exception instanceof ApiException apiException
          ? apiException.getErrorCode().name()
          : "EXPORT_FAILED";
      var message = safeErrorMessage(exception.getMessage());
      statusService.markFailed(jobId, errorCode, message);
      log.warn("Export job failed: exportJobId={} errorCode={}", jobId, errorCode);
    }
  }

  private ExportFileWriteResult buildFile(ExportJob job) {
    if (job.getJobType() == ExportJobType.DATASET_VERSION) {
      return writer.writeDataset(datasetExportService.buildExport(job.getDatasetVersionId()));
    }
    return writer.writeResults(testRunExportService.buildExport(job.getTestRunId(), job.getResultMode()));
  }

  private String safeErrorMessage(String message) {
    var sanitized = masker.sanitizeText(message == null ? "Export failed" : message).value();
    return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
  }
}
