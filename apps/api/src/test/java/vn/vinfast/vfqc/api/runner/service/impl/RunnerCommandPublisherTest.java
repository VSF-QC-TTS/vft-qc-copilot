package vn.vinfast.vfqc.api.runner.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import vn.vinfast.vfqc.api.common.masking.SanitizationResult;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.runner.config.RunnerRedisStreams;
import vn.vinfast.vfqc.api.runner.entity.RunnerCommand;
import vn.vinfast.vfqc.api.runner.enums.RunnerCommandStatus;
import vn.vinfast.vfqc.api.runner.enums.RunnerCommandType;
import vn.vinfast.vfqc.api.runner.repository.RunnerCommandRepository;

class RunnerCommandPublisherTest {

  @Test
  @SuppressWarnings("unchecked")
  void publishPending_RedisSucceeds_MarksCommandPublished() {
    var repository = mock(RunnerCommandRepository.class);
    var redisTemplate = mock(StringRedisTemplate.class);
    StreamOperations<String, String, String> streams = mock(StreamOperations.class);
    var masker = mock(VfqcSensitiveDataMasker.class);
    var command = pendingCommand();
    when(repository.findPublishable(RunnerCommandStatus.PENDING, RunnerCommandStatus.FAILED, 5))
        .thenReturn(List.of(command));
    when(redisTemplate.<String, String>opsForStream()).thenReturn(streams);
    when(streams.add(eq(RunnerRedisStreams.COMMANDS), any(Map.class)))
        .thenReturn(RecordId.of("1740000000000-0"));
    var publisher = new RunnerCommandPublisher(repository, redisTemplate, masker, fixedClock());

    publisher.publishPending();

    assertThat(command.getStatus()).isEqualTo(RunnerCommandStatus.PUBLISHED);
    assertThat(command.getAttempts()).isEqualTo(1);
    assertThat(command.getStreamId()).isEqualTo("1740000000000-0");
    verify(repository).save(command);
  }

  @Test
  @SuppressWarnings("unchecked")
  void publishPending_RedisReturnsNoRecordId_MarksCommandForRetry() {
    var repository = mock(RunnerCommandRepository.class);
    var redisTemplate = mock(StringRedisTemplate.class);
    StreamOperations<String, String, String> streams = mock(StreamOperations.class);
    var masker = mock(VfqcSensitiveDataMasker.class);
    var command = pendingCommand();
    when(repository.findPublishable(RunnerCommandStatus.PENDING, RunnerCommandStatus.FAILED, 5))
        .thenReturn(List.of(command));
    when(redisTemplate.<String, String>opsForStream()).thenReturn(streams);
    when(streams.add(eq(RunnerRedisStreams.COMMANDS), any(Map.class))).thenReturn(null);
    when(masker.sanitizeText("Redis did not return a stream record id"))
        .thenReturn(
            new SanitizationResult<>("Redis did not return a stream record id", List.of()));
    var publisher = new RunnerCommandPublisher(repository, redisTemplate, masker, fixedClock());

    publisher.publishPending();

    assertThat(command.getStatus()).isEqualTo(RunnerCommandStatus.FAILED);
    assertThat(command.getAttempts()).isEqualTo(1);
    assertThat(command.getStreamId()).isNull();
    assertThat(command.getSanitizedError()).isEqualTo("Redis did not return a stream record id");
    verify(repository).save(command);
  }

  private RunnerCommand pendingCommand() {
    var now = OffsetDateTime.now(fixedClock());
    return RunnerCommand.builder()
        .id(UUID.randomUUID())
        .testRunId(UUID.randomUUID())
        .commandType(RunnerCommandType.START_RUN)
        .idempotencyKey(UUID.randomUUID() + ":START_RUN")
        .status(RunnerCommandStatus.PENDING)
        .attempts(0)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  private Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-07-10T07:00:00Z"), ZoneOffset.UTC);
  }
}
