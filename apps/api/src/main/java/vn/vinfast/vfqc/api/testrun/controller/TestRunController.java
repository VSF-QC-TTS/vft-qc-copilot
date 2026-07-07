package vn.vinfast.vfqc.api.testrun.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vinfast.vfqc.api.common.api.CollectionResponse;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.testrun.dto.CancelTestRunRequest;
import vn.vinfast.vfqc.api.testrun.dto.CreateTestRunRequest;
import vn.vinfast.vfqc.api.testrun.dto.RunPreflightRequest;
import vn.vinfast.vfqc.api.testrun.dto.RunPreflightResponse;
import vn.vinfast.vfqc.api.testrun.dto.TestRunDetailResponse;
import vn.vinfast.vfqc.api.testrun.dto.TestRunSummaryResponse;
import vn.vinfast.vfqc.api.testrun.service.TestRunPreflightService;
import vn.vinfast.vfqc.api.testrun.service.TestRunQueryService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Test Runs", description = "TestRun preflight, creation, state and read APIs")
public class TestRunController {

  private final TestRunPreflightService preflightService;
  private final TestRunQueryService queryService;

  @PostMapping("/projects/{projectId}/test-runs/preflight")
  @Operation(summary = "Preflight a test run without creating it")
  @ApiResponse(responseCode = "200", description = "Preflight result returned")
  public RunPreflightResponse preflight(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID projectId,
      @Valid @RequestBody RunPreflightRequest request) {
    return preflightService.preflight(user, projectId, request);
  }

  @PostMapping("/projects/{projectId}/test-runs")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a test run with locked snapshots")
  @ApiResponse(responseCode = "201", description = "Test run created")
  public TestRunDetailResponse create(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID projectId,
      @Valid @RequestBody CreateTestRunRequest request) {
    return queryService.create(user, projectId, request);
  }

  @GetMapping("/projects/{projectId}/test-runs")
  @Operation(summary = "List project test runs")
  @ApiResponse(responseCode = "200", description = "Test runs returned")
  public CollectionResponse<TestRunSummaryResponse> list(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID projectId,
      @PageableDefault(size = 50) Pageable pageable) {
    return queryService.list(user, projectId, pageable);
  }

  @GetMapping("/test-runs/{testRunId}")
  @Operation(summary = "Get test run detail")
  @ApiResponse(responseCode = "200", description = "Test run returned")
  public TestRunDetailResponse get(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID testRunId) {
    return queryService.get(user, testRunId);
  }

  @PostMapping("/test-runs/{testRunId}/cancel")
  @Operation(summary = "Request test run cancellation")
  @ApiResponse(responseCode = "200", description = "Test run cancellation state updated")
  public TestRunDetailResponse cancel(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID testRunId,
      @Valid @RequestBody(required = false) CancelTestRunRequest request) {
    return queryService.cancel(user, testRunId, request == null ? new CancelTestRunRequest(null) : request);
  }
}
