package vn.vinfast.vfqc.api.dataset.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.dataset.entity.Dataset;

public interface DatasetRepository extends JpaRepository<Dataset, UUID> {

  Optional<Dataset> findByIdAndDeletedAtIsNull(UUID id);

  List<Dataset> findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID projectId);

  boolean existsByProjectIdAndNameLookupAndDeletedAtIsNull(UUID projectId, String nameLookup);

  long countByProjectIdAndDeletedAtIsNull(UUID projectId);
}
