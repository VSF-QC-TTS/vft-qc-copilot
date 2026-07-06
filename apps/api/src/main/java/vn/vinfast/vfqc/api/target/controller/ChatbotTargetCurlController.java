package vn.vinfast.vfqc.api.target.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.target.dto.InspectCurlRequest;
import vn.vinfast.vfqc.api.target.dto.InspectCurlResponse;
import vn.vinfast.vfqc.api.target.service.CurlInspectionService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Chatbot Targets", description = "Chatbot target configuration APIs")
public class ChatbotTargetCurlController {

  private final CurlInspectionService curlInspectionService;

  @PostMapping("/projects/{projectId}/chatbot-targets/curl/inspect")
  @Operation(summary = "Inspect raw cURL without executing it")
  @ApiResponse(responseCode = "200", description = "Sanitized cURL preview returned")
  @ApiResponse(responseCode = "400", description = "Unsupported or invalid cURL input")
  public InspectCurlResponse inspectCurl(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID projectId,
      @Valid @RequestBody InspectCurlRequest request) {
    return curlInspectionService.inspect(user, projectId, request);
  }
}
