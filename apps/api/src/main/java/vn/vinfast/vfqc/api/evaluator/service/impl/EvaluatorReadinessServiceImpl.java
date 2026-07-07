package vn.vinfast.vfqc.api.evaluator.service.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.evaluator.repository.EvaluatorRepository;
import vn.vinfast.vfqc.api.evaluator.service.EvaluatorReadinessService;

@Service
@RequiredArgsConstructor
public class EvaluatorReadinessServiceImpl implements EvaluatorReadinessService {

  private final EvaluatorRepository evaluatorRepository;

  @Override
  @Transactional(readOnly = true)
  public long activeEvaluatorCount(UUID projectId) {
    return evaluatorRepository.countByProjectIdAndDeletedAtIsNull(projectId);
  }
}
