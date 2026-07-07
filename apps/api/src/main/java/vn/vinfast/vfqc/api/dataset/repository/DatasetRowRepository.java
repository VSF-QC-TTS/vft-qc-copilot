package vn.vinfast.vfqc.api.dataset.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.dataset.entity.DatasetRow;

public interface DatasetRowRepository extends JpaRepository<DatasetRow, Long> {

  List<DatasetRow> findByDatasetVersionIdOrderByRowIndexAsc(UUID datasetVersionId);

  Page<DatasetRow> findByDatasetVersionIdOrderByRowIndexAsc(UUID datasetVersionId, Pageable pageable);

  long countByDatasetVersionId(UUID datasetVersionId);
}
