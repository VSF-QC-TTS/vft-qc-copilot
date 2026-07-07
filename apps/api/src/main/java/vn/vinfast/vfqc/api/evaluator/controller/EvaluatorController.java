package vn.vinfast.vfqc.api.evaluator.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vinfast.vfqc.api.common.api.CollectionResponse;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.evaluator.dto.CreateEvaluatorRequest;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorResponse;
import vn.vinfast.vfqc.api.evaluator.dto.UpdateEvaluatorRequest;
import vn.vinfast.vfqc.api.evaluator.service.EvaluatorService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EvaluatorController {

  private final EvaluatorService evaluatorService;

  @GetMapping("/projects/{projectId}/evaluators")
  @Operation(summary = "List project evaluators")
  public CollectionResponse<EvaluatorResponse> list(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID projectId) {
    return CollectionResponse.of(evaluatorService.list(user, projectId));
  }

  @PostMapping("/projects/{projectId}/evaluators")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create evaluator with initial version")
  public EvaluatorResponse create(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID projectId,
      @Valid @RequestBody CreateEvaluatorRequest request) {
    return evaluatorService.create(user, projectId, request);
  }

  @GetMapping("/evaluators/{evaluatorId}")
  @Operation(summary = "Get evaluator")
  public EvaluatorResponse get(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID evaluatorId) {
    return evaluatorService.get(user, evaluatorId);
  }

  @PatchMapping("/evaluators/{evaluatorId}")
  @Operation(summary = "Update evaluator metadata")
  public EvaluatorResponse update(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID evaluatorId,
      @Valid @RequestBody UpdateEvaluatorRequest request) {
    return evaluatorService.update(user, evaluatorId, request);
  }

  @DeleteMapping("/evaluators/{evaluatorId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Soft delete evaluator")
  public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID evaluatorId) {
    evaluatorService.delete(user, evaluatorId);
  }
}
