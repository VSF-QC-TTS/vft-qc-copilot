package vn.vinfast.vfqc.api.evaluator.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.evaluator.entity.Evaluator;

public interface EvaluatorRepository extends JpaRepository<Evaluator, UUID> {

  Optional<Evaluator> findByIdAndDeletedAtIsNull(UUID id);

  List<Evaluator> findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID projectId);

  boolean existsByProjectIdAndNameLookupAndDeletedAtIsNull(UUID projectId, String nameLookup);

  long countByProjectIdAndDeletedAtIsNull(UUID projectId);
}
