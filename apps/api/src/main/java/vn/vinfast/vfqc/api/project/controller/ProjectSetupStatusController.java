package vn.vinfast.vfqc.api.project.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.project.dto.ProjectSetupStatusResponse;
import vn.vinfast.vfqc.api.project.service.ProjectSetupStatusService;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/setup-status")
@RequiredArgsConstructor
@Tag(name = "Project Setup Status", description = "Project readiness aggregate for setup wizard")
public class ProjectSetupStatusController {

  private final ProjectSetupStatusService setupStatusService;

  @GetMapping
  @Operation(summary = "Get project setup status")
  @ApiResponse(responseCode = "200", description = "Project setup status returned")
  public ProjectSetupStatusResponse get(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId) {
    return setupStatusService.get(user, projectId);
  }
}
