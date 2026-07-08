package vn.vinfast.vfqc.api.testrun.sse;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@Slf4j
class InMemoryTestRunSseEmitterRegistry {

  private final ConcurrentHashMap<UUID, Set<SseEmitter>> emittersByRun = new ConcurrentHashMap<>();

  void register(UUID testRunId, SseEmitter emitter) {
    emittersByRun.computeIfAbsent(testRunId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
    emitter.onCompletion(() -> unregister(testRunId, emitter));
    emitter.onTimeout(() -> unregister(testRunId, emitter));
    emitter.onError(error -> unregister(testRunId, emitter));
  }

  void broadcast(UUID testRunId, SseEmitter.SseEventBuilder event) {
    var emitters = emittersByRun.getOrDefault(testRunId, Set.of());
    sendToAll(testRunId, emitters, event);
  }

  void broadcastAll(SseEmitter.SseEventBuilder event) {
    emittersByRun.forEach((testRunId, emitters) -> sendToAll(testRunId, emitters, event));
  }

  private void sendToAll(UUID testRunId, Set<SseEmitter> emitters, SseEmitter.SseEventBuilder event) {
    var failedEmitters = new ArrayList<SseEmitter>();
    for (var emitter : emitters) {
      try {
        emitter.send(event);
      } catch (IOException | IllegalStateException exception) {
        log.debug("Removing failed SSE emitter: testRunId={}", testRunId, exception);
        failedEmitters.add(emitter);
      }
    }
    failedEmitters.forEach(emitter -> unregister(testRunId, emitter));
  }

  int activeEmitterCount(UUID testRunId) {
    return emittersByRun.getOrDefault(testRunId, Set.of()).size();
  }

  @PreDestroy
  void completeAll() {
    log.info("Shutting down SSE registry: completing all active emitters");
    emittersByRun.values().forEach(emitters -> emitters.forEach(SseEmitter::complete));
    emittersByRun.clear();
  }

  private void unregister(UUID testRunId, SseEmitter emitter) {
    var emitters = emittersByRun.get(testRunId);
    if (emitters == null) {
      return;
    }
    emitters.remove(emitter);
    if (emitters.isEmpty()) {
      emittersByRun.remove(testRunId, emitters);
    }
  }
}
