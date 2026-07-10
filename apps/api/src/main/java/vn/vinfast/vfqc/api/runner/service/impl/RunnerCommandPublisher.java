package vn.vinfast.vfqc.api.runner.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.runner.config.RunnerRedisStreams;
import vn.vinfast.vfqc.api.runner.entity.RunnerCommand;
import vn.vinfast.vfqc.api.runner.enums.RunnerCommandStatus;
import vn.vinfast.vfqc.api.runner.repository.RunnerCommandRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class RunnerCommandPublisher {

  private static final int MAX_ATTEMPTS = 5;

  private final RunnerCommandRepository commandRepository;
  private final StringRedisTemplate redisTemplate;
  private final VfqcSensitiveDataMasker masker;
  private final Clock clock;

  @Scheduled(fixedDelayString = "${vfqc.runner.commands.publish-interval-ms:100}")
  @Transactional
  public void publishPending() {
    for (var command :
        commandRepository.findPublishable(
            RunnerCommandStatus.PENDING, RunnerCommandStatus.FAILED, MAX_ATTEMPTS)) {
      publish(command);
    }
  }

  private void publish(RunnerCommand command) {
    try {
      var payload =
          Map.of(
              "commandId", command.getId().toString(),
              "testRunId", command.getTestRunId().toString(),
              "commandType", command.getCommandType().name(),
              "createdAt", command.getCreatedAt().toString());
      RecordId streamId = redisTemplate.opsForStream().add(RunnerRedisStreams.COMMANDS, payload);
      if (streamId == null) {
        throw new IllegalStateException("Redis did not return a stream record id");
      }
      var now = OffsetDateTime.now(clock);
      command.setStreamId(streamId.getValue());
      command.setStatus(RunnerCommandStatus.PUBLISHED);
      command.setAttempts(command.getAttempts() + 1);
      command.setPublishedAt(now);
      command.setSanitizedError(null);
      command.setUpdatedAt(now);
      commandRepository.save(command);
      log.info(
          "Runner command published: commandId={} testRunId={}",
          command.getId(),
          command.getTestRunId());
    } catch (RuntimeException exception) {
      var now = OffsetDateTime.now(clock);
      command.setStatus(RunnerCommandStatus.FAILED);
      command.setAttempts(command.getAttempts() + 1);
      command.setSanitizedError(masker.sanitizeText(exception.getMessage()).value());
      command.setUpdatedAt(now);
      commandRepository.save(command);
      log.warn(
          "Runner command publish failed: commandId={} attempt={} errorType={}",
          command.getId(),
          command.getAttempts(),
          exception.getClass().getSimpleName());
    }
  }
}
