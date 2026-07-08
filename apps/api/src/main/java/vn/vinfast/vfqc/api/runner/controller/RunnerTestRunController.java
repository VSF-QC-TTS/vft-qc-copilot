package vn.vinfast.vfqc.api.runner.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vinfast.vfqc.api.runner.dto.RunnerControlStateResponse;
import vn.vinfast.vfqc.api.runner.dto.RunnerRuntimeSecretsRequest;
import vn.vinfast.vfqc.api.runner.dto.RunnerRuntimeSecretsResponse;
import vn.vinfast.vfqc.api.runner.dto.RunnerTestRunConfigResponse;
import vn.vinfast.vfqc.api.runner.service.RunnerConfigService;
import vn.vinfast.vfqc.api.runner.service.RunnerRuntimeSecretService;

@RestController
@RequestMapping("/internal/runner/test-runs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RUNNER')")
@Tag(name = "Internal Runner Test Runs", description = "Runner execution config and control APIs")
public class RunnerTestRunController {

  private final RunnerConfigService configService;
  private final RunnerRuntimeSecretService secretService;

  @GetMapping("/{testRunId}/config")
  @Operation(summary = "Fetch locked test run config")
  @ApiResponse(responseCode = "200", description = "Locked config returned")
  public RunnerTestRunConfigResponse config(
      @PathVariable UUID testRunId, @PageableDefault(size = 100) Pageable pageable) {
    return configService.config(testRunId, pageable);
  }

  @PostMapping("/{testRunId}/runtime-secrets")
  @Operation(summary = "Resolve runtime-only secrets referenced by locked snapshots")
  @ApiResponse(responseCode = "200", description = "Runtime secrets returned")
  public RunnerRuntimeSecretsResponse runtimeSecrets(
      @PathVariable UUID testRunId, @Valid @RequestBody RunnerRuntimeSecretsRequest request) {
    return secretService.resolve(testRunId, request);
  }

  @GetMapping("/{testRunId}/control-state")
  @Operation(summary = "Fetch current runner control state")
  @ApiResponse(responseCode = "200", description = "Control state returned")
  public RunnerControlStateResponse controlState(@PathVariable UUID testRunId) {
    return configService.controlState(testRunId);
  }
}
