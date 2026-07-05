package vn.vinfast.vfqc.api.dataset.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.vinfast.vfqc.api.dataset.entity.DatasetVersion;
import vn.vinfast.vfqc.api.dataset.enums.DatasetVersionStatus;

public interface DatasetVersionRepository extends JpaRepository<DatasetVersion, UUID> {

  List<DatasetVersion> findByDatasetIdOrderByVersionDesc(UUID datasetId);

  Optional<DatasetVersion> findByDatasetIdAndStatus(UUID datasetId, DatasetVersionStatus status);

  Optional<DatasetVersion> findTopByDatasetIdOrderByVersionDesc(UUID datasetId);

  @Query("""
      select count(v)
      from DatasetVersion v
      join Dataset d on d.id = v.datasetId
      where d.projectId = :projectId
        and d.deletedAt is null
        and v.status = :status
      """)
  long countActiveVersionsByProject(UUID projectId, DatasetVersionStatus status);
}
