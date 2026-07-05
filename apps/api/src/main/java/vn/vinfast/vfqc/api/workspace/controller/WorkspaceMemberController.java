package vn.vinfast.vfqc.api.workspace.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vinfast.vfqc.api.common.api.CollectionResponse;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.workspace.dto.AddWorkspaceMemberRequest;
import vn.vinfast.vfqc.api.workspace.dto.UpdateWorkspaceMemberRoleRequest;
import vn.vinfast.vfqc.api.workspace.dto.WorkspaceMemberResponse;
import vn.vinfast.vfqc.api.workspace.service.WorkspaceMemberService;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/members")
@RequiredArgsConstructor
@Tag(name = "Workspace Members", description = "Workspace membership and role APIs")
public class WorkspaceMemberController {

  private final WorkspaceMemberService memberService;

  @GetMapping
  @Operation(summary = "List workspace members")
  @ApiResponse(responseCode = "200", description = "Workspace members returned")
  public CollectionResponse<WorkspaceMemberResponse> list(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID workspaceId) {
    return CollectionResponse.of(memberService.list(user, workspaceId));
  }

  @PostMapping
  @Operation(summary = "Add workspace member")
  @ApiResponse(responseCode = "200", description = "Workspace member added")
  @ApiResponse(responseCode = "403", description = "Manage permission required")
  public WorkspaceMemberResponse add(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID workspaceId,
      @Valid @RequestBody AddWorkspaceMemberRequest request) {
    return memberService.add(user, workspaceId, request);
  }

  @PatchMapping("/{memberId}/role")
  @Operation(summary = "Update workspace member role")
  @ApiResponse(responseCode = "200", description = "Workspace member role updated")
  @ApiResponse(responseCode = "403", description = "Manage permission required")
  public WorkspaceMemberResponse updateRole(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID workspaceId,
      @PathVariable Long memberId,
      @Valid @RequestBody UpdateWorkspaceMemberRoleRequest request) {
    return memberService.updateRole(user, workspaceId, memberId, request);
  }
}
