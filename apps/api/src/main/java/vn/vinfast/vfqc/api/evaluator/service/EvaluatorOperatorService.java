package vn.vinfast.vfqc.api.evaluator.service;

import java.util.List;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorOperatorResponse;

public interface EvaluatorOperatorService {

  /**
   * Lists supported safe evaluator operators.
   *
   * <p>This metadata is static and contains only declarative operators accepted by the backend MVP.
   *
   * @return operator metadata for UI builders and validation hints
   */
  List<EvaluatorOperatorResponse> list();
}
