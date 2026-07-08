package vn.vinfast.vfqc.api.runner.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.runner.config.RunnerRedisStreams;
import vn.vinfast.vfqc.api.runner.dto.RunnerEventRequest;
import vn.vinfast.vfqc.api.runner.enums.RunnerEventType;
import vn.vinfast.vfqc.api.runner.service.RunnerDlqService;
import vn.vinfast.vfqc.api.runner.service.RunnerEventIngestionService;
import vn.vinfast.vfqc.api.runner.service.RunnerRedisEventConsumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunnerRedisEventConsumerImpl implements RunnerRedisEventConsumer {

  private static final int MAX_ATTEMPTS = 3;
  private static final String CONSUMER_GROUP = "vfqc-api";
  private static final String CONSUMER_NAME = "api-runner-events";
  private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final RunnerEventIngestionService ingestionService;
  private final RunnerDlqService dlqService;
  private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();

  @Override
  @SuppressWarnings("unchecked")
  public int consumeOnce() {
    ensureGroup();
    StreamOperations<String, String, String> streams = redisTemplate.opsForStream();
    var records = pendingRecords(streams);
    if (records == null || records.isEmpty()) {
      records = newRecords(streams);
    }
    if (records == null || records.isEmpty()) {
      return 0;
    }

    var consumed = 0;
    for (var record : records) {
      var streamId = record.getId().getValue();
      var message = stringify(record.getValue());
      try {
        ingestionService.ingest(toRequest(message), streamId);
        failedAttempts.remove(streamId);
        streams.acknowledge(RunnerRedisStreams.EVENTS, CONSUMER_GROUP, record.getId());
        consumed++;
      } catch (RuntimeException ex) {
        var attempts = failedAttempts.merge(streamId, 1, Integer::sum);
        log.warn(
            "Runner event stream ingestion failed: streamId={} attempt={} errorType={}",
            streamId,
            attempts,
            ex.getClass().getSimpleName());
        if (attempts >= MAX_ATTEMPTS) {
          dlqService.send(RunnerRedisStreams.EVENTS_DLQ, message, ex.getMessage());
          failedAttempts.remove(streamId);
          streams.acknowledge(RunnerRedisStreams.EVENTS, CONSUMER_GROUP, record.getId());
          consumed++;
        }
        break;
      }
    }
    return consumed;
  }

  private void ensureGroup() {
    try {
      redisTemplate.opsForStream().createGroup(RunnerRedisStreams.EVENTS, ReadOffset.from("0-0"), CONSUMER_GROUP);
    } catch (RedisSystemException exception) {
      if (!hasCauseMessage(exception, "BUSYGROUP")) {
        throw exception;
      }
    }
  }

  private boolean hasCauseMessage(Throwable throwable, String expected) {
    var current = throwable;
    while (current != null) {
      if (current.getMessage() != null && current.getMessage().contains(expected)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private List<MapRecord<String, String, String>> pendingRecords(StreamOperations<String, String, String> streams) {
    var options = StreamReadOptions.empty().count(25);
    return streams.read(
        Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
        options,
        StreamOffset.create(RunnerRedisStreams.EVENTS, ReadOffset.from("0")));
  }

  private List<MapRecord<String, String, String>> newRecords(StreamOperations<String, String, String> streams) {
    var options = StreamReadOptions.empty().count(25).block(Duration.ofMillis(100));
    return streams.read(
        Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
        options,
        StreamOffset.create(RunnerRedisStreams.EVENTS, ReadOffset.lastConsumed()));
  }

  private RunnerEventRequest toRequest(Map<String, String> message) {
    return new RunnerEventRequest(
        required(message, "eventId"),
        UUID.fromString(required(message, "testRunId")),
        RunnerEventType.valueOf(required(message, "eventType")),
        uuid(message.get("runnerId")),
        uuid(message.get("commandId")),
        occurredAt(message.get("occurredAt")),
        payload(message.get("payload")));
  }

  private Map<String, String> stringify(Map<String, String> value) {
    var message = new LinkedHashMap<String, String>();
    value.forEach((key, val) -> message.put(key, val));
    return message;
  }

  private String required(Map<String, String> message, String key) {
    var value = message.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Runner event message missing field: " + key);
    }
    return value;
  }

  private UUID uuid(String value) {
    return value == null || value.isBlank() ? null : UUID.fromString(value);
  }

  private OffsetDateTime occurredAt(String value) {
    return value == null || value.isBlank() ? null : OffsetDateTime.parse(value);
  }

  private Map<String, Object> payload(String value) {
    if (value == null || value.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(value, PAYLOAD_TYPE);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Runner event payload is not valid JSON", ex);
    }
  }
}
