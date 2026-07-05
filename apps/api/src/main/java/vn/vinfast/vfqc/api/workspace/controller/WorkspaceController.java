package vn.vinfast.vfqc.api.workspace.controller;

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
import vn.vinfast.vfqc.api.workspace.dto.CreateWorkspaceRequest;
import vn.vinfast.vfqc.api.workspace.dto.UpdateWorkspaceRequest;
import vn.vinfast.vfqc.api.workspace.dto.WorkspaceResponse;
import vn.vinfast.vfqc.api.workspace.service.WorkspaceService;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@Tag(name = "Workspaces", description = "Workspace metadata and lifecycle APIs")
public class WorkspaceController {

  private final WorkspaceService workspaceService;

  @GetMapping
  @Operation(summary = "List visible workspaces")
  @ApiResponse(responseCode = "200", description = "Visible workspaces returned")
  public CollectionResponse<WorkspaceResponse> list(@AuthenticationPrincipal CurrentUser user) {
    return CollectionResponse.of(workspaceService.list(user));
  }

  @PostMapping
  @Operation(summary = "Create team workspace")
  @ApiResponse(responseCode = "200", description = "Workspace created")
  @ApiResponse(responseCode = "409", description = "Workspace name already exists")
  public WorkspaceResponse create(
      @AuthenticationPrincipal CurrentUser user,
      @Valid @RequestBody CreateWorkspaceRequest request) {
    return workspaceService.create(user, request);
  }

  @GetMapping("/{workspaceId}")
  @Operation(summary = "Get workspace")
  @ApiResponse(responseCode = "200", description = "Workspace returned")
  @ApiResponse(responseCode = "404", description = "Workspace not found or hidden")
  public WorkspaceResponse get(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID workspaceId) {
    return workspaceService.get(user, workspaceId);
  }

  @PatchMapping("/{workspaceId}")
  @Operation(summary = "Update workspace")
  @ApiResponse(responseCode = "200", description = "Workspace updated")
  @ApiResponse(responseCode = "403", description = "Manage permission required")
  public WorkspaceResponse update(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID workspaceId,
      @Valid @RequestBody UpdateWorkspaceRequest request) {
    return workspaceService.update(user, workspaceId, request);
  }

  @DeleteMapping("/{workspaceId}")
  @Operation(summary = "Delete workspace")
  @ApiResponse(responseCode = "204", description = "Workspace soft-deleted")
  @ApiResponse(responseCode = "403", description = "Manage permission required")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID workspaceId) {
    workspaceService.delete(user, workspaceId);
    return ResponseEntity.noContent().build();
  }
}
