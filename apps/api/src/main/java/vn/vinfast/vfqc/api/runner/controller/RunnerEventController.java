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
import vn.vinfast.vfqc.api.runner.dto.RunnerEventIngestionResponse;
import vn.vinfast.vfqc.api.runner.dto.RunnerEventRequest;
import vn.vinfast.vfqc.api.runner.service.RunnerEventIngestionService;

@RestController
@RequestMapping("/internal/runner/events")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RUNNER')")
@Tag(name = "Internal Runner Events", description = "Runner event ingestion APIs")
public class RunnerEventController {

  private final RunnerEventIngestionService service;

  @PostMapping
  @Operation(summary = "Ingest a runner event")
  @ApiResponse(responseCode = "200", description = "Event ingested")
  public RunnerEventIngestionResponse ingest(@Valid @RequestBody RunnerEventRequest request) {
    return service.ingest(request, null);
  }
}
