package vn.vinfast.vfqc.api.dataset.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.dataset.entity.DatasetRow;

public interface DatasetRowRepository extends JpaRepository<DatasetRow, Long> {

  List<DatasetRow> findByDatasetVersionIdOrderByRowIndexAsc(UUID datasetVersionId);

  long countByDatasetVersionId(UUID datasetVersionId);
}
