package vn.vinfast.vfqc.api.provider.controller;

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
import vn.vinfast.vfqc.api.provider.dto.CreateLlmProviderRequest;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderResponse;
import vn.vinfast.vfqc.api.provider.dto.UpdateLlmProviderRequest;
import vn.vinfast.vfqc.api.provider.service.LlmProviderService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "LLM Providers", description = "LLM provider configuration APIs")
public class LlmProviderController {

  private final LlmProviderService providerService;

  @GetMapping("/workspaces/{workspaceId}/llm-providers")
  @Operation(summary = "List workspace LLM providers")
  @ApiResponse(responseCode = "200", description = "Workspace providers returned")
  public CollectionResponse<LlmProviderResponse> listWorkspace(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID workspaceId) {
    return CollectionResponse.of(providerService.listWorkspace(user, workspaceId));
  }

  @PostMapping("/workspaces/{workspaceId}/llm-providers")
  @Operation(summary = "Create workspace LLM provider")
  @ApiResponse(responseCode = "200", description = "Provider created")
  public LlmProviderResponse createWorkspace(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID workspaceId,
      @Valid @RequestBody CreateLlmProviderRequest request) {
    return providerService.createWorkspace(user, workspaceId, request);
  }

  @GetMapping("/me/llm-providers")
  @Operation(summary = "List personal LLM providers")
  @ApiResponse(responseCode = "200", description = "Personal providers returned")
  public CollectionResponse<LlmProviderResponse> listPersonal(
      @AuthenticationPrincipal CurrentUser user) {
    return CollectionResponse.of(providerService.listPersonal(user));
  }

  @PostMapping("/me/llm-providers")
  @Operation(summary = "Create personal LLM provider")
  @ApiResponse(responseCode = "200", description = "Provider created")
  public LlmProviderResponse createPersonal(
      @AuthenticationPrincipal CurrentUser user,
      @Valid @RequestBody CreateLlmProviderRequest request) {
    return providerService.createPersonal(user, request);
  }

  @GetMapping("/llm-providers/{providerId}")
  @Operation(summary = "Get LLM provider")
  @ApiResponse(responseCode = "200", description = "Provider returned")
  public LlmProviderResponse get(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID providerId) {
    return providerService.get(user, providerId);
  }

  @PatchMapping("/llm-providers/{providerId}")
  @Operation(summary = "Update LLM provider")
  @ApiResponse(responseCode = "200", description = "Provider updated")
  public LlmProviderResponse update(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID providerId,
      @Valid @RequestBody UpdateLlmProviderRequest request) {
    return providerService.update(user, providerId, request);
  }

  @DeleteMapping("/llm-providers/{providerId}")
  @Operation(summary = "Delete LLM provider")
  @ApiResponse(responseCode = "204", description = "Provider soft-deleted")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID providerId) {
    providerService.delete(user, providerId);
    return ResponseEntity.noContent().build();
  }
}
