package vn.vinfast.vfqc.api.evaluator.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorValidationResponse;
import vn.vinfast.vfqc.api.evaluator.dto.ValidateEvaluatorRequest;

public interface EvaluatorValidationService {

  EvaluatorValidationResponse validate(CurrentUser user, UUID projectId, ValidateEvaluatorRequest request);
}
