package vn.vinfast.vfqc.api.project.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vinfast.vfqc.api.common.api.CollectionResponse;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.project.dto.CreateProjectRequest;
import vn.vinfast.vfqc.api.project.dto.ProjectResponse;
import vn.vinfast.vfqc.api.project.dto.UpdateProjectRequest;
import vn.vinfast.vfqc.api.project.service.ProjectService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project metadata and lifecycle APIs")
public class ProjectController {

  private final ProjectService projectService;

  @GetMapping("/workspaces/{workspaceId}/projects")
  @Operation(summary = "List workspace projects")
  @ApiResponse(responseCode = "200", description = "Projects returned")
  public CollectionResponse<ProjectResponse> list(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID workspaceId) {
    return CollectionResponse.of(projectService.list(user, workspaceId));
  }

  @PostMapping("/workspaces/{workspaceId}/projects")
  @Operation(summary = "Create project")
  @ApiResponse(responseCode = "200", description = "Project created with default dataset schema")
  @ApiResponse(responseCode = "409", description = "Project name already exists")
  public ProjectResponse create(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID workspaceId,
      @Valid @RequestBody CreateProjectRequest request) {
    return projectService.create(user, workspaceId, request);
  }

  @GetMapping("/projects/{projectId}")
  @Operation(summary = "Get project")
  @ApiResponse(responseCode = "200", description = "Project returned")
  @ApiResponse(responseCode = "404", description = "Project not found or hidden")
  ProjectResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId) {
    return projectService.get(user, projectId);
  }

  @PatchMapping("/projects/{projectId}")
  @Operation(summary = "Update project")
  @ApiResponse(responseCode = "200", description = "Project updated")
  @ApiResponse(responseCode = "403", description = "Edit permission required")
  ProjectResponse update(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID projectId,
      @Valid @RequestBody UpdateProjectRequest request) {
    return projectService.update(user, projectId, request);
  }

  @PostMapping("/projects/{projectId}/archive")
  @Operation(summary = "Archive project")
  @ApiResponse(responseCode = "200", description = "Project archived")
  public ProjectResponse archive(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId) {
    return projectService.archive(user, projectId);
  }

  @DeleteMapping("/projects/{projectId}")
  @Operation(summary = "Delete project")
  @ApiResponse(responseCode = "204", description = "Project soft-deleted")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId) {
    projectService.delete(user, projectId);
    return ResponseEntity.noContent().build();
  }
}
