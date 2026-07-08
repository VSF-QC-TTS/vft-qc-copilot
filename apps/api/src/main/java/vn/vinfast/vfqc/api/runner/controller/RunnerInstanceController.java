package vn.vinfast.vfqc.api.runner.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vinfast.vfqc.api.runner.dto.RegisterRunnerInstanceRequest;
import vn.vinfast.vfqc.api.runner.dto.RunnerHeartbeatRequest;
import vn.vinfast.vfqc.api.runner.dto.RunnerInstanceResponse;
import vn.vinfast.vfqc.api.runner.service.RunnerInstanceService;

@RestController
@RequestMapping("/internal/runner/instances")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RUNNER')")
@Tag(name = "Internal Runner Instances", description = "Runner service registration and heartbeat APIs")
public class RunnerInstanceController {

  private final RunnerInstanceService service;

  @PostMapping("/register")
  @Operation(summary = "Register a runner instance")
  @ApiResponse(responseCode = "200", description = "Runner registered")
  public RunnerInstanceResponse register(@Valid @RequestBody RegisterRunnerInstanceRequest request) {
    return service.register(request);
  }

  @PostMapping("/heartbeat")
  @Operation(summary = "Heartbeat a runner instance")
  @ApiResponse(responseCode = "200", description = "Runner heartbeat recorded")
  public RunnerInstanceResponse heartbeat(@Valid @RequestBody RunnerHeartbeatRequest request) {
    return service.heartbeat(request);
  }
}
