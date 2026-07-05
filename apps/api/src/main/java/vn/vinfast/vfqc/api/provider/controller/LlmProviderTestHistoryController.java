package vn.vinfast.vfqc.api.provider.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vinfast.vfqc.api.common.api.CollectionResponse;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderTestLogResponse;
import vn.vinfast.vfqc.api.provider.dto.LlmProviderTestResponse;
import vn.vinfast.vfqc.api.provider.dto.TestLlmProviderRequest;
import vn.vinfast.vfqc.api.provider.service.LlmProviderTestService;

@RestController
@RequestMapping("/api/v1/llm-providers/{providerId}")
@RequiredArgsConstructor
@Tag(name = "LLM Provider Tests", description = "Provider connectivity tests and sanitized logs")
public class LlmProviderTestHistoryController {

  private final LlmProviderTestService testService;

  @PostMapping("/test")
  @Operation(summary = "Test LLM provider")
  @ApiResponse(responseCode = "200", description = "Sanitized provider test result returned")
  public LlmProviderTestResponse test(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID providerId,
      @Valid @RequestBody TestLlmProviderRequest request) {
    return testService.test(user, providerId, request);
  }

  @GetMapping("/test-logs")
  @Operation(summary = "List LLM provider test logs")
  @ApiResponse(responseCode = "200", description = "Sanitized provider test logs returned")
  public CollectionResponse<LlmProviderTestLogResponse> history(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID providerId,
      @RequestParam(defaultValue = "20") int limit) {
    return CollectionResponse.of(testService.history(user, providerId, limit));
  }
}
