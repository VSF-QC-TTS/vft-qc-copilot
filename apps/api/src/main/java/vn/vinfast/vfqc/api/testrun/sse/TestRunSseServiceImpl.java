package vn.vinfast.vfqc.api.testrun.sse;

import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.ProjectPermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.runner.repository.RunnerEventRepository;
import vn.vinfast.vfqc.api.testrun.repository.TestRunRepository;

@Service
@RequiredArgsConstructor
@Slf4j
class TestRunSseServiceImpl implements TestRunSseService {

  private static final long EMITTER_TIMEOUT_MS = 30L * 60L * 1000L;
  private static final int REPLAY_LIMIT = 500;

  private final TestRunRepository testRunRepository;
  private final RunnerEventRepository runnerEventRepository;
  private final ProjectPermissionService projectPermissionService;
  private final TestRunSseEventProjector projector;
  private final InMemoryTestRunSseEmitterRegistry registry;

  @Override
  public SseEmitter subscribe(
      CurrentUser user, UUID testRunId, String lastEventId, String afterSequenceNo) {
    var run = testRunRepository.findById(testRunId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    projectPermissionService.requireProject(user, run.getProjectId(), PermissionAction.VIEW);

    var emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
    registry.register(testRunId, emitter);
    sendInitialHeartbeat(testRunId, emitter);
    replay(testRunId, replayCursor(lastEventId, afterSequenceNo), emitter);
    return emitter;
  }

  @Override
  public void broadcast(Long runnerEventId) {
    runnerEventRepository.findById(runnerEventId).ifPresent(this::broadcast);
  }

  @Override
  public void sendHeartbeat() {
    registry.broadcastAll(SseEmitter.event().comment("ping"));
  }

  private void broadcast(vn.vinfast.vfqc.api.runner.entity.RunnerEvent event) {
    registry.broadcast(event.getTestRunId(), toSseEvent(event));
  }

  private void replay(UUID testRunId, long afterSequenceNo, SseEmitter emitter) {
    var events =
        runnerEventRepository.findByTestRunIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(
            testRunId, afterSequenceNo, PageRequest.of(0, REPLAY_LIMIT));
    for (var event : events) {
      try {
        emitter.send(toSseEvent(event));
      } catch (IOException | IllegalStateException exception) {
        log.debug("Closing SSE during replay: testRunId={}", testRunId, exception);
        emitter.completeWithError(exception);
        return;
      }
    }
  }

  private SseEmitter.SseEventBuilder toSseEvent(vn.vinfast.vfqc.api.runner.entity.RunnerEvent event) {
    return SseEmitter.event()
        .id(Long.toString(event.getSequenceNo()))
        .name(event.getEventType().name())
        .data(projector.toResponse(event), MediaType.APPLICATION_JSON);
  }

  private void sendInitialHeartbeat(UUID testRunId, SseEmitter emitter) {
    try {
      emitter.send(SseEmitter.event().comment("ping"));
    } catch (IOException | IllegalStateException exception) {
      log.debug("Failed to send initial SSE heartbeat: testRunId={}", testRunId, exception);
      emitter.completeWithError(exception);
    }
  }

  private long replayCursor(String lastEventId, String afterSequenceNo) {
    if (lastEventId != null && !lastEventId.isBlank()) {
      return parseSequenceNo(lastEventId, "Last-Event-ID");
    }
    if (afterSequenceNo != null && !afterSequenceNo.isBlank()) {
      return parseSequenceNo(afterSequenceNo, "afterSequenceNo");
    }
    return 0L;
  }

  private long parseSequenceNo(String raw, String fieldName) {
    try {
      return Math.max(0L, Long.parseLong(raw));
    } catch (NumberFormatException exception) {
      throw ErrorCode.VALIDATION_ERROR.throwIt(fieldName + " must be numeric");
    }
  }
}
