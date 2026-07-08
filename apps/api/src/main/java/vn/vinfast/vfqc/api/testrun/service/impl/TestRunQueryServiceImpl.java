package vn.vinfast.vfqc.api.testrun.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.api.CollectionResponse;
import vn.vinfast.vfqc.api.common.api.PageInfo;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.ProjectPermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.testrun.assembler.TestRunResponseAssembler;
import vn.vinfast.vfqc.api.testrun.dto.CancelTestRunRequest;
import vn.vinfast.vfqc.api.testrun.dto.CreateTestRunRequest;
import vn.vinfast.vfqc.api.testrun.dto.TestRunDetailResponse;
import vn.vinfast.vfqc.api.testrun.dto.TestRunSummaryResponse;
import vn.vinfast.vfqc.api.testrun.entity.TestRun;
import vn.vinfast.vfqc.api.testrun.entity.TestRunEvaluatorVersion;
import vn.vinfast.vfqc.api.testrun.entity.TestRunTarget;
import vn.vinfast.vfqc.api.testrun.enums.TestRunStatus;
import vn.vinfast.vfqc.api.testrun.repository.TestRunEvaluatorVersionRepository;
import vn.vinfast.vfqc.api.testrun.repository.TestRunRepository;
import vn.vinfast.vfqc.api.testrun.repository.TestRunTargetRepository;
import vn.vinfast.vfqc.api.testrun.service.TestRunCommandService;
import vn.vinfast.vfqc.api.testrun.service.TestRunHashService;
import vn.vinfast.vfqc.api.testrun.service.TestRunQueryService;
import vn.vinfast.vfqc.api.testrun.service.TestRunSnapshotService;
import vn.vinfast.vfqc.api.testrun.state.TestRunStateMachine;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestRunQueryServiceImpl implements TestRunQueryService {

  private final TestRunRepository testRunRepository;
  private final TestRunTargetRepository targetRepository;
  private final TestRunEvaluatorVersionRepository evaluatorVersionRepository;
  private final ProjectPermissionService projectPermissionService;
  private final TestRunSnapshotService snapshotService;
  private final TestRunHashService hashService;
  private final TestRunCommandService commandService;
  private final TestRunStateMachine stateMachine;
  private final TestRunResponseAssembler assembler;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public CollectionResponse<TestRunSummaryResponse> list(
      CurrentUser user, UUID projectId, Pageable pageable) {
    projectPermissionService.requireProject(user, projectId, PermissionAction.VIEW);
    var page = testRunRepository.findByProjectIdOrderByCreatedAtDesc(projectId, pageable);
    return new CollectionResponse<>(
        page.getContent().stream().map(assembler::toSummary).toList(),
        new PageInfo(page.getSize(), null, page.hasNext()));
  }

  @Override
  @Transactional
  public TestRunDetailResponse create(CurrentUser user, UUID projectId, CreateTestRunRequest request) {
    projectPermissionService.requireProject(user, projectId, PermissionAction.EDIT);
    var locked = snapshotService.lock(user, projectId, request);
    var now = OffsetDateTime.now(clock);
    var run =
        testRunRepository.save(
            TestRun.builder()
                .projectId(projectId)
                .runType(locked.runType())
                .datasetId(locked.datasetId())
                .datasetVersionId(locked.datasetVersionId())
                .datasetSchemaId(locked.datasetSchemaId())
                .datasetSchemaVersion(locked.datasetSchemaVersion())
                .judgeProviderId(locked.judgeProviderId())
                .judgeModel(locked.judgeModel())
                .status(TestRunStatus.CREATED)
                .progressCompleted(0)
                .progressTotal(locked.rowCount() * Math.max(1, locked.targets().size()))
                .summaryMetricsJson(Map.of())
                .domainConfigSnapshotJson(locked.domainSnapshot())
                .promptfooConfigSnapshotJson(locked.promptfooSnapshot())
                .domainConfigHash(hashService.hash(locked.domainSnapshot()))
                .promptfooConfigHash(hashService.hash(locked.promptfooSnapshot()))
                .startedBy(user.id())
                .createdAt(now)
                .updatedAt(now)
                .build());
    var targets =
        locked.targets().stream()
            .map(
                target ->
                    targetRepository.save(
                        TestRunTarget.builder()
                            .testRunId(run.getId())
                            .targetType(target.targetType())
                            .chatbotTargetId(target.chatbotTargetId())
                            .providerId(target.providerId())
                            .model(target.model())
                            .name(target.name())
                            .orderIndex(target.orderIndex())
                            .configJson(target.config())
                            .createdAt(now)
                            .build()))
            .toList();
    var evaluators =
        locked.evaluators().stream()
            .map(
                evaluator ->
                    evaluatorVersionRepository.save(
                        TestRunEvaluatorVersion.builder()
                            .testRunId(run.getId())
                            .evaluatorId(evaluator.evaluatorId())
                            .evaluatorVersionId(evaluator.evaluatorVersionId())
                            .evaluatorType(evaluator.evaluatorType())
                            .version(evaluator.version())
                            .orderIndex(evaluator.orderIndex())
                            .configJson(evaluator.config())
                            .rulesJson(evaluator.rules())
                            .createdAt(now)
                            .build()))
            .toList();
    commandService.afterRunCreated(run);
    log.info("TestRun created: testRunId={} projectId={} actorId={}", run.getId(), projectId, user.id());
    return assembler.toDetail(run, targets, evaluators);
  }

  @Override
  @Transactional(readOnly = true)
  public TestRunDetailResponse get(CurrentUser user, UUID testRunId) {
    var run = requireRun(user, testRunId, PermissionAction.VIEW);
    return detail(run);
  }

  @Override
  @Transactional
  public TestRunDetailResponse cancel(CurrentUser user, UUID testRunId, CancelTestRunRequest request) {
    var run = requireRun(user, testRunId, PermissionAction.EDIT);
    var next =
        run.getStatus() == TestRunStatus.RUNNING
            ? TestRunStatus.CANCEL_REQUESTED
            : TestRunStatus.CANCELLED;
    stateMachine.requireTransition(run.getStatus(), next);
    var now = OffsetDateTime.now(clock);
    run.setStatus(next);
    run.setCancelRequestedAt(now);
    run.setUpdatedAt(now);
    if (next == TestRunStatus.CANCEL_REQUESTED) {
      commandService.afterRunCancellationRequested(run);
    }
    log.info("TestRun cancellation requested: testRunId={} actorId={}", testRunId, user.id());
    return detail(run);
  }

  @Override
  public TestRun requireRun(CurrentUser user, UUID testRunId, PermissionAction action) {
    var run = testRunRepository.findById(testRunId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    projectPermissionService.requireProject(user, run.getProjectId(), action);
    return run;
  }

  private TestRunDetailResponse detail(TestRun run) {
    return assembler.toDetail(
        run,
        targetRepository.findByTestRunIdOrderByOrderIndexAsc(run.getId()),
        evaluatorVersionRepository.findByTestRunIdOrderByOrderIndexAsc(run.getId()));
  }
}
