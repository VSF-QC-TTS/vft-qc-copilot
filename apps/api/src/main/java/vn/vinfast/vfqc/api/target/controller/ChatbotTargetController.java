package vn.vinfast.vfqc.api.target.controller;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vinfast.vfqc.api.common.api.CollectionResponse;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.target.dto.ChatbotTargetExampleResponse;
import vn.vinfast.vfqc.api.target.dto.ChatbotTargetReadinessResponse;
import vn.vinfast.vfqc.api.target.dto.ChatbotTargetResponse;
import vn.vinfast.vfqc.api.target.dto.CreateChatbotTargetRequest;
import vn.vinfast.vfqc.api.target.dto.ResponseExtractionConfigDto;
import vn.vinfast.vfqc.api.target.dto.TestChatbotTargetRequest;
import vn.vinfast.vfqc.api.target.dto.UpdateChatbotTargetRequest;
import vn.vinfast.vfqc.api.target.service.ChatbotTargetService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Chatbot Targets", description = "Chatbot target configuration APIs")
public class ChatbotTargetController {

  private final ChatbotTargetService targetService;

  @GetMapping("/projects/{projectId}/chatbot-targets")
  @Operation(summary = "List project chatbot targets")
  @ApiResponse(responseCode = "200", description = "Targets returned")
  public CollectionResponse<ChatbotTargetResponse> list(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId) {
    return CollectionResponse.of(targetService.list(user, projectId));
  }

  @PostMapping("/projects/{projectId}/chatbot-targets")
  @Operation(summary = "Create chatbot target")
  @ApiResponse(responseCode = "200", description = "Target created")
  public ChatbotTargetResponse create(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID projectId,
      @Valid @RequestBody CreateChatbotTargetRequest request) {
    return targetService.create(user, projectId, request);
  }

  @GetMapping("/chatbot-targets/{targetId}")
  @Operation(summary = "Get chatbot target")
  @ApiResponse(responseCode = "200", description = "Target returned")
  public ChatbotTargetResponse get(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID targetId) {
    return targetService.get(user, targetId);
  }

  @PatchMapping("/chatbot-targets/{targetId}")
  @Operation(summary = "Update chatbot target")
  @ApiResponse(responseCode = "200", description = "Target updated")
  public ChatbotTargetResponse update(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID targetId,
      @Valid @RequestBody UpdateChatbotTargetRequest request) {
    return targetService.update(user, targetId, request);
  }

  @DeleteMapping("/chatbot-targets/{targetId}")
  @Operation(summary = "Delete chatbot target")
  @ApiResponse(responseCode = "204", description = "Target soft-deleted")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID targetId) {
    targetService.delete(user, targetId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/chatbot-targets/{targetId}/test")
  @Operation(summary = "Test chatbot target")
  @ApiResponse(responseCode = "200", description = "Sanitized test example persisted")
  public ChatbotTargetExampleResponse test(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID targetId,
      @RequestBody(required = false) TestChatbotTargetRequest request) {
    return targetService.test(user, targetId, request);
  }

  @GetMapping("/chatbot-targets/{targetId}/examples")
  @Operation(summary = "List chatbot target test examples")
  @ApiResponse(responseCode = "200", description = "Examples returned")
  public CollectionResponse<ChatbotTargetExampleResponse> examples(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID targetId) {
    return CollectionResponse.of(targetService.examples(user, targetId));
  }

  @PutMapping("/chatbot-targets/{targetId}/response-extraction")
  @Operation(summary = "Replace chatbot target response extraction config")
  @ApiResponse(responseCode = "200", description = "Extraction config updated")
  public ChatbotTargetResponse updateExtraction(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID targetId,
      @Valid @RequestBody ResponseExtractionConfigDto request) {
    return targetService.updateExtraction(user, targetId, request);
  }

  @GetMapping("/chatbot-targets/{targetId}/readiness")
  @Operation(summary = "Get chatbot target readiness")
  @ApiResponse(responseCode = "200", description = "Readiness returned")
  public ChatbotTargetReadinessResponse readiness(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID targetId) {
    return targetService.readiness(user, targetId);
  }
}
