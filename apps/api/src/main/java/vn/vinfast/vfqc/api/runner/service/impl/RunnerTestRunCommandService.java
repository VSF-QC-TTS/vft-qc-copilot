package vn.vinfast.vfqc.api.runner.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.runner.entity.RunnerCommand;
import vn.vinfast.vfqc.api.runner.enums.RunnerCommandStatus;
import vn.vinfast.vfqc.api.runner.enums.RunnerCommandType;
import vn.vinfast.vfqc.api.runner.repository.RunnerCommandRepository;
import vn.vinfast.vfqc.api.testrun.entity.TestRun;
import vn.vinfast.vfqc.api.testrun.enums.TestRunStatus;
import vn.vinfast.vfqc.api.testrun.service.TestRunCommandService;
import vn.vinfast.vfqc.api.testrun.state.TestRunStateMachine;

@Service
@RequiredArgsConstructor
@Slf4j
public class RunnerTestRunCommandService implements TestRunCommandService {

  private final RunnerCommandRepository commandRepository;
  private final TestRunStateMachine stateMachine;
  private final Clock clock;

  @Override
  public void afterRunCreated(TestRun testRun) {
    stateMachine.requireTransition(testRun.getStatus(), TestRunStatus.QUEUED);
    var now = OffsetDateTime.now(clock);
    testRun.setStatus(TestRunStatus.QUEUED);
    testRun.setUpdatedAt(now);
    command(testRun, RunnerCommandType.START_RUN, now);
  }

  @Override
  public void afterRunCancellationRequested(TestRun testRun) {
    var now = OffsetDateTime.now(clock);
    command(testRun, RunnerCommandType.CANCEL_RUN, now);
  }

  private RunnerCommand command(TestRun testRun, RunnerCommandType type, OffsetDateTime now) {
    var idempotencyKey = testRun.getId() + ":" + type;
    return commandRepository
        .findByIdempotencyKey(idempotencyKey)
        .orElseGet(
            () ->
                commandRepository.save(
                    RunnerCommand.builder()
                        .testRunId(testRun.getId())
                        .commandType(type)
                        .idempotencyKey(idempotencyKey)
                        .status(RunnerCommandStatus.PENDING)
                        .attempts(0)
                        .createdAt(now)
                        .updatedAt(now)
                        .build()));
  }

}
