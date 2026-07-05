package vn.vinfast.vfqc.api.secret.controller;

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
import vn.vinfast.vfqc.api.secret.dto.CreateSecretRequest;
import vn.vinfast.vfqc.api.secret.dto.RotateSecretRequest;
import vn.vinfast.vfqc.api.secret.dto.SecretResponse;
import vn.vinfast.vfqc.api.secret.dto.UpdateSecretRequest;
import vn.vinfast.vfqc.api.secret.service.SecretService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Secrets", description = "Encrypted secret metadata and rotation APIs")
public class SecretController {

  private final SecretService secretService;

  @GetMapping("/workspaces/{workspaceId}/secrets")
  @Operation(summary = "List workspace secrets")
  @ApiResponse(responseCode = "200", description = "Workspace secrets returned without values")
  public CollectionResponse<SecretResponse> listWorkspace(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID workspaceId) {
    return CollectionResponse.of(secretService.listWorkspace(user, workspaceId));
  }

  @PostMapping("/workspaces/{workspaceId}/secrets")
  @Operation(summary = "Create workspace secret")
  @ApiResponse(responseCode = "200", description = "Secret encrypted and created")
  public SecretResponse createWorkspace(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID workspaceId,
      @Valid @RequestBody CreateSecretRequest request) {
    return secretService.createWorkspace(user, workspaceId, request);
  }

  @GetMapping("/me/secrets")
  @Operation(summary = "List personal secrets")
  @ApiResponse(responseCode = "200", description = "Personal secrets returned without values")
  public CollectionResponse<SecretResponse> listPersonal(@AuthenticationPrincipal CurrentUser user) {
    return CollectionResponse.of(secretService.listPersonal(user));
  }

  @PostMapping("/me/secrets")
  @Operation(summary = "Create personal secret")
  @ApiResponse(responseCode = "200", description = "Secret encrypted and created")
  public SecretResponse createPersonal(
      @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateSecretRequest request) {
    return secretService.createPersonal(user, request);
  }

  @GetMapping("/secrets/{secretId}")
  @Operation(summary = "Get secret metadata")
  @ApiResponse(responseCode = "200", description = "Secret metadata returned without values")
  public SecretResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID secretId) {
    return secretService.get(user, secretId);
  }

  @PatchMapping("/secrets/{secretId}")
  @Operation(summary = "Update secret metadata")
  @ApiResponse(responseCode = "200", description = "Secret metadata updated")
  public SecretResponse update(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID secretId,
      @Valid @RequestBody UpdateSecretRequest request) {
    return secretService.update(user, secretId, request);
  }

  @PostMapping("/secrets/{secretId}/rotate")
  @Operation(summary = "Rotate secret value")
  @ApiResponse(responseCode = "200", description = "Secret value rotated without exposing plaintext")
  public SecretResponse rotate(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID secretId,
      @Valid @RequestBody RotateSecretRequest request) {
    return secretService.rotate(user, secretId, request);
  }

  @PostMapping("/secrets/{secretId}/disable")
  @Operation(summary = "Disable secret")
  @ApiResponse(responseCode = "200", description = "Secret disabled")
  public SecretResponse disable(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID secretId) {
    return secretService.disable(user, secretId);
  }

  @DeleteMapping("/secrets/{secretId}")
  @Operation(summary = "Delete secret")
  @ApiResponse(responseCode = "204", description = "Secret soft-deleted")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID secretId) {
    secretService.delete(user, secretId);
    return ResponseEntity.noContent().build();
  }
}
