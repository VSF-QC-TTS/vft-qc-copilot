package vn.vinfast.vfqc.api.dataset.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.dataset.entity.DatasetImportJob;

public interface DatasetImportJobRepository extends JpaRepository<DatasetImportJob, UUID> {

  Optional<DatasetImportJob> findByIdAndTargetProjectId(UUID id, UUID targetProjectId);
}
