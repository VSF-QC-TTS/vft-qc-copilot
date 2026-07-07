package vn.vinfast.vfqc.api.evaluator.service;

import java.util.UUID;

public interface EvaluatorReadinessService {

  long activeEvaluatorCount(UUID projectId);
}
