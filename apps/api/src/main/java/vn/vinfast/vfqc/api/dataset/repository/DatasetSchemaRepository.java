package vn.vinfast.vfqc.api.dataset.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.dataset.entity.DatasetSchema;

public interface DatasetSchemaRepository extends JpaRepository<DatasetSchema, UUID> {

  Optional<DatasetSchema> findTopByProjectIdOrderByVersionDesc(UUID projectId);

  List<DatasetSchema> findByProjectIdOrderByVersionDesc(UUID projectId);
}
