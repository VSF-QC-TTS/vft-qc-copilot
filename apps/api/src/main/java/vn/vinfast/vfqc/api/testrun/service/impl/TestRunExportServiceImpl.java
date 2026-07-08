package vn.vinfast.vfqc.api.testrun.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.ProjectPermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.entity.DatasetRow;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRowRepository;
import vn.vinfast.vfqc.api.exportjob.enums.ExportResultMode;
import vn.vinfast.vfqc.api.exportjob.model.ExportResultModel;
import vn.vinfast.vfqc.api.exportjob.model.ExportResultRowModel;
import vn.vinfast.vfqc.api.testrun.entity.TestCaseResult;
import vn.vinfast.vfqc.api.testrun.entity.TestCaseResultReview;
import vn.vinfast.vfqc.api.testrun.entity.TestRunTarget;
import vn.vinfast.vfqc.api.testrun.repository.TestCaseResultRepository;
import vn.vinfast.vfqc.api.testrun.repository.TestCaseResultReviewRepository;
import vn.vinfast.vfqc.api.testrun.repository.TestRunRepository;
import vn.vinfast.vfqc.api.testrun.repository.TestRunTargetRepository;
import vn.vinfast.vfqc.api.testrun.service.TestRunExportService;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestRunExportServiceImpl implements TestRunExportService {

  private static final int PAGE_SIZE = 500;

  private final TestRunRepository testRunRepository;
  private final TestRunTargetRepository targetRepository;
  private final TestCaseResultRepository resultRepository;
  private final TestCaseResultReviewRepository reviewRepository;
  private final DatasetRowRepository datasetRowRepository;
  private final ProjectPermissionService projectPermissionService;
  private final VfqcSensitiveDataMasker masker;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional(readOnly = true)
  public void requireCanExport(CurrentUser user, UUID testRunId) {
    log.info("Checking export permission for testRun={}", testRunId);
    var run =
        testRunRepository.findById(testRunId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    projectPermissionService.requireProject(user, run.getProjectId(), PermissionAction.VIEW);
  }

  @Override
  @Transactional(readOnly = true)
  public ExportResultModel buildExport(UUID testRunId, ExportResultMode resultMode) {
    log.info("Building test run export for testRun={}, resultMode={}", testRunId, resultMode);
    var run =
        testRunRepository.findById(testRunId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var targets =
        targetRepository.findByTestRunIdOrderByOrderIndexAsc(testRunId).stream()
            .collect(Collectors.toMap(TestRunTarget::getId, Function.identity()));
    var rows = new ArrayList<ExportResultRowModel>();
    Pageable pageable = PageRequest.of(0, PAGE_SIZE);
    while (true) {
      var page = resultRepository.findByTestRunIdOrderByIdAsc(testRunId, pageable);
      var results = page.getContent();
      var reviews =
          reviewRepository
              .findByTestCaseResultIdIn(results.stream().map(TestCaseResult::getId).toList())
              .stream()
              .collect(
                  Collectors.toMap(TestCaseResultReview::getTestCaseResultId, Function.identity()));
      var datasetRows =
          datasetRowRepository
              .findAllById(results.stream().map(TestCaseResult::getDatasetRowId).toList())
              .stream()
              .collect(Collectors.toMap(DatasetRow::getId, Function.identity()));
      for (var result : results) {
        var review = reviews.get(result.getId());
        var target = targets.get(result.getTestRunTargetId());
        var datasetRow = datasetRows.get(result.getDatasetRowId());
        var finalStatus = finalStatus(result.getStatus().name(), review, resultMode);
        var finalScore =
            resultMode == ExportResultMode.FINAL
                    && review != null
                    && review.getOverrideScore() != null
                ? review.getOverrideScore()
                : result.getScore();
        rows.add(
            new ExportResultRowModel(
                datasetRow == null ? -1 : datasetRow.getRowIndex(),
                target == null ? result.getTestRunTargetId().toString() : target.getName(),
                result.getStatus().name(),
                result.getScore(),
                finalStatus,
                finalScore,
                result.getPassed(),
                safeText(result.getErrorCategory()),
                safeText(result.getMachineSummary()),
                review == null ? null : review.getReviewStatus().name(),
                review == null ? null : safeText(review.getNote()),
                safeMap(result.getExtractedOutputJson()),
                result.getLatencyMs()));
      }
      if (!page.hasNext()) {
        break;
      }
      pageable = page.nextPageable();
    }
    rows.sort(Comparator.comparingInt(ExportResultRowModel::datasetRowIndex));
    return new ExportResultModel("test-run-" + run.getId(), rows);
  }

  private String finalStatus(
      String machineStatus, TestCaseResultReview review, ExportResultMode resultMode) {
    if (resultMode == ExportResultMode.FINAL
        && review != null
        && review.getOverrideStatus() != null) {
      return review.getOverrideStatus().name();
    }
    return machineStatus;
  }

  private String safeText(String text) {
    return masker.sanitizeText(text).value();
  }

  private String safeMap(Map<String, Object> value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(masker.sanitizeMap(value).value());
    } catch (JsonProcessingException exception) {
      return "{}";
    }
  }
}
