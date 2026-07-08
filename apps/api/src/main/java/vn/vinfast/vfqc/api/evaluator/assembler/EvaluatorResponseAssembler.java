package vn.vinfast.vfqc.api.evaluator.assembler;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorResponse;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorRuleResponse;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorVersionResponse;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorVersionSummaryResponse;
import vn.vinfast.vfqc.api.evaluator.entity.Evaluator;
import vn.vinfast.vfqc.api.evaluator.entity.EvaluatorRule;
import vn.vinfast.vfqc.api.evaluator.entity.EvaluatorVersion;
import vn.vinfast.vfqc.api.evaluator.repository.EvaluatorVersionRepository;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;
import vn.vinfast.vfqc.api.user.service.UserSummaryService;

@Component
@RequiredArgsConstructor
public class EvaluatorResponseAssembler {

  private final EvaluatorVersionRepository versionRepository;
  private final UserSummaryService userSummaryService;

  public EvaluatorResponse toResponse(Evaluator evaluator) {
    var latest = versionRepository.findTopByEvaluatorIdOrderByVersionDesc(evaluator.getId()).orElse(null);
    var users = userSummaryService.summariesById(userIds(List.of(evaluator), latest == null ? List.of() : List.of(latest)));
    return toResponse(evaluator, latest, users);
  }

  public List<EvaluatorResponse> toResponses(List<Evaluator> evaluators) {
    var evaluatorIds = evaluators.stream().map(Evaluator::getId).toList();
    var latestByEvaluatorId =
        versionRepository.findByEvaluatorIdIn(evaluatorIds).stream()
            .collect(
                Collectors.groupingBy(
                    EvaluatorVersion::getEvaluatorId,
                    Collectors.maxBy(Comparator.comparingInt(EvaluatorVersion::getVersion))));
    var latestVersions =
        latestByEvaluatorId.values().stream().flatMap(Optional::stream).toList();
    var users = userSummaryService.summariesById(userIds(evaluators, latestVersions));
    return evaluators.stream()
        .map(evaluator -> toResponse(evaluator, latestById(latestByEvaluatorId, evaluator.getId()), users))
        .toList();
  }

  public EvaluatorVersionSummaryResponse toSummary(EvaluatorVersion version) {
    if (version == null) {
      return null;
    }
    return new EvaluatorVersionSummaryResponse(
        version.getId(),
        version.getVersion(),
        version.getCreatedAt(),
        userSummaryService.toSummaryById(version.getCreatedBy()));
  }

  public EvaluatorVersionResponse toVersionResponse(
      Evaluator evaluator, EvaluatorVersion version, List<EvaluatorRule> rules) {
    return toVersionResponse(
        evaluator,
        version,
        rules,
        userSummaryService.summariesById(List.of(version.getCreatedBy())));
  }

  public List<EvaluatorVersionResponse> toVersionResponses(
      Evaluator evaluator, List<EvaluatorVersion> versions, Map<UUID, List<EvaluatorRule>> rulesByVersionId) {
    var users = userSummaryService.summariesById(versions.stream().map(EvaluatorVersion::getCreatedBy).toList());
    return versions.stream()
        .map(version -> toVersionResponse(evaluator, version, rulesByVersionId.getOrDefault(version.getId(), List.of()), users))
        .toList();
  }

  private EvaluatorResponse toResponse(
      Evaluator evaluator, EvaluatorVersion latest, Map<UUID, UserSummaryResponse> users) {
    return new EvaluatorResponse(
        evaluator.getId(),
        evaluator.getProjectId(),
        evaluator.getName(),
        evaluator.getType(),
        toSummary(latest, users),
        evaluator.getCreatedBy(),
        users.get(evaluator.getCreatedBy()),
        evaluator.getCreatedAt(),
        evaluator.getUpdatedAt());
  }

  private EvaluatorVersionResponse toVersionResponse(
      Evaluator evaluator,
      EvaluatorVersion version,
      List<EvaluatorRule> rules,
      Map<UUID, UserSummaryResponse> users) {
    return new EvaluatorVersionResponse(
        version.getId(),
        evaluator.getId(),
        evaluator.getProjectId(),
        evaluator.getName(),
        evaluator.getType(),
        version.getVersion(),
        safeMap(version.getConfigJson()),
        rules.stream().map(this::toRuleResponse).toList(),
        version.getCreatedBy(),
        users.get(version.getCreatedBy()),
        version.getCreatedAt());
  }

  private EvaluatorRuleResponse toRuleResponse(EvaluatorRule rule) {
    return new EvaluatorRuleResponse(
        rule.getId(),
        rule.getFieldSource(),
        rule.getOperator(),
        safeMap(rule.getExpectedValueJson()),
        rule.getWeight(),
        rule.isRequired(),
        rule.getOrderIndex(),
        safeMap(rule.getConfigJson()));
  }

  private EvaluatorVersionSummaryResponse toSummary(
      EvaluatorVersion version, Map<UUID, UserSummaryResponse> users) {
    if (version == null) {
      return null;
    }
    return new EvaluatorVersionSummaryResponse(
        version.getId(), version.getVersion(), version.getCreatedAt(), users.get(version.getCreatedBy()));
  }

  private EvaluatorVersion latestById(
      Map<UUID, Optional<EvaluatorVersion>> latestByEvaluatorId, UUID evaluatorId) {
    return latestByEvaluatorId.getOrDefault(evaluatorId, Optional.empty()).orElse(null);
  }

  private Collection<UUID> userIds(List<Evaluator> evaluators, List<EvaluatorVersion> versions) {
    var evaluatorUserIds = evaluators.stream().map(Evaluator::getCreatedBy);
    var versionUserIds = versions.stream().map(EvaluatorVersion::getCreatedBy);
    return Stream.concat(evaluatorUserIds, versionUserIds).toList();
  }

  private Map<String, Object> safeMap(Map<String, Object> value) {
    return value == null ? Map.of() : value;
  }
}
