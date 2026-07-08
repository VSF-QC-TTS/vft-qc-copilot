package vn.vinfast.vfqc.api.testrun.sse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.vinfast.vfqc.api.common.security.CurrentUser;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Test Run Events", description = "Realtime TestRun SSE events")
public class TestRunSseController {

  private final TestRunSseService service;

  @GetMapping(value = "/test-runs/{testRunId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(summary = "Subscribe to realtime test run events")
  @ApiResponse(responseCode = "200", description = "SSE stream opened")
  public ResponseEntity<SseEmitter> events(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID testRunId,
      @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
      @RequestParam(name = "afterSequenceNo", required = false) String afterSequenceNo) {
    var emitter = service.subscribe(user, testRunId, lastEventId, afterSequenceNo);
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .cacheControl(CacheControl.noCache())
        .header(HttpHeaders.CONNECTION, "keep-alive")
        .header("X-Accel-Buffering", "no")
        .body(emitter);
  }
}
