package vn.vinfast.vfqc.api.evaluator.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.vinfast.vfqc.api.evaluator.entity.EvaluatorVersion;

public interface EvaluatorVersionRepository extends JpaRepository<EvaluatorVersion, UUID> {

  List<EvaluatorVersion> findByEvaluatorIdOrderByVersionDesc(UUID evaluatorId);

  Optional<EvaluatorVersion> findTopByEvaluatorIdOrderByVersionDesc(UUID evaluatorId);

  List<EvaluatorVersion> findByEvaluatorIdIn(Collection<UUID> evaluatorIds);

  @Query("select coalesce(max(v.version), 0) from EvaluatorVersion v where v.evaluatorId = :evaluatorId")
  int maxVersion(UUID evaluatorId);
}
