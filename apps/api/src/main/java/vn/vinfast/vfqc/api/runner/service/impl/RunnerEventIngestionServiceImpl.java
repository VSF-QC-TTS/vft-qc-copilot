package vn.vinfast.vfqc.api.runner.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.runner.dto.RunnerEventIngestionResponse;
import vn.vinfast.vfqc.api.runner.dto.RunnerEventRequest;
import vn.vinfast.vfqc.api.runner.entity.RunnerEvent;
import vn.vinfast.vfqc.api.runner.enums.RunnerEventStatus;
import vn.vinfast.vfqc.api.runner.enums.RunnerEventType;
import vn.vinfast.vfqc.api.runner.event.RunnerEventCommittedEvent;
import vn.vinfast.vfqc.api.runner.repository.RunnerEventRepository;
import vn.vinfast.vfqc.api.runner.service.RunnerEventIngestionService;
import vn.vinfast.vfqc.api.runner.service.RunnerSequenceService;
import vn.vinfast.vfqc.api.testrun.entity.TestCaseResult;
import vn.vinfast.vfqc.api.testrun.entity.TestRun;
import vn.vinfast.vfqc.api.testrun.enums.TestCaseResultStatus;
import vn.vinfast.vfqc.api.testrun.enums.TestRunStatus;
import vn.vinfast.vfqc.api.testrun.repository.TestCaseResultRepository;
import vn.vinfast.vfqc.api.testrun.repository.TestRunRepository;
import vn.vinfast.vfqc.api.testrun.state.TestRunStateMachine;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunnerEventIngestionServiceImpl implements RunnerEventIngestionService {

  private final RunnerEventRepository eventRepository;
  private final TestRunRepository testRunRepository;
  private final TestCaseResultRepository resultRepository;
  private final RunnerSequenceService sequenceService;
  private final TestRunStateMachine stateMachine;
  private final VfqcSensitiveDataMasker masker;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Override
  @Transactional
  public RunnerEventIngestionResponse ingest(RunnerEventRequest request, String streamId) {
    log.info("Ingesting runner event: eventId={}, testRunId={}, eventType={}, streamId={}", request.eventId(), request.testRunId(), request.eventType(), streamId);
    var existing = eventRepository.findByEventId(request.eventId());
    if (existing.isPresent()) {
      return idempotentResponse(request, existing.get());
    }

    var run = testRunRepository.findWithLockById(request.testRunId()).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    existing = eventRepository.findByEventId(request.eventId());
    if (existing.isPresent()) {
      return idempotentResponse(request, existing.get());
    }
    var now = OffsetDateTime.now(clock);
    var payload = sanitizePayload(request.payload());
    applyEffect(run, request.eventType(), payload, now);
    var event =
        RunnerEvent.builder()
            .eventId(request.eventId())
            .testRunId(request.testRunId())
            .runnerId(request.runnerId())
            .commandId(request.commandId())
            .eventType(request.eventType())
            .sequenceNo(sequenceService.nextSequence(request.testRunId()))
            .streamId(streamId)
            .status(RunnerEventStatus.APPLIED)
            .attempts(1)
            .payloadJson(payload)
            .occurredAt(request.occurredAt())
            .createdAt(now)
            .updatedAt(now)
            .build();
    var saved = eventRepository.save(event);
    eventPublisher.publishEvent(new RunnerEventCommittedEvent(saved.getId()));
    return new RunnerEventIngestionResponse(
        saved.getEventId(), saved.getTestRunId(), saved.getSequenceNo(), saved.getStatus());
  }

  private void applyEffect(TestRun run, RunnerEventType type, Map<String, Object> payload, OffsetDateTime now) {
    switch (type) {
      case RUN_STARTED -> transition(run, TestRunStatus.RUNNING, now, true);
      case RUN_PROGRESS -> updateProgress(run, payload, now);
      case TESTCASE_COMPLETED -> upsertResult(run, payload, false, now);
      case TESTCASE_ERROR -> upsertResult(run, payload, true, now);
      case RUN_COMPLETED -> complete(run, now);
      case RUN_FAILED -> transition(run, TestRunStatus.FAILED, now, false);
      case RUN_CANCELLED -> transition(run, TestRunStatus.CANCELLED, now, false);
      case RUN_LOG, ARTIFACT_CREATED -> run.setUpdatedAt(now);
    }
  }

  private void transition(TestRun run, TestRunStatus next, OffsetDateTime now, boolean started) {
    stateMachine.requireTransition(run.getStatus(), next);
    run.setStatus(next);
    if (started) {
      run.setStartedAt(now);
    }
    if (next == TestRunStatus.COMPLETED || next == TestRunStatus.FAILED || next == TestRunStatus.CANCELLED) {
      run.setFinishedAt(now);
    }
    run.setUpdatedAt(now);
  }

  private void updateProgress(TestRun run, Map<String, Object> payload, OffsetDateTime now) {
    if (run.getStatus() != TestRunStatus.RUNNING && run.getStatus() != TestRunStatus.CANCEL_REQUESTED) {
      throw ErrorCode.INVALID_STATE.throwIt("Progress event is not allowed for current test run status");
    }
    integer(payload, "completed").ifPresent(run::setProgressCompleted);
    integer(payload, "total").ifPresent(run::setProgressTotal);
    run.setUpdatedAt(now);
  }

  private void complete(TestRun run, OffsetDateTime now) {
    transition(run, TestRunStatus.COMPLETED, now, false);
    var total = resultRepository.countByTestRunId(run.getId());
    var passed = resultRepository.countByTestRunIdAndStatus(run.getId(), TestCaseResultStatus.PASSED);
    var failed = resultRepository.countByTestRunIdAndStatus(run.getId(), TestCaseResultStatus.FAILED);
    var errored = total - passed - failed;
    var metrics = new LinkedHashMap<String, Object>();
    metrics.put("total", total);
    metrics.put("passed", passed);
    metrics.put("failed", failed);
    metrics.put("errored", errored);
    metrics.put("passRate", total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(passed).divide(BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP));
    run.setSummaryMetricsJson(metrics);
  }

  private void upsertResult(TestRun run, Map<String, Object> payload, boolean errorEvent, OffsetDateTime now) {
    if (run.getStatus() != TestRunStatus.RUNNING && run.getStatus() != TestRunStatus.CANCEL_REQUESTED) {
      throw ErrorCode.INVALID_STATE.throwIt("Test case event is not allowed for current test run status");
    }
    var targetId = uuid(payload, "testRunTargetId");
    var rowId = longValue(payload, "datasetRowId");
    if (targetId == null || rowId == null) {
      throw ErrorCode.VALIDATION_ERROR.throwIt("Test case event requires testRunTargetId and datasetRowId");
    }
    var result =
        resultRepository
            .findByTestRunIdAndTestRunTargetIdAndDatasetRowId(run.getId(), targetId, rowId)
            .orElseGet(
                () ->
                    TestCaseResult.builder()
                        .testRunId(run.getId())
                        .testRunTargetId(targetId)
                        .datasetRowId(rowId)
                        .createdAt(now)
                        .build());
    result.setStatus(status(payload, errorEvent));
    result.setScore(decimal(payload, "score"));
    result.setPassed(booleanValue(payload, "passed"));
    result.setErrorCategory(stringValue(payload, "errorCategory"));
    result.setRequestJson(mapValue(payload, "request"));
    result.setResponseJson(mapValue(payload, "response"));
    result.setExtractedOutputJson(mapValue(payload, "extractedOutput"));
    result.setGradingResultJson(mapValue(payload, "gradingResult"));
    result.setComponentResultsJson(mapValue(payload, "componentResults"));
    result.setTokenUsageJson(mapValue(payload, "tokenUsage"));
    result.setLatencyMs(integer(payload, "latencyMs").orElse(null));
    result.setMachineSummary(masker.sanitizeText(stringValue(payload, "machineSummary")).value());
    result.setUpdatedAt(now);
    resultRepository.save(result);
  }

  private TestCaseResultStatus status(Map<String, Object> payload, boolean errorEvent) {
    if (payload.get("status") != null) {
      try {
        return TestCaseResultStatus.valueOf(payload.get("status").toString());
      } catch (IllegalArgumentException exception) {
        throw ErrorCode.VALIDATION_ERROR.throwIt("Unknown test case result status");
      }
    }
    return errorEvent ? TestCaseResultStatus.ERROR : TestCaseResultStatus.PASSED;
  }

  private RunnerEventIngestionResponse idempotentResponse(RunnerEventRequest request, RunnerEvent event) {
    if (!event.getTestRunId().equals(request.testRunId()) || event.getEventType() != request.eventType()) {
      throw ErrorCode.DUPLICATE_RESOURCE.throwIt("Runner event id already exists with different event metadata");
    }
    return new RunnerEventIngestionResponse(
        event.getEventId(), event.getTestRunId(), event.getSequenceNo(), event.getStatus());
  }

  private Map<String, Object> sanitizePayload(Map<String, Object> payload) {
    return masker.sanitizeMap(payload == null ? Map.of() : payload).value();
  }

  private java.util.Optional<Integer> integer(Map<String, Object> payload, String key) {
    var value = payload.get(key);
    if (value instanceof Number number) {
      return java.util.Optional.of(number.intValue());
    }
    if (value instanceof String string && !string.isBlank()) {
      return java.util.Optional.of(Integer.parseInt(string));
    }
    return java.util.Optional.empty();
  }

  private Long longValue(Map<String, Object> payload, String key) {
    var value = payload.get(key);
    if (value instanceof Number number) {
      return number.longValue();
    }
    return value == null ? null : Long.parseLong(value.toString());
  }

  private UUID uuid(Map<String, Object> payload, String key) {
    var value = payload.get(key);
    return value == null ? null : UUID.fromString(value.toString());
  }

  private BigDecimal decimal(Map<String, Object> payload, String key) {
    var value = payload.get(key);
    if (value instanceof BigDecimal decimal) {
      return decimal;
    }
    if (value instanceof Number number) {
      return BigDecimal.valueOf(number.doubleValue());
    }
    return value == null ? null : new BigDecimal(value.toString());
  }

  private Boolean booleanValue(Map<String, Object> payload, String key) {
    var value = payload.get(key);
    if (value instanceof Boolean bool) {
      return bool;
    }
    return value == null ? null : Boolean.parseBoolean(value.toString());
  }

  private String stringValue(Map<String, Object> payload, String key) {
    var value = payload.get(key);
    return value == null ? null : value.toString();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> mapValue(Map<String, Object> payload, String key) {
    var value = payload.get(key);
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return Map.of();
  }
}
