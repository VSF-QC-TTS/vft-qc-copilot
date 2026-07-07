package vn.vinfast.vfqc.api.evaluator.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vinfast.vfqc.api.common.api.CollectionResponse;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.evaluator.dto.CreateEvaluatorVersionRequest;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorVersionResponse;
import vn.vinfast.vfqc.api.evaluator.service.EvaluatorVersionService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EvaluatorVersionController {

  private final EvaluatorVersionService evaluatorVersionService;

  @GetMapping("/evaluators/{evaluatorId}/versions")
  @Operation(summary = "List evaluator versions")
  public CollectionResponse<EvaluatorVersionResponse> list(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID evaluatorId) {
    return CollectionResponse.of(evaluatorVersionService.list(user, evaluatorId));
  }

  @PostMapping("/evaluators/{evaluatorId}/versions")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create evaluator version")
  public EvaluatorVersionResponse create(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID evaluatorId,
      @Valid @RequestBody CreateEvaluatorVersionRequest request) {
    return evaluatorVersionService.create(user, evaluatorId, request);
  }

  @GetMapping("/evaluator-versions/{evaluatorVersionId}")
  @Operation(summary = "Get evaluator version")
  public EvaluatorVersionResponse get(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID evaluatorVersionId) {
    return evaluatorVersionService.get(user, evaluatorVersionId);
  }
}
