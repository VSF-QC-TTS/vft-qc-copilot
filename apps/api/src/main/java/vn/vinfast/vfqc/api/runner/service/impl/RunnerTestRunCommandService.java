package vn.vinfast.vfqc.api.runner.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.runner.config.RunnerRedisStreams;
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
  private final StringRedisTemplate redisTemplate;
  private final TestRunStateMachine stateMachine;
  private final VfqcSensitiveDataMasker masker;
  private final Clock clock;
  private final PlatformTransactionManager transactionManager;

  @Override
  public void afterRunCreated(TestRun testRun) {
    stateMachine.requireTransition(testRun.getStatus(), TestRunStatus.QUEUED);
    var now = OffsetDateTime.now(clock);
    testRun.setStatus(TestRunStatus.QUEUED);
    testRun.setUpdatedAt(now);
    var command = command(testRun, RunnerCommandType.START_RUN, now);
    publishAfterCommit(command);
  }

  @Override
  public void afterRunCancellationRequested(TestRun testRun) {
    var now = OffsetDateTime.now(clock);
    var command = command(testRun, RunnerCommandType.CANCEL_RUN, now);
    publishAfterCommit(command);
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

  private void publishAfterCommit(RunnerCommand command) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              publish(command.getId());
            }
          });
      return;
    }
    publish(command.getId());
  }

  private void publish(UUID commandId) {
    var template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    template.executeWithoutResult(status -> publishInTransaction(commandId));
  }

  private void publishInTransaction(UUID commandId) {
    var command = commandRepository.findById(commandId).orElseThrow();
    try {
      var payload =
          Map.of(
              "commandId", command.getId().toString(),
              "testRunId", command.getTestRunId().toString(),
              "commandType", command.getCommandType().name(),
              "createdAt", command.getCreatedAt().toString());
      RecordId streamId = redisTemplate.opsForStream().add(RunnerRedisStreams.COMMANDS, payload);
      var now = OffsetDateTime.now(clock);
      command.setStreamId(streamId == null ? null : streamId.getValue());
      command.setStatus(RunnerCommandStatus.PUBLISHED);
      command.setAttempts(command.getAttempts() + 1);
      command.setPublishedAt(now);
      command.setUpdatedAt(now);
      commandRepository.save(command);
      log.info("Runner command published: commandId={} testRunId={}", command.getId(), command.getTestRunId());
    } catch (RuntimeException exception) {
      var now = OffsetDateTime.now(clock);
      command.setStatus(RunnerCommandStatus.FAILED);
      command.setAttempts(command.getAttempts() + 1);
      command.setSanitizedError(masker.sanitizeText(exception.getMessage()).value());
      command.setUpdatedAt(now);
      commandRepository.save(command);
      throw exception;
    }
  }
}
