package vn.vinfast.vfqc.api.testrun.sse;

import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.vinfast.vfqc.api.common.security.CurrentUser;

public interface TestRunSseService {

  /**
   * Subscribes to the Server-Sent Events stream for real-time test run updates.
   *
   * @param user the current authenticated user subscribing to events
   * @param testRunId the unique identifier of the test run
   * @param lastEventId last received event sequence number for playback reconnects
   * @param afterSequenceNo fallback sequence number parameter for reconnect playback
   * @return a configured Server-Sent Events emitter
   */
  SseEmitter subscribe(CurrentUser user, UUID testRunId, String lastEventId, String afterSequenceNo);

  /**
   * Broadcasts a runner event to all active subscribers of the corresponding test run.
   *
   * @param runnerEventId the unique identifier of the runner event to broadcast
   */
  void broadcast(Long runnerEventId);

  /**
   * Broadcasts a heartbeat ping message to all connected SSE clients to prevent timeouts.
   */
  void sendHeartbeat();
}
