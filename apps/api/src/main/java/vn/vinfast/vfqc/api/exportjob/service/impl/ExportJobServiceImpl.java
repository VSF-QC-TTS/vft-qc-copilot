package vn.vinfast.vfqc.api.exportjob.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.service.DatasetExportService;
import vn.vinfast.vfqc.api.exportjob.dto.ExportJobResponse;
import vn.vinfast.vfqc.api.exportjob.entity.ExportJob;
import vn.vinfast.vfqc.api.exportjob.enums.ExportFormat;
import vn.vinfast.vfqc.api.exportjob.enums.ExportJobStatus;
import vn.vinfast.vfqc.api.exportjob.enums.ExportJobType;
import vn.vinfast.vfqc.api.exportjob.enums.ExportResultMode;
import vn.vinfast.vfqc.api.exportjob.event.ExportJobCreatedEvent;
import vn.vinfast.vfqc.api.exportjob.repository.ExportJobRepository;
import vn.vinfast.vfqc.api.exportjob.service.ExportJobService;
import vn.vinfast.vfqc.api.testrun.service.TestRunExportService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportJobServiceImpl implements ExportJobService {

  private final ExportJobRepository repository;
  private final DatasetExportService datasetExportService;
  private final TestRunExportService testRunExportService;
  private final ExportJobMapper mapper;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Override
  @Transactional
  public ExportJobResponse createDatasetExport(
      CurrentUser user, UUID datasetVersionId, ExportFormat format) {
    log.info("Request to create dataset export job: datasetVersionId={}, format={}, user={}", datasetVersionId, format, user.id());
    datasetExportService.requireCanExport(user, datasetVersionId);
    var job =
        repository.save(
            ExportJob.builder()
                .jobType(ExportJobType.DATASET_VERSION)
                .format(format == null ? ExportFormat.XLSX : format)
                .status(ExportJobStatus.PENDING)
                .datasetVersionId(datasetVersionId)
                .createdBy(user.id())
                .createdAt(OffsetDateTime.now(clock))
                .build());
    eventPublisher.publishEvent(new ExportJobCreatedEvent(job.getId()));
    return mapper.toResponse(job);
  }

  @Override
  @Transactional
  public ExportJobResponse createTestRunExport(
      CurrentUser user, UUID testRunId, ExportFormat format, ExportResultMode resultMode) {
    log.info("Request to create test run export job: testRunId={}, format={}, resultMode={}, user={}", testRunId, format, resultMode, user.id());
    testRunExportService.requireCanExport(user, testRunId);
    var job =
        repository.save(
            ExportJob.builder()
                .jobType(ExportJobType.TEST_RUN_RESULTS)
                .format(format == null ? ExportFormat.XLSX : format)
                .status(ExportJobStatus.PENDING)
                .testRunId(testRunId)
                .resultMode(resultMode == null ? ExportResultMode.FINAL : resultMode)
                .createdBy(user.id())
                .createdAt(OffsetDateTime.now(clock))
                .build());
    eventPublisher.publishEvent(new ExportJobCreatedEvent(job.getId()));
    return mapper.toResponse(job);
  }
}
