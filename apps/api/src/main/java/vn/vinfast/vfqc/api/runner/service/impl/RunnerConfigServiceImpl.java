package vn.vinfast.vfqc.api.runner.service.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRowRepository;
import vn.vinfast.vfqc.api.runner.dto.RunnerControlStateResponse;
import vn.vinfast.vfqc.api.runner.dto.RunnerTestRunConfigResponse;
import vn.vinfast.vfqc.api.runner.mapper.RunnerMapper;
import vn.vinfast.vfqc.api.runner.service.RunnerConfigService;
import vn.vinfast.vfqc.api.testrun.enums.TestRunStatus;
import vn.vinfast.vfqc.api.testrun.repository.TestRunEvaluatorVersionRepository;
import vn.vinfast.vfqc.api.testrun.repository.TestRunRepository;
import vn.vinfast.vfqc.api.testrun.repository.TestRunTargetRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunnerConfigServiceImpl implements RunnerConfigService {

  private final TestRunRepository testRunRepository;
  private final TestRunTargetRepository targetRepository;
  private final TestRunEvaluatorVersionRepository evaluatorRepository;
  private final DatasetRowRepository rowRepository;
  private final RunnerMapper mapper;

  @Override
  @Transactional(readOnly = true)
  public RunnerTestRunConfigResponse config(UUID testRunId, Pageable pageable) {
    log.info("Requesting test run config for testRunId={}, page={}", testRunId, pageable != null ? pageable.getPageNumber() : 0);
    var run = testRunRepository.findById(testRunId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var rows =
        rowRepository.findByDatasetVersionIdOrderByRowIndexAsc(run.getDatasetVersionId(), pageable).stream()
            .map(mapper::toRowSnapshot)
            .toList();
    var targets =
        targetRepository.findByTestRunIdOrderByOrderIndexAsc(testRunId).stream()
            .map(mapper::toTargetSnapshot)
            .toList();
    var evaluators =
        evaluatorRepository.findByTestRunIdOrderByOrderIndexAsc(testRunId).stream()
            .map(mapper::toEvaluatorSnapshot)
            .toList();
    return new RunnerTestRunConfigResponse(
        run.getId(),
        run.getProjectId(),
        run.getRunType(),
        run.getStatus(),
        run.getDatasetId(),
        run.getDatasetVersionId(),
        run.getDatasetSchemaId(),
        run.getDatasetSchemaVersion(),
        run.getProgressTotal(),
        run.getDomainConfigSnapshotJson(),
        run.getPromptfooConfigSnapshotJson(),
        targets,
        evaluators,
        rows);
  }

  @Override
  @Transactional(readOnly = true)
  public RunnerControlStateResponse controlState(UUID testRunId) {
    log.info("Requesting control state for testRunId={}", testRunId);
    var run = testRunRepository.findById(testRunId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    return new RunnerControlStateResponse(
        run.getId(),
        run.getStatus(),
        run.getStatus() == TestRunStatus.CANCEL_REQUESTED || run.getCancelRequestedAt() != null);
  }
}

