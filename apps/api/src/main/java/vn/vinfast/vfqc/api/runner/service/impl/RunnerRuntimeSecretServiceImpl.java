package vn.vinfast.vfqc.api.runner.service.impl;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.project.repository.ProjectRepository;
import vn.vinfast.vfqc.api.runner.dto.RunnerRuntimeSecretsRequest;
import vn.vinfast.vfqc.api.runner.dto.RunnerRuntimeSecretsResponse;
import vn.vinfast.vfqc.api.runner.service.RunnerRuntimeSecretService;
import vn.vinfast.vfqc.api.secret.entity.Secret;
import vn.vinfast.vfqc.api.secret.enums.SecretStatus;
import vn.vinfast.vfqc.api.secret.repository.SecretRepository;
import vn.vinfast.vfqc.api.secret.service.SecretEncryptionService;
import vn.vinfast.vfqc.api.testrun.repository.TestRunEvaluatorVersionRepository;
import vn.vinfast.vfqc.api.testrun.repository.TestRunRepository;
import vn.vinfast.vfqc.api.testrun.repository.TestRunTargetRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunnerRuntimeSecretServiceImpl implements RunnerRuntimeSecretService {

  private final TestRunRepository testRunRepository;
  private final TestRunTargetRepository targetRepository;
  private final TestRunEvaluatorVersionRepository evaluatorRepository;
  private final ProjectRepository projectRepository;
  private final SecretRepository secretRepository;
  private final SecretEncryptionService encryptionService;

  @Override
  @Transactional(readOnly = true)
  public RunnerRuntimeSecretsResponse resolve(UUID testRunId, RunnerRuntimeSecretsRequest request) {
    var run = testRunRepository.findById(testRunId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var project = projectRepository.findById(run.getProjectId()).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var allowed = allowedRefs(testRunId);
    var requested = request.secretIds() == null ? List.<UUID>of() : request.secretIds();
    log.info("Resolving runner runtime secrets: testRunId={}, secretCount={}", testRunId, requested.size());
    if (!allowed.containsAll(requested)) {
      throw ErrorCode.ACCESS_DENIED.throwIt("Requested secret is not referenced by the locked run snapshots");
    }
    var secretsById =
        secretRepository.findByIdInAndStatusAndDeletedAtIsNull(new LinkedHashSet<>(requested), SecretStatus.ACTIVE).stream()
            .collect(Collectors.toMap(Secret::getId, secret -> secret));
    var values = new LinkedHashMap<String, String>();
    for (var secretId : requested) {
      var secret = secretsById.get(secretId);
      if (secret == null) {
        throw ErrorCode.RESOURCE_NOT_FOUND.throwIt();
      }
      if (secret.getWorkspaceId() != null && !secret.getWorkspaceId().equals(project.getWorkspaceId())) {
        throw ErrorCode.ACCESS_DENIED.throwIt();
      }
      values.put(secretId.toString(), encryptionService.decrypt(secret.getEncryptedValue()));
    }
    return new RunnerRuntimeSecretsResponse(values);
  }

  private Set<UUID> allowedRefs(UUID testRunId) {
    var refs = new LinkedHashSet<UUID>();
    targetRepository.findByTestRunIdOrderByOrderIndexAsc(testRunId).forEach(target -> collectRefs(target.getConfigJson(), refs));
    evaluatorRepository.findByTestRunIdOrderByOrderIndexAsc(testRunId).forEach(evaluator -> collectRefs(evaluator.getConfigJson(), refs));
    return refs;
  }

  @SuppressWarnings("unchecked")
  private void collectRefs(Object value, Set<UUID> refs) {
    if (value instanceof Map<?, ?> map) {
      for (var entry : map.entrySet()) {
        var key = String.valueOf(entry.getKey());
        if ("refId".equals(key) || "runtimeProviderRefId".equals(key)) {
          addUuid(entry.getValue(), refs);
        }
        collectRefs(entry.getValue(), refs);
      }
      return;
    }
    if (value instanceof List<?> list) {
      list.forEach(item -> collectRefs(item, refs));
    }
  }

  private void addUuid(Object value, Set<UUID> refs) {
    if (value == null) {
      return;
    }
    try {
      refs.add(UUID.fromString(value.toString()));
    } catch (IllegalArgumentException ignored) {
      // Ignore non-UUID snapshot values.
    }
  }
}
