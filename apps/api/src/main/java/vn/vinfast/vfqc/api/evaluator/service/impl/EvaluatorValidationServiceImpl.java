package vn.vinfast.vfqc.api.evaluator.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.ProjectPermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorRuleRequest;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorValidationResponse;
import vn.vinfast.vfqc.api.evaluator.dto.ValidateEvaluatorRequest;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorType;
import vn.vinfast.vfqc.api.evaluator.service.EvaluatorFieldSourceService;
import vn.vinfast.vfqc.api.evaluator.service.EvaluatorValidationService;
import vn.vinfast.vfqc.api.evaluator.validator.EvaluatorConfigValidator;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluatorValidationServiceImpl implements EvaluatorValidationService {

  private final ProjectPermissionService projectPermissionService;
  private final EvaluatorFieldSourceService fieldSourceService;
  private final EvaluatorConfigValidator validator;

  @Override
  @Transactional(readOnly = true)
  public EvaluatorValidationResponse validate(
      CurrentUser user, UUID projectId, ValidateEvaluatorRequest request) {
    projectPermissionService.requireProject(user, projectId, PermissionAction.EDIT);
    var sources = availableSources(user, projectId, request.context());
    var rules = request.rules() == null ? List.<EvaluatorRuleRequest>of() : request.rules();
    validator.validate(user, request.type(), request.config() == null ? Map.of() : request.config(), rules, sources);
    var warnings = new ArrayList<EvaluatorValidationResponse.Warning>();
    if ((request.type() == EvaluatorType.LLM_JUDGE
            || request.type() == EvaluatorType.MIXED)
        && !hasDefaultProvider(request.config())) {
      warnings.add(
          new EvaluatorValidationResponse.Warning(
              "LLM_JUDGE_DEFAULT_PROVIDER_NOT_SET",
              "No default judge provider is configured. User must select a judge provider during run setup."));
    }
    var response = new EvaluatorValidationResponse(
        true,
        warnings,
        Map.of(
            "type", request.type().name(),
            "ruleCount", rules.size(),
            "hasLlmJudge", request.config() != null && request.config().containsKey("llmJudge")));
    log.info("Evaluator validation passed: projectId={}, type={}, ruleCount={}", projectId, request.type(), rules.size());
    return response;
  }

  private List<String> availableSources(
      CurrentUser user, UUID projectId, ValidateEvaluatorRequest.ValidationContext context) {
    if (context == null || context.chatbotTargetId() == null) {
      return List.of();
    }
    var response = fieldSourceService.list(user, projectId, context.chatbotTargetId());
    var sources = new ArrayList<String>();
    response.datasetVariables().forEach(variable -> sources.add("dataset:" + variable.path()));
    response.extractedFields().forEach(field -> sources.add(field.fieldSource()));
    response.responseFields().forEach(field -> sources.add(field.fieldSource()));
    return sources;
  }

  @SuppressWarnings("unchecked")
  private boolean hasDefaultProvider(Map<String, Object> config) {
    if (config == null || !(config.get("llmJudge") instanceof Map<?, ?> llmJudge)) {
      return false;
    }
    return ((Map<String, Object>) llmJudge).get("defaultJudgeProviderId") != null;
  }
}
