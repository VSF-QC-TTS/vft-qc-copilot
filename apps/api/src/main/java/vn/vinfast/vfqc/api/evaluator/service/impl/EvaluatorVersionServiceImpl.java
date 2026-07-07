package vn.vinfast.vfqc.api.evaluator.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.evaluator.assembler.EvaluatorResponseAssembler;
import vn.vinfast.vfqc.api.evaluator.dto.CreateEvaluatorVersionRequest;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorVersionResponse;
import vn.vinfast.vfqc.api.evaluator.entity.EvaluatorRule;
import vn.vinfast.vfqc.api.evaluator.repository.EvaluatorRuleRepository;
import vn.vinfast.vfqc.api.evaluator.repository.EvaluatorVersionRepository;
import vn.vinfast.vfqc.api.evaluator.service.EvaluatorVersionService;
import vn.vinfast.vfqc.api.evaluator.validator.EvaluatorConfigValidator;

@Service
@RequiredArgsConstructor
public class EvaluatorVersionServiceImpl implements EvaluatorVersionService {

  private final EvaluatorServiceImpl evaluatorService;
  private final EvaluatorVersionRepository versionRepository;
  private final EvaluatorRuleRepository ruleRepository;
  private final EvaluatorConfigValidator validator;
  private final EvaluatorResponseAssembler assembler;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public List<EvaluatorVersionResponse> list(CurrentUser user, UUID evaluatorId) {
    var evaluator = evaluatorService.requireEvaluator(user, evaluatorId, PermissionAction.VIEW);
    var versions = versionRepository.findByEvaluatorIdOrderByVersionDesc(evaluatorId);
    var rulesByVersion =
        ruleRepository.findByEvaluatorVersionIdIn(versions.stream().map(version -> version.getId()).toList()).stream()
            .collect(Collectors.groupingBy(EvaluatorRule::getEvaluatorVersionId));
    return assembler.toVersionResponses(evaluator, versions, rulesByVersion);
  }

  @Override
  @Transactional
  public EvaluatorVersionResponse create(
      CurrentUser user, UUID evaluatorId, CreateEvaluatorVersionRequest request) {
    var evaluator = evaluatorService.requireEvaluator(user, evaluatorId, PermissionAction.EDIT);
    var config = evaluatorService.safeConfig(request.config());
    var rules = evaluatorService.safeRules(request.rules());
    validator.validate(user, evaluator.getType(), config, rules, List.of());
    var now = OffsetDateTime.now(clock);
    var version =
        evaluatorService.createVersion(
            evaluatorId, versionRepository.maxVersion(evaluatorId) + 1, config, user.id(), now);
    evaluatorService.createRules(version.getId(), rules, now);
    return assembler.toVersionResponse(
        evaluator, version, ruleRepository.findByEvaluatorVersionIdOrderByOrderIndexAsc(version.getId()));
  }

  @Override
  @Transactional(readOnly = true)
  public EvaluatorVersionResponse get(CurrentUser user, UUID evaluatorVersionId) {
    var version =
        versionRepository
            .findById(evaluatorVersionId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var evaluator = evaluatorService.requireEvaluator(user, version.getEvaluatorId(), PermissionAction.VIEW);
    return assembler.toVersionResponse(
        evaluator,
        version,
        ruleRepository.findByEvaluatorVersionIdOrderByOrderIndexAsc(version.getId()));
  }
}
