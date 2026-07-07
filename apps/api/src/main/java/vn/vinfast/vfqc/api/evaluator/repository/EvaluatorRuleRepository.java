package vn.vinfast.vfqc.api.evaluator.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.evaluator.entity.EvaluatorRule;

public interface EvaluatorRuleRepository extends JpaRepository<EvaluatorRule, Long> {

  List<EvaluatorRule> findByEvaluatorVersionIdOrderByOrderIndexAsc(UUID evaluatorVersionId);

  List<EvaluatorRule> findByEvaluatorVersionIdIn(Collection<UUID> evaluatorVersionIds);
}
