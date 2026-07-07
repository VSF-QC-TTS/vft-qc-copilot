package vn.vinfast.vfqc.api.evaluator.service;

import java.util.List;
import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.evaluator.dto.CreateEvaluatorRequest;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorResponse;
import vn.vinfast.vfqc.api.evaluator.dto.UpdateEvaluatorRequest;

public interface EvaluatorService {

  List<EvaluatorResponse> list(CurrentUser user, UUID projectId);

  EvaluatorResponse create(CurrentUser user, UUID projectId, CreateEvaluatorRequest request);

  EvaluatorResponse get(CurrentUser user, UUID evaluatorId);

  EvaluatorResponse update(CurrentUser user, UUID evaluatorId, UpdateEvaluatorRequest request);

  void delete(CurrentUser user, UUID evaluatorId);
}
