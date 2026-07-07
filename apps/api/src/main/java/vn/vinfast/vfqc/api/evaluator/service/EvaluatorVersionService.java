package vn.vinfast.vfqc.api.evaluator.service;

import java.util.List;
import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.evaluator.dto.CreateEvaluatorVersionRequest;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorVersionResponse;

public interface EvaluatorVersionService {

  List<EvaluatorVersionResponse> list(CurrentUser user, UUID evaluatorId);

  EvaluatorVersionResponse create(CurrentUser user, UUID evaluatorId, CreateEvaluatorVersionRequest request);

  EvaluatorVersionResponse get(CurrentUser user, UUID evaluatorVersionId);
}
