package vn.vinfast.vfqc.api.testrun.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.testrun.dto.ComparisonMatrixResponse;
import vn.vinfast.vfqc.api.testrun.dto.TestCaseResultDetailResponse;
import vn.vinfast.vfqc.api.testrun.dto.TestCaseResultReviewResponse;
import vn.vinfast.vfqc.api.testrun.dto.TestRunResultPageResponse;
import vn.vinfast.vfqc.api.testrun.dto.UpsertTestCaseResultReviewRequest;
import vn.vinfast.vfqc.api.testrun.enums.ResultMode;
import vn.vinfast.vfqc.api.testrun.service.TestRunComparisonMatrixService;
import vn.vinfast.vfqc.api.testrun.service.TestRunResultService;
import vn.vinfast.vfqc.api.testrun.service.TestRunReviewService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Test Run Results", description = "TestRun result, review and comparison matrix APIs")
public class TestRunResultController {

  private final TestRunResultService resultService;
  private final TestRunReviewService reviewService;
  private final TestRunComparisonMatrixService matrixService;

  @GetMapping("/test-runs/{testRunId}/results")
  @Operation(summary = "List test run results")
  @ApiResponse(responseCode = "200", description = "Result page returned")
  public TestRunResultPageResponse results(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID testRunId,
      @RequestParam(defaultValue = "FINAL") ResultMode mode,
      @PageableDefault(size = 50) Pageable pageable) {
    return resultService.list(user, testRunId, mode, pageable);
  }

  @GetMapping("/test-case-results/{resultId}")
  @Operation(summary = "Get test case result detail")
  @ApiResponse(responseCode = "200", description = "Result detail returned")
  public TestCaseResultDetailResponse result(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable Long resultId,
      @RequestParam(defaultValue = "FINAL") ResultMode mode) {
    return resultService.get(user, resultId, mode);
  }

  @PutMapping("/test-case-results/{resultId}/review")
  @Operation(summary = "Create or update a QC review")
  @ApiResponse(responseCode = "200", description = "Review saved")
  public TestCaseResultReviewResponse review(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable Long resultId,
      @Valid @RequestBody UpsertTestCaseResultReviewRequest request) {
    return reviewService.upsert(user, resultId, request);
  }

  @GetMapping("/test-runs/{testRunId}/comparison-matrix")
  @Operation(summary = "Get comparison matrix")
  @ApiResponse(responseCode = "200", description = "Comparison matrix returned")
  public ComparisonMatrixResponse matrix(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID testRunId,
      @RequestParam(defaultValue = "FINAL") ResultMode mode,
      @PageableDefault(size = 50) Pageable pageable) {
    return matrixService.matrix(user, testRunId, mode, pageable);
  }
}
