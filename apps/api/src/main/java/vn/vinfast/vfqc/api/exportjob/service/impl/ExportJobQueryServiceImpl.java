package vn.vinfast.vfqc.api.exportjob.service.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.api.CollectionResponse;
import vn.vinfast.vfqc.api.common.api.PageInfo;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.service.DatasetExportService;
import vn.vinfast.vfqc.api.exportjob.dto.ExportJobResponse;
import vn.vinfast.vfqc.api.exportjob.entity.ExportJob;
import vn.vinfast.vfqc.api.exportjob.enums.ExportJobType;
import vn.vinfast.vfqc.api.exportjob.mapper.ExportJobMapper;
import vn.vinfast.vfqc.api.exportjob.repository.ExportJobRepository;
import vn.vinfast.vfqc.api.exportjob.service.ExportJobQueryService;
import vn.vinfast.vfqc.api.testrun.service.TestRunExportService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportJobQueryServiceImpl implements ExportJobQueryService {

  private final ExportJobRepository repository;
  private final DatasetExportService datasetExportService;
  private final TestRunExportService testRunExportService;
  private final ExportJobMapper mapper;

  @Override
  @Transactional(readOnly = true)
  public ExportJobResponse get(CurrentUser user, UUID jobId) {
    log.info("Querying export job status: jobId={}, user={}", jobId, user.id());
    var job = repository.findById(jobId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    requireSourceAccess(user, job);
    return mapper.toResponse(job);
  }

  @Override
  @Transactional(readOnly = true)
  public CollectionResponse<ExportJobResponse> listDatasetVersionExports(
      CurrentUser user, UUID datasetVersionId, Pageable pageable) {
    log.info(
        "Listing dataset export jobs: datasetVersionId={}, page={}, size={}, user={}",
        datasetVersionId,
        pageable.getPageNumber(),
        pageable.getPageSize(),
        user.id());
    datasetExportService.requireCanExport(user, datasetVersionId);
    var page = repository.findByDatasetVersionIdOrderByCreatedAtDesc(datasetVersionId, pageable);
    return new CollectionResponse<>(
        page.getContent().stream().map(mapper::toResponse).toList(),
        new PageInfo(page.getSize(), null, page.hasNext()));
  }

  @Override
  @Transactional(readOnly = true)
  public CollectionResponse<ExportJobResponse> listTestRunExports(
      CurrentUser user, UUID testRunId, Pageable pageable) {
    log.info(
        "Listing test run export jobs: testRunId={}, page={}, size={}, user={}",
        testRunId,
        pageable.getPageNumber(),
        pageable.getPageSize(),
        user.id());
    testRunExportService.requireCanExport(user, testRunId);
    var page = repository.findByTestRunIdOrderByCreatedAtDesc(testRunId, pageable);
    return new CollectionResponse<>(
        page.getContent().stream().map(mapper::toResponse).toList(),
        new PageInfo(page.getSize(), null, page.hasNext()));
  }

  private void requireSourceAccess(CurrentUser user, ExportJob job) {
    if (job.getJobType() == ExportJobType.DATASET_VERSION) {
      datasetExportService.requireCanExport(user, job.getDatasetVersionId());
      return;
    }
    testRunExportService.requireCanExport(user, job.getTestRunId());
  }
}
