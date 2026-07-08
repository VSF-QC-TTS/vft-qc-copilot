package vn.vinfast.vfqc.api.exportjob.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.common.storage.StorageService;
import vn.vinfast.vfqc.api.dataset.service.DatasetExportService;
import vn.vinfast.vfqc.api.exportjob.enums.ExportJobStatus;
import vn.vinfast.vfqc.api.exportjob.enums.ExportJobType;
import vn.vinfast.vfqc.api.exportjob.model.ExportDownload;
import vn.vinfast.vfqc.api.exportjob.repository.ExportJobRepository;
import vn.vinfast.vfqc.api.exportjob.service.ExportDownloadService;
import vn.vinfast.vfqc.api.testrun.service.TestRunExportService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportDownloadServiceImpl implements ExportDownloadService {

  private static final String XLSX_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private final ExportJobRepository repository;
  private final DatasetExportService datasetExportService;
  private final TestRunExportService testRunExportService;
  private final StorageService storageService;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public ExportDownload download(CurrentUser user, UUID jobId) {
    log.info("Processing export file download request: jobId={}, user={}", jobId, user.id());
    var job = repository.findById(jobId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    if (job.getJobType() == ExportJobType.DATASET_VERSION) {
      datasetExportService.requireCanExport(user, job.getDatasetVersionId());
    } else {
      testRunExportService.requireCanExport(user, job.getTestRunId());
    }
    if (job.getStatus() != ExportJobStatus.COMPLETED) {
      throw ErrorCode.RESOURCE_NOT_FOUND.throwIt();
    }
    if (job.getExpiresAt() != null && !job.getExpiresAt().isAfter(OffsetDateTime.now(clock))) {
      throw ErrorCode.RESOURCE_NOT_FOUND.throwIt();
    }
    var file = storageService.load(job.getFilePath());
    return new ExportDownload(file.resource(), job.getFileName(), XLSX_CONTENT_TYPE, file.sizeBytes());
  }
}
