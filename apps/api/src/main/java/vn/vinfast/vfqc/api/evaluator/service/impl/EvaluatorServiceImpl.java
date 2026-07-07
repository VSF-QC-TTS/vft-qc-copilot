package vn.vinfast.vfqc.api.evaluator.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.naming.ResourceNameNormalizer;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.ProjectPermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.evaluator.assembler.EvaluatorResponseAssembler;
import vn.vinfast.vfqc.api.evaluator.dto.CreateEvaluatorRequest;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorResponse;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorRuleRequest;
import vn.vinfast.vfqc.api.evaluator.dto.UpdateEvaluatorRequest;
import vn.vinfast.vfqc.api.evaluator.entity.Evaluator;
import vn.vinfast.vfqc.api.evaluator.entity.EvaluatorRule;
import vn.vinfast.vfqc.api.evaluator.entity.EvaluatorVersion;
import vn.vinfast.vfqc.api.evaluator.repository.EvaluatorRepository;
import vn.vinfast.vfqc.api.evaluator.repository.EvaluatorRuleRepository;
import vn.vinfast.vfqc.api.evaluator.repository.EvaluatorVersionRepository;
import vn.vinfast.vfqc.api.evaluator.service.EvaluatorService;
import vn.vinfast.vfqc.api.evaluator.validator.EvaluatorConfigValidator;

@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluatorServiceImpl implements EvaluatorService {

  private final EvaluatorRepository evaluatorRepository;
  private final EvaluatorVersionRepository versionRepository;
  private final EvaluatorRuleRepository ruleRepository;
  private final ProjectPermissionService projectPermissionService;
  private final ResourceNameNormalizer nameNormalizer;
  private final EvaluatorConfigValidator validator;
  private final EvaluatorResponseAssembler assembler;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public List<EvaluatorResponse> list(CurrentUser user, UUID projectId) {
    projectPermissionService.requireProject(user, projectId, PermissionAction.VIEW);
    return assembler.toResponses(
        evaluatorRepository.findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId));
  }

  @Override
  @Transactional
  public EvaluatorResponse create(CurrentUser user, UUID projectId, CreateEvaluatorRequest request) {
    projectPermissionService.requireProject(user, projectId, PermissionAction.EDIT);
    var normalized = nameNormalizer.normalize(request.name(), 160);
    if (evaluatorRepository.existsByProjectIdAndNameLookupAndDeletedAtIsNull(
        projectId, normalized.lookupName())) {
      throw ErrorCode.EVALUATOR_NAME_ALREADY_EXISTS.throwIt();
    }
    var config = safeConfig(request.config());
    var rules = safeRules(request.rules());
    validator.validate(user, request.type(), config, rules, List.of());
    var now = OffsetDateTime.now(clock);
    var evaluator =
        evaluatorRepository.save(
            Evaluator.builder()
                .projectId(projectId)
                .name(normalized.displayName())
                .nameLookup(normalized.lookupName())
                .type(request.type())
                .createdBy(user.id())
                .createdAt(now)
                .updatedAt(now)
                .build());
    var version = createVersion(evaluator.getId(), 1, config, user.id(), now);
    createRules(version.getId(), rules, now);
    log.info("Evaluator created: evaluatorId={} projectId={} actorId={}", evaluator.getId(), projectId, user.id());
    return assembler.toResponse(evaluator);
  }

  @Override
  @Transactional(readOnly = true)
  public EvaluatorResponse get(CurrentUser user, UUID evaluatorId) {
    return assembler.toResponse(requireEvaluator(user, evaluatorId, PermissionAction.VIEW));
  }

  @Override
  @Transactional
  public EvaluatorResponse update(CurrentUser user, UUID evaluatorId, UpdateEvaluatorRequest request) {
    var evaluator = requireEvaluator(user, evaluatorId, PermissionAction.EDIT);
    if (request.name() != null) {
      var normalized = nameNormalizer.normalize(request.name(), 160);
      if (!evaluator.getNameLookup().equals(normalized.lookupName())
          && evaluatorRepository.existsByProjectIdAndNameLookupAndDeletedAtIsNull(
              evaluator.getProjectId(), normalized.lookupName())) {
        throw ErrorCode.EVALUATOR_NAME_ALREADY_EXISTS.throwIt();
      }
      evaluator.setName(normalized.displayName());
      evaluator.setNameLookup(normalized.lookupName());
    }
    evaluator.setUpdatedAt(OffsetDateTime.now(clock));
    log.info("Evaluator updated: evaluatorId={} actorId={}", evaluatorId, user.id());
    return assembler.toResponse(evaluator);
  }

  @Override
  @Transactional
  public void delete(CurrentUser user, UUID evaluatorId) {
    var evaluator = requireEvaluator(user, evaluatorId, PermissionAction.EDIT);
    var now = OffsetDateTime.now(clock);
    evaluator.setDeletedAt(now);
    evaluator.setUpdatedAt(now);
    log.info("Evaluator soft-deleted: evaluatorId={} actorId={}", evaluatorId, user.id());
  }

  Evaluator requireEvaluator(CurrentUser user, UUID evaluatorId, PermissionAction action) {
    var evaluator =
        evaluatorRepository
            .findByIdAndDeletedAtIsNull(evaluatorId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    projectPermissionService.requireProject(user, evaluator.getProjectId(), action);
    return evaluator;
  }

  EvaluatorVersion createVersion(
      UUID evaluatorId, int versionNumber, Map<String, Object> config, UUID createdBy, OffsetDateTime now) {
    return versionRepository.save(
        EvaluatorVersion.builder()
            .evaluatorId(evaluatorId)
            .version(versionNumber)
            .configJson(config)
            .configHash(hash(config))
            .createdBy(createdBy)
            .createdAt(now)
            .build());
  }

  void createRules(UUID versionId, List<EvaluatorRuleRequest> rules, OffsetDateTime now) {
    for (var i = 0; i < rules.size(); i++) {
      var rule = rules.get(i);
      ruleRepository.save(
          EvaluatorRule.builder()
              .evaluatorVersionId(versionId)
              .fieldSource(rule.fieldSource())
              .operator(rule.operator())
              .expectedValueJson(safeConfig(rule.expectedValue()))
              .weight(rule.weight())
              .required(rule.required())
              .orderIndex(rule.orderIndex() == null ? i + 1 : rule.orderIndex())
              .configJson(safeConfig(rule.config()))
              .createdAt(now)
              .updatedAt(now)
              .build());
    }
  }

  Map<String, Object> safeConfig(Map<String, Object> config) {
    return config == null ? Map.of() : config;
  }

  List<EvaluatorRuleRequest> safeRules(List<EvaluatorRuleRequest> rules) {
    return rules == null ? List.of() : rules;
  }

  private String hash(Map<String, Object> config) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(objectMapper.writeValueAsString(config).getBytes(StandardCharsets.UTF_8)));
    } catch (JsonProcessingException exception) {
      throw ErrorCode.EVALUATOR_RULE_INVALID.throwIt("Evaluator config must be valid JSON");
    } catch (Exception exception) {
      throw ErrorCode.INTERNAL_ERROR.throwIt();
    }
  }
}
