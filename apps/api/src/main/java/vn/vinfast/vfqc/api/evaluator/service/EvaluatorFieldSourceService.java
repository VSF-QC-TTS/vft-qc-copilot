package vn.vinfast.vfqc.api.evaluator.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorFieldSourceResponse;

public interface EvaluatorFieldSourceService {

  EvaluatorFieldSourceResponse list(CurrentUser user, UUID projectId, UUID chatbotTargetId);
}
