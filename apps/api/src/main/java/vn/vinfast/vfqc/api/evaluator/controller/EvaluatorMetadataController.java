package vn.vinfast.vfqc.api.evaluator.controller;

import io.swagger.v3.oas.annotations.Operation;
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
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorFieldSourceResponse;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorOperatorResponse;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorValidationResponse;
import vn.vinfast.vfqc.api.evaluator.dto.ValidateEvaluatorRequest;
import vn.vinfast.vfqc.api.evaluator.service.EvaluatorFieldSourceService;
import vn.vinfast.vfqc.api.evaluator.service.EvaluatorOperatorService;
import vn.vinfast.vfqc.api.evaluator.service.EvaluatorValidationService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EvaluatorMetadataController {

  private final EvaluatorValidationService validationService;
  private final EvaluatorFieldSourceService fieldSourceService;
  private final EvaluatorOperatorService operatorService;

  @PostMapping("/projects/{projectId}/evaluators/validate")
  @Operation(summary = "Validate evaluator config")
  public EvaluatorValidationResponse validate(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID projectId,
      @Valid @RequestBody ValidateEvaluatorRequest request) {
    return validationService.validate(user, projectId, request);
  }

  @GetMapping("/projects/{projectId}/evaluator-field-sources")
  @Operation(summary = "List evaluator field sources")
  public EvaluatorFieldSourceResponse fieldSources(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID projectId,
      @RequestParam(required = false) UUID chatbotTargetId) {
    return fieldSourceService.list(user, projectId, chatbotTargetId);
  }

  @GetMapping("/evaluator-operators")
  @Operation(summary = "List evaluator operators")
  public CollectionResponse<EvaluatorOperatorResponse> operators() {
    return CollectionResponse.of(operatorService.list());
  }
}
